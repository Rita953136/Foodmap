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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class HomeViewModel extends AndroidViewModel {

    private static final String TAG = "HomeViewModel";

    public enum TagMatchMode { ANY, ALL }

    // --- UI State LiveData (保持不變) ---
    private final MutableLiveData<List<Place>> places = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> emptyMessage = new MutableLiveData<>("點擊或滑動卡片來探索");
    private final MutableLiveData<List<String>> selectedTags = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<TagMatchMode> tagMatchMode = new MutableLiveData<>(TagMatchMode.ALL);

    // --- Trash（不喜歡） (保持不變) ---
    private final MutableLiveData<List<Place>> dislikedPlaces = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoadingTrash = new MutableLiveData<>(false);
    private final MutableLiveData<String> trashError = new MutableLiveData<>();

    // --- Services and Dependencies ---
    private static final String PREFS_NAME = "FmapUserPrefs";
    private static final String DISLIKED_PLACES_KEY = "disliked_places";

    private final SharedPreferences prefs;
    private final FavoritesStore favStore;
    private final ExecutorService viewModelExecutor; // ✨ 使用新的名稱，避免與 Repository 的混淆
    private final OpenAIClient openAIClient;
    private final StoresRepository storeRepo;

    // ✨✨✨【改造點 1：建構子】✨✨✨
    public HomeViewModel(@NonNull Application app) {
        super(app);
        prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        favStore = FavoritesStore.getInstance(app.getApplicationContext());
        viewModelExecutor = Executors.newSingleThreadExecutor(); // 初始化 ViewModel 自己的背景執行緒
        openAIClient = new OpenAIClient(BuildConfig.OPENAI_API_KEY);

        // 初始化 Repository
        storeRepo = new StoresRepository(app);
        storeRepo.initFromAssets(app);
        storeRepo.getDbReady().observeForever(ready -> { // observeForever 在此處相對安全，因為 ViewModel 會在 App 結束時才銷毀
            if (Boolean.TRUE.equals(ready)) {
                if (places.getValue() == null || places.getValue().isEmpty()) {
                    loadPlaces(); // 當資料庫就緒，自動載入一次資料
                }
            }
        });
    }

    // --- LiveData getters (保持不變) ---
    public LiveData<List<Place>> getPlaces() { return places; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getError() { return error; }
    public LiveData<String> getEmptyMessage() { return emptyMessage; }
    public LiveData<List<String>> getSelectedTags() { return selectedTags; }
    public LiveData<TagMatchMode> getTagMatchMode() { return tagMatchMode; }
    public LiveData<List<Place>> getDislikedPlaces() { return dislikedPlaces; }
    public LiveData<Boolean> getIsLoadingTrash() { return isLoadingTrash; }
    public LiveData<String> getTrashError() { return trashError; }


    // ---------------- 載入店家（已改造為純背景執行） ----------------

    /** 依目前 selectedTags + tagMatchMode 進行載入（只走 Room）。 */
    public void loadPlaces() {
        if (Boolean.TRUE.equals(isLoading.getValue())) return;
        isLoading.postValue(true);
        error.postValue(null);
        emptyMessage.postValue("正在載入店家...");

        List<String> tags = selectedTags.getValue() != null ? selectedTags.getValue() : Collections.emptyList();
        loadFromLocal(tags);
    }

    public void applyTagFilter(List<String> selected) {
        selectedTags.postValue(selected != null ? selected : new ArrayList<>());
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

    private void loadFromLocal(List<String> selectedCategories) {
        // 步驟 1：【主執行緒】準備好所有需要傳遞給背景執行緒的「安全資料」。
        // 這樣可以完全避免在背景執行緒中直接存取 favStore，從而根除崩潰問題。
        final Set<String> favoriteIds = new HashSet<>();
        List<Place> currentFavorites = favStore.getAll(); // 從 favStore 取得當前的「最愛」列表快照
        if (currentFavorites != null) {
            for (Place p : currentFavorites) {
                if (p != null && p.id != null) {
                    favoriteIds.add(p.id);
                }
            }
        }

        // 步驟 2：【切換到背景執行緒】開始執行耗時的資料庫查詢和過濾。
        viewModelExecutor.execute(() -> {
            try {
                // 步驟 2.1：準備資料庫查詢所需的參數。
                String keyword = "";
                String dishLike = "";
                String priceEq = "";
                // DAO 目前只支援單一標籤，所以暫時取第一個。
                String singleCategory = (selectedCategories != null && !selectedCategories.isEmpty()) ? selectedCategories.get(0) : "";
                int categoryCount = (selectedCategories != null) ? selectedCategories.size() : 0;

                // 步驟 2.2：在背景執行緒中執行耗時的資料庫查詢。
                List<StoreEntity> entities = storeRepo.searchAdvancedBlocking(keyword, singleCategory, categoryCount, dishLike, priceEq);

                // 步驟 2.3：將資料庫模型轉換為 UI 模型。
                List<Place> allPlacesFromDb = StoreMappers.toPlaceList(entities);
                if (allPlacesFromDb == null) {
                    allPlacesFromDb = new ArrayList<>(); // 確保不是 null
                }

                // 步驟 2.4：執行過濾操作，移除「不喜歡」和「已收藏」的項目。
                Set<String> dislikedIds = getDislikedIds(); // 從 SharedPreferences 讀取，這是執行緒安全的。
                List<Place> filteredResult = new ArrayList<>();
                for (Place p : allPlacesFromDb) {
                    if (p == null || p.id == null) continue;

                    // 直接使用從主執行緒傳進來的 `favoriteIds` 快照，絕對安全！
                    if (!dislikedIds.contains(p.id) && !favoriteIds.contains(p.id)) {
                        filteredResult.add(p);
                    }
                }

                // 步驟 3：【切換回主執行緒】將最終處理好的結果，透過 postValue 更新 UI。
                if (filteredResult.isEmpty()) {
                    emptyMessage.postValue("找不到符合的店家，試試看別的篩選條件吧！");
                }
                // 發送最終的、乾淨的卡片列表。
                places.postValue(filteredResult);

            } catch (Exception e) {
                // 統一的錯誤處理
                Log.e(TAG, "loadFromLocal 在背景執行緒中發生錯誤", e);
                error.postValue("載入資料時發生未知錯誤");
            } finally {
                // 無論成功或失敗，最後都標示為載入結束。
                isLoading.postValue(false);
            }
        });
    }

    // ---------------- Dislikes (SharedPreferences) ----------------

    public void addToDislikes(Place place) {
        if (place == null || place.id == null) return;
        addPlaceToDislikesInternal(place);
    }

    private void addPlaceToDislikesInternal(Place place) {
        if (place == null || place.id == null) return;
        viewModelExecutor.execute(() -> {
            Set<String> ids = new HashSet<>(prefs.getStringSet(DISLIKED_PLACES_KEY, Collections.emptySet()));
            if (ids.add(place.id)) {
                prefs.edit().putStringSet(DISLIKED_PLACES_KEY, ids).apply();
                loadDislikedPlacesFromPrefs(); // 觸發更新垃圾桶列表
            }
        });
    }

    private Set<String> getDislikedIds() {
        return new HashSet<>(prefs.getStringSet(DISLIKED_PLACES_KEY, Collections.emptySet()));
    }
    private void saveDislikedIds(Set<String> ids) {
        if (ids == null) return;
        prefs.edit().putStringSet(DISLIKED_PLACES_KEY, ids).apply();
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

                // 1. 呼叫 Repository 的 blocking 方法
                List<StoreEntity> entities = storeRepo.getByIdsBlocking(new ArrayList<>(ids));

                // 2. 轉換模型
                List<Place> list = StoreMappers.toPlaceList(entities);

                // 3. 更新 UI
                dislikedPlaces.postValue(list != null ? list : new ArrayList<>());

            } catch (Exception e) {
                Log.e(TAG, "loadDislikedPlacesFromPrefs failed", e);
                trashError.postValue("讀取垃圾桶失敗：" + e.getMessage());
            } finally {
                // 無論成功或失敗，最後都標示為載入結束
                isLoadingTrash.postValue(false);
            }
        });
    }

    public void removeFromDislikes(String placeId) {
        if (placeId == null) {
            return;
        }
        viewModelExecutor.execute(() -> {
            try {
                Set<String> dislikedIds = getDislikedIds();
                if (dislikedIds.remove(placeId)) {
                    saveDislikedIds(dislikedIds);

                    loadDislikedPlacesFromPrefs(); // 重新載入垃圾桶列表
                    loadPlaces(); // 讓主畫面的卡片堆也重新整理
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
        viewModelExecutor.shutdown(); // 在 ViewModel 銷毀時關閉執行緒池
    }
}
