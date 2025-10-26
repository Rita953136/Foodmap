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

    // --- AI Advisor (保持不變) ---
    private final MutableLiveData<String> _aiResponse = new MutableLiveData<>();
    public LiveData<String> aiResponse = _aiResponse;
    private final MutableLiveData<Boolean> _isAiLoading = new MutableLiveData<>(false);
    public LiveData<Boolean> isAiLoading = _isAiLoading;

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
        storeRepo.initFromAssets(app); // 觸發資料庫初始化檢查

        // 【關鍵改造】使用 observe (有生命週期感知) 而不是 observeForever
        // 這裡我們假設 ViewModel 的生命週期與 Application 同步，所以不手動移除觀察者
        // 另一種更簡潔的方式是在 Activity/Fragment 中觀察
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
        isLoading.setValue(true);
        error.setValue(null);
        emptyMessage.setValue("正在載入店家...");

        List<String> tags = selectedTags.getValue() != null ? selectedTags.getValue() : Collections.emptyList();
        // TagMatchMode 暫時未使用，因為 DAO 邏輯已簡化

        // ✨✨✨【改造點 2：呼叫新的 loadFromLocal】✨✨✨
        loadFromLocal(tags);
    }

    public void applyTagFilter(List<String> selected) {
        selectedTags.setValue(selected != null ? selected : new ArrayList<>());
        loadPlaces();
    }

    public void setTagMatchMode(TagMatchMode mode) {
        tagMatchMode.setValue(mode != null ? mode : TagMatchMode.ALL);
        loadPlaces();
    }

    public void handleSwipeAction(Swipe.Action action, Place place) {
        if (place == null) return;
        if (action == Swipe.Action.LIKE) {
            favStore.add(place);
        } else if (action == Swipe.Action.NOPE) {
            addToDislikes(place);
        }
    }

    // 【改造點 3：重寫 loadFromLocal，告別 observeForever】
    private void loadFromLocal(List<String> selectedCategories) {
        viewModelExecutor.execute(() -> {
            try {
                // 1. 準備 DAO 需要的參數
                String keyword = "";
                String dishLike = "";
                String priceEq = "";
                // 因為 DAO 目前只支援單一標籤搜尋，我們暫時取第一個
                String singleCategory = (selectedCategories != null && !selectedCategories.isEmpty()) ? selectedCategories.get(0) : "";
                int categoryCount = (selectedCategories != null && !selectedCategories.isEmpty()) ? selectedCategories.size() : 0;

// 使用 5 個參數呼叫
                List<StoreEntity> entities = storeRepo.searchAdvancedBlocking(keyword, singleCategory, categoryCount, dishLike, priceEq);

                // --- 後續處理不變 ---
                // 3. 將資料庫模型轉換為 UI 模型
                List<Place> all = StoreMappers.toPlaceList(entities);

                // 4. 在背景執行緒中進行過濾 (不喜歡/已收藏)
                Set<String> dislikedIds = getDislikedIds();
                Set<String> favoriteIds = new HashSet<>();
                for (Place p : favStore.getAll()) { // favStore 應是執行緒安全的
                    if (p != null && p.id != null) favoriteIds.add(p.id);
                }

                List<Place> filtered = new ArrayList<>();
                for (Place p : all) {
                    if (p == null || p.id == null) continue;
                    if (!dislikedIds.contains(p.id) && !favoriteIds.contains(p.id)) {
                        filtered.add(p);
                    }
                }

                // 5. 將最終結果發送到主執行緒以更新 UI
                if (filtered.isEmpty()) {
                    emptyMessage.postValue("本地資料沒有符合的結果，換個標籤試試。");
                }
                places.postValue(filtered);

            } catch (Exception e) {
                Log.e(TAG, "loadFromLocal failed", e);
                error.postValue("載入資料時發生錯誤: " + e.getMessage());
            } finally {
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

    public void loadDislikedPlacesFromPrefs() {
        isLoadingTrash.setValue(true);
        trashError.setValue(null);

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
                isLoadingTrash.postValue(false);
            }
        });
    }

    public void removeFromDislikes(String placeId) {
        if (placeId == null) {
            trashError.setValue("無法復原：缺少 placeId");
            return;
        }
        viewModelExecutor.execute(() -> {
            Set<String> ids = new HashSet<>(prefs.getStringSet(DISLIKED_PLACES_KEY, Collections.emptySet()));
            if (ids.remove(placeId)) {
                prefs.edit().putStringSet(DISLIKED_PLACES_KEY, ids).apply();
            }
            // 移除後，再次載入垃圾桶列表以更新 UI
            loadDislikedPlacesFromPrefs();
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        viewModelExecutor.shutdown(); // 在 ViewModel 銷毀時關閉執行緒池
    }
}
