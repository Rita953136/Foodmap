package com.example.fmap.ui.home;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fmap.BuildConfig;
import com.example.fmap.data.StoresRepository;
import com.example.fmap.data.local.StoreEntity;
import com.example.fmap.data.local.StoreMappers;
import com.example.fmap.model.FavoritesStore;
import com.example.fmap.model.Place;
import com.example.fmap.model.Swipe;
import com.example.fmap.util.OpenAIClient;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeViewModel extends AndroidViewModel {

    // ===== Debug 開關 =====
    private static final String TAG = "HomeViewModel";
    private static final boolean DEBUG_LOG_PLACES     = true;   // 印出筆數與前幾筆內容
    private static final boolean DEBUG_BYPASS_FILTERS = false;  // 設 true 時，忽略所有過濾，直接把 mapped 丟給 UI

    public enum TagMatchMode { ANY, ALL }

    // --- UI State LiveData ---
    private final MutableLiveData<List<Place>> places = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> emptyMessage = new MutableLiveData<>("點擊或滑動卡片來探索");
    private final MutableLiveData<List<String>> selectedTags = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<TagMatchMode> tagMatchMode = new MutableLiveData<>(TagMatchMode.ALL);
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");

    //地圖聚焦事件（詳情頁呼叫 → 地圖頁觀察並置中）
    private final MutableLiveData<Event<String>> _navigateToMapAndFocusOn = new MutableLiveData<>();
    public LiveData<Event<String>> getNavigateToMapAndFocusOn() { return _navigateToMapAndFocusOn; }

    // --- Trash（不喜歡） ---
    private final MutableLiveData<List<Place>> dislikedPlaces = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoadingTrash = new MutableLiveData<>(false);
    private final MutableLiveData<String> trashError = new MutableLiveData<>();

    // --- Services and Dependencies ---
    private static final String PREFS_NAME = "FmapUserPrefs";
    private static final String DISLIKED_PLACES_KEY = "disliked_places";

    private final SharedPreferences prefs;
    private final FavoritesStore favStore;
    private final ExecutorService viewModelExecutor;
    private final OpenAIClient openAIClient;
    private final StoresRepository storeRepo;

    public HomeViewModel(@NonNull Application app) {
        super(app);
        prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        favStore = FavoritesStore.getInstance(app.getApplicationContext());
        viewModelExecutor = Executors.newSingleThreadExecutor();
        openAIClient = new OpenAIClient(BuildConfig.OPENAI_API_KEY);

        storeRepo = new StoresRepository(app);
        storeRepo.initFromAssets(app);
        storeRepo.getDbReady().observeForever(ready -> {
            if (Boolean.TRUE.equals(ready)) {
                if (DEBUG_LOG_PLACES) Log.d(TAG, "DB ready → trigger loadPlaces()");
                if (places.getValue() == null || places.getValue().isEmpty()) {
                    loadPlaces();
                }
            } else {
                if (DEBUG_LOG_PLACES) Log.d(TAG, "DB not ready yet");
            }
        });
    }

    // --- LiveData getters ---
    public LiveData<List<Place>> getPlaces() { return places; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getError() { return error; }
    public LiveData<String> getEmptyMessage() { return emptyMessage; }
    public LiveData<List<String>> getSelectedTags() { return selectedTags; }
    public LiveData<List<Place>> getDislikedPlaces() { return dislikedPlaces; }
    public LiveData<Boolean> getIsLoadingTrash() { return isLoadingTrash; }
    public LiveData<String> getTrashError() { return trashError; }

    /** 主邏輯：載入店家並篩選 **/
    public void loadPlaces() {
        if (Boolean.TRUE.equals(isLoading.getValue())) return;
        isLoading.postValue(true);
        error.postValue(null);

        List<String> selectedCategories = selectedTags.getValue() != null ? selectedTags.getValue() : Collections.emptyList();
        String keyword = searchQuery.getValue() != null ? searchQuery.getValue() : "";
        TagMatchMode mode = tagMatchMode.getValue() != null ? tagMatchMode.getValue() : TagMatchMode.ALL;

        if (DEBUG_LOG_PLACES) {
            Log.d(TAG, "loadPlaces(): keyword=" + keyword + ", selectedCategories=" + selectedCategories + ", mode=" + mode + ", BYPASS=" + DEBUG_BYPASS_FILTERS);
        }
        emptyMessage.postValue("正在載入店家...");
        loadFromLocal(keyword, selectedCategories, mode);
    }

    public void applyTagFilter(List<String> selected) {
        selectedTags.postValue(selected != null ? selected : new ArrayList<>());
        loadPlaces();
    }

    public void applySearchQuery(String query) {
        if (Objects.equals(searchQuery.getValue(), query)) return;
        searchQuery.setValue(query != null ? query.trim() : "");
        loadPlaces();
    }

    public void setTagMatchMode(TagMatchMode mode) {
        tagMatchMode.postValue(mode != null ? mode : TagMatchMode.ALL);
        loadPlaces();
    }

    public void handleSwipeAction(Swipe.Action action, Place place) {
        if (place == null) return;
        if (action == Swipe.Action.LIKE) {
            favStore.add(place);
        } else if (action == Swipe.Action.NOPE) {
            addToDislikes(place);
        }
        loadPlaces();
    }

    /** ✅ 新增：由 PlaceDetailFragment 呼叫，觸發「讓地圖聚焦到指定店家」 */
    public void requestFocusOnPlace(String placeId) {
        if (placeId != null && !placeId.isEmpty()) {
            _navigateToMapAndFocusOn.setValue(new Event<>(placeId));
        }
    }

    /** 針對 category 是 List<String> 的版本 **/
    private void loadFromLocal(String keyword, List<String> selectedRaw, TagMatchMode mode) {
        final Set<String> favoriteIds = new HashSet<>();
        List<Place> currentFavorites = favStore.getAll();
        if (currentFavorites != null) {
            for (Place p : currentFavorites) if (p != null && p.id != null) favoriteIds.add(p.id);
        }

        final List<String> wantedCats = normalizeStrings(selectedRaw);

        viewModelExecutor.execute(() -> {
            try {
                final boolean singleCat = (wantedCats.size() == 1);
                final String dbCategory = singleCat ? wantedCats.get(0) : "";

                if (DEBUG_LOG_PLACES) {
                    Log.d(TAG, "loadFromLocal(): query db with keyword=" + keyword + ", dbCategory=" + dbCategory);
                }

                List<StoreEntity> candidates = storeRepo.searchAdvancedBlocking(
                        keyword,
                        dbCategory,
                        1,
                        keyword,
                        ""
                );
                if (DEBUG_LOG_PLACES) {
                    Log.d(TAG, "candidates (raw from DB) size=" + (candidates == null ? 0 : candidates.size()));
                }

                List<StoreEntity> afterCats = new ArrayList<>();
                if (wantedCats.isEmpty()) {
                    afterCats = candidates;
                } else {
                    for (StoreEntity e : candidates) {
                        if (e == null || e.category == null) continue;
                        Set<String> storeCats = toLowerSet(e.category);
                        boolean pass = matchesByCategories(storeCats, wantedCats, mode);
                        if (pass) afterCats.add(e);
                    }
                }

                // DB → UI 模型
                List<Place> mapped = StoreMappers.toPlaceList(afterCats);
                if (DEBUG_LOG_PLACES) {
                    Log.d(TAG, "mapped to Place size=" + (mapped == null ? 0 : mapped.size()));
                    if (mapped != null && !mapped.isEmpty()) {
                        int show = Math.min(5, mapped.size());
                        for (int i = 0; i < show; i++) {
                            Place p = mapped.get(i);
                            Log.d(TAG, String.format(Locale.ROOT,
                                    "mapped[%d] id=%s name=%s lat=%s lng=%s",
                                    i, p == null ? "null" : p.id,
                                    p == null ? "null" : p.getName(),
                                    p == null ? "null" : String.valueOf(p.getLat()),
                                    p == null ? "null" : String.valueOf(p.getLng())));
                        }
                    }
                }

                List<Place> finalResult;
                if (DEBUG_BYPASS_FILTERS) {
                    // 直接把 mapped 丟給 UI，協助定位：資料是否有讀到 / 是否含座標
                    finalResult = mapped != null ? mapped : new ArrayList<>();
                } else {
                    // 原本過濾：排除喜歡與不喜歡
                    Set<String> dislikedIds = getDislikedIds();
                    finalResult = new ArrayList<>();
                    if (mapped != null) {
                        for (Place p : mapped) {
                            if (p == null || p.id == null) continue;
                            if (!dislikedIds.contains(p.id) && !favoriteIds.contains(p.id)) {
                                finalResult.add(p);
                            }
                        }
                    }
                }

                if (DEBUG_LOG_PLACES) {
                    Log.d(TAG, "finalResult size=" + (finalResult == null ? 0 : finalResult.size()));
                    if (finalResult != null && !finalResult.isEmpty()) {
                        int show = Math.min(5, finalResult.size());
                        for (int i = 0; i < show; i++) {
                            Place p = finalResult.get(i);
                            Log.d(TAG, String.format(Locale.ROOT,
                                    "final[%d] id=%s name=%s lat=%s lng=%s",
                                    i, p == null ? "null" : p.id,
                                    p == null ? "null" : p.getName(),
                                    p == null ? "null" : String.valueOf(p.getLat()),
                                    p == null ? "null" : String.valueOf(p.getLng())));
                        }
                    }
                }

                if (finalResult == null || finalResult.isEmpty()) {
                    emptyMessage.postValue("找不到符合的店家，試試看調整類別或關鍵字。");
                }
                places.postValue(finalResult);

            } catch (Exception e) {
                Log.e(TAG, "loadFromLocal error", e);
                error.postValue("載入資料時發生錯誤");
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    // --- 篩選邏輯 ---
    private boolean matchesByCategories(Set<String> storeCats, List<String> wantedCats, TagMatchMode mode) {
        if (wantedCats == null || wantedCats.isEmpty()) return true;
        if (storeCats == null || storeCats.isEmpty()) return false;

        if (mode == TagMatchMode.ALL) {
            for (String w : wantedCats) if (!storeCats.contains(w)) return false;
            return true;
        } else { // ANY
            for (String w : wantedCats) if (storeCats.contains(w)) return true;
            return false;
        }
    }

    // --- Normalization ---
    private static String norm(String s) {
        if (s == null) return "";
        String x = s.replace("\ufeff", "")
                .replace("\u200b", "").replace("\u200c", "").replace("\u200d", "")
                .trim();
        x = Normalizer.normalize(x, Normalizer.Form.NFKC);
        return x.toLowerCase(Locale.ROOT);
    }

    private List<String> normalizeStrings(List<String> items) {
        List<String> out = new ArrayList<>();
        if (items == null) return out;
        for (String s : items) {
            String t = norm(s);
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }

    private Set<String> toLowerSet(List<String> items) {
        Set<String> out = new HashSet<>();
        if (items == null) return out;
        for (String s : items) {
            String t = norm(s);
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    // --- Dislikes ---
    public void addToDislikes(Place place) {
        if (place == null || place.id == null) return;
        viewModelExecutor.execute(() -> {
            Set<String> ids = new HashSet<>(prefs.getStringSet(DISLIKED_PLACES_KEY, Collections.emptySet()));
            if (ids.add(place.id)) {
                prefs.edit().putStringSet(DISLIKED_PLACES_KEY, ids).apply();
                loadDislikedPlacesFromPrefs();
            }
        });
    }

    private Set<String> getDislikedIds() {
        return new HashSet<>(prefs.getStringSet(DISLIKED_PLACES_KEY, Collections.emptySet()));
    }

    public void loadDislikedPlacesFromPrefs() {
        isLoadingTrash.postValue(true);
        trashError.postValue(null);

        viewModelExecutor.execute(() -> {
            try {
                Set<String> ids = getDislikedIds();
                if (ids.isEmpty()) {
                    dislikedPlaces.postValue(new ArrayList<>());
                    return;
                }
                List<StoreEntity> entities = storeRepo.getByIdsBlocking(new ArrayList<>(ids));
                List<Place> list = StoreMappers.toPlaceList(entities);
                dislikedPlaces.postValue(list != null ? list : new ArrayList<>());
            } catch (Exception e) {
                Log.e(TAG, "loadDislikedPlacesFromPrefs failed", e);
                trashError.postValue("讀取垃圾桶失敗：" + e.getMessage());
            } finally {
                isLoadingTrash.postValue(false);
            }
        });
    }

    public void removeFromDislikes(String placeId) {
        if (placeId == null) return;
        viewModelExecutor.execute(() -> {
            try {
                Set<String> dislikedIds = getDislikedIds();
                if (dislikedIds.remove(placeId)) {
                    prefs.edit().putStringSet(DISLIKED_PLACES_KEY, dislikedIds).apply();
                    loadDislikedPlacesFromPrefs();
                    loadPlaces();
                } else {
                    trashError.postValue("在垃圾桶中找不到該項目，可能已被復原。");
                }
            } catch (Exception e) {
                Log.e(TAG, "removeFromDislikes failed", e);
                trashError.postValue("復原店家時發生錯誤：" + e.getMessage());
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        viewModelExecutor.shutdown();
    }
}
