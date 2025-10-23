package com.example.fmap.ui.home;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.fmap.BuildConfig;
import com.example.fmap.data.local.StoreEntity;      // 本地 Room Entity
import com.example.fmap.data.local.StoreMappers;     // Entity <-> Place 映射
import com.example.fmap.model.Place;
import com.example.fmap.model.Swipe;
import com.example.fmap.util.OpenAIClient;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * 純本機資料版 HomeViewModel：
 * - 來源：Room (StoreRepository) + SharedPreferences (dislikes)
 * - 收藏：FavoritesStore
 * - AI 顧問：OpenAIClient
 */
public class HomeViewModel extends AndroidViewModel {

    private static final String TAG = "HomeViewModel";

    public enum TagMatchMode { ANY, ALL }

    // --- UI State LiveData ---
    private final MutableLiveData<List<Place>> places = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> emptyMessage = new MutableLiveData<>("點擊或滑動卡片來探索");
    private final MutableLiveData<List<String>> selectedTags = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<TagMatchMode> tagMatchMode = new MutableLiveData<>(TagMatchMode.ALL);

    // --- Trash（不喜歡） ---
    private final MutableLiveData<List<Place>> dislikedPlaces = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoadingTrash = new MutableLiveData<>(false);
    private final MutableLiveData<String> trashError = new MutableLiveData<>();

    // --- AI Advisor ---
    private final MutableLiveData<String> _aiResponse = new MutableLiveData<>();
    public LiveData<String> aiResponse = _aiResponse;
    private final MutableLiveData<Boolean> _isAiLoading = new MutableLiveData<>(false);
    public LiveData<Boolean> isAiLoading = _isAiLoading;

    // --- Local stores & services ---
    private static final String PREFS_NAME = "FmapUserPrefs";
    private static final String DISLIKED_PLACES_KEY = "disliked_places";

    private final SharedPreferences prefs;
    private final FavoritesStore favStore;
    private final Gson gson;
    private final ExecutorService executor;
    private final OpenAIClient openAIClient;

    // Room Repository（你專案內的本地資料存取）
    private final StoreRepository storeRepo;

    public HomeViewModel(@NonNull Application app) {
        super(app);
        prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        favStore = FavoritesStore.getInstance(app.getApplicationContext());
        gson = new Gson();
        executor = Executors.newSingleThreadExecutor();
        openAIClient = new OpenAIClient(BuildConfig.OPENAI_API_KEY);

        storeRepo = new StoreRepository(app);
        storeRepo.initFromAssets(app); // 首次安裝匯入

        // DB ready 後自動載入一次（若目前列表為空）
        storeRepo.getDbReady().observeForever(ready -> {
            if (Boolean.TRUE.equals(ready)) {
                if (places.getValue() == null || places.getValue().isEmpty()) {
                    loadPlaces();
                }
            }
        });
    }

    // --- LiveData getters ---
    public LiveData<List<Place>> getPlaces() { return places; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getError() { return error; }
    public LiveData<String> getEmptyMessage() { return emptyMessage; }
    public LiveData<List<String>> getSelectedTags() { return selectedTags; }
    public LiveData<TagMatchMode> getTagMatchMode() { return tagMatchMode; }
    public LiveData<List<Place>> getDislikedPlaces() { return dislikedPlaces; }
    public LiveData<Boolean> getIsLoadingTrash() { return isLoadingTrash; }
    public LiveData<String> getTrashError() { return trashError; }
    public LiveData<List<Place>> getCurrentPlaces() { return places; }

    // ---------------- AI Recommendation ----------------
    public void getAiRecommendation(String userQuestion, List<Place> availablePlaces) {
        if (Boolean.TRUE.equals(_isAiLoading.getValue())) return;
        _isAiLoading.postValue(true);
        _aiResponse.postValue("AI 思考中...");

        executor.execute(() -> {
            OpenAIClient.AgentBuilder agentBuilder = openAIClient.new AgentBuilder()
                    .setSystemMessage("你是一個樂於助人的美食顧問。請用台灣人習慣的繁體中文和親切口氣來回答問題。")
                    .addUserMessage(userQuestion);

            openAIClient.createChatCompletion(agentBuilder, new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    _aiResponse.postValue("抱歉，網路有點不穩，請稍後再試一次。");
                    _isAiLoading.postValue(false);
                }

                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful() || response.body() == null) {
                        _aiResponse.postValue("抱歉，我好像有點短路了，可以再問一次嗎？");
                        _isAiLoading.postValue(false);
                        return;
                    }
                    String responseBody = response.body().string();
                    JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);
                    String aiReply = jsonObject.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .get("message").getAsJsonObject()
                            .get("content").getAsString();
                    _aiResponse.postValue(aiReply);
                    _isAiLoading.postValue(false);
                }
            });
        });
    }

    // ---------------- 載入店家（純本機） ----------------

    /** 依目前 selectedTags + tagMatchMode 進行載入（只走 Room）。 */
    public void loadPlaces() {
        if (Boolean.TRUE.equals(isLoading.getValue())) return;
        isLoading.setValue(true);
        error.setValue(null);
        emptyMessage.setValue("正在載入店家...");

        List<String> tags = selectedTags.getValue() != null ? selectedTags.getValue() : Collections.emptyList();
        TagMatchMode mode = tagMatchMode.getValue() != null ? tagMatchMode.getValue() : TagMatchMode.ALL;

        loadFromLocal(tags, mode);
    }

    /** 設定/更新標籤篩選（多選），並重新查詢。 */
    public void applyTagFilter(List<String> selected) {
        selectedTags.setValue(selected != null ? selected : new ArrayList<>());
        loadPlaces();
    }

    /** 切換匹配模式（ANY/ALL），並重新查詢。 */
    public void setTagMatchMode(TagMatchMode mode) {
        tagMatchMode.setValue(mode != null ? mode : TagMatchMode.ALL);
        loadPlaces();
    }

    /** 卡片滑動行為（LIKE → 收藏；NOPE → 存本機不喜歡）。 */
    public void handleSwipeAction(Swipe.Action action, Place place) {
        if (place == null) return;
        if (action == Swipe.Action.LIKE) {
            favStore.add(place); // 加入收藏
        } else if (action == Swipe.Action.NOPE) {
            addToDislikes(place); // 加入不喜歡名單
        }
    }

    // --- 本地 Room ---
    private void loadFromLocal(List<String> selectedCategories, TagMatchMode mode) {
        final List<String> categories = (selectedCategories != null)
                ? selectedCategories : Collections.emptyList();

        final String keyword  = ""; // 若要支援關鍵字再帶值
        final String dishLike = ""; // 若要支援菜名再帶值
        final String priceEq  = ""; // 若要支援價位再帶值

        LiveData<List<StoreEntity>> live = storeRepo.searchAdvanced(keyword, categories, dishLike, priceEq);

        Observer<List<StoreEntity>> once = new Observer<List<StoreEntity>>() {
            @Override
            public void onChanged(List<StoreEntity> entities) {
                live.removeObserver(this);

                List<Place> all = StoreMappers.toPlaceList(entities);

                // 過濾：不喜歡 / 已收藏
                Set<String> dislikedIds = getDislikedIds();
                Set<String> favoriteIds = new HashSet<>();
                for (Place p : favStore.getAll()) {
                    if (p != null && p.id != null) favoriteIds.add(p.id);
                }

                List<Place> filtered = new ArrayList<>();
                for (Place p : all) {
                    if (p == null || p.id == null) continue;
                    if (!dislikedIds.contains(p.id) && !favoriteIds.contains(p.id)) {
                        // 若要真正做 ALL/ANY 在本地端，可以把 categories 與你的 tags/category 對應加上判斷
                        filtered.add(p);
                    }
                }

                if (filtered.isEmpty()) {
                    emptyMessage.setValue("本地資料沒有符合的結果，換個標籤試試。");
                }
                places.setValue(filtered);
                isLoading.setValue(false);
            }
        };

        live.observeForever(once);
    }

    // ---------------- Dislikes (SharedPreferences) ----------------
    private void addToDislikes(Place place) {
        if (place == null || place.id == null) return;
        Set<String> ids = new HashSet<>(prefs.getStringSet(DISLIKED_PLACES_KEY, Collections.emptySet()));
        if (ids.add(place.id)) {
            prefs.edit().putStringSet(DISLIKED_PLACES_KEY, ids).apply();
        }
    }

    private Set<String> getDislikedIds() {
        return new HashSet<>(prefs.getStringSet(DISLIKED_PLACES_KEY, Collections.emptySet()));
    }

    // ---------------- Trash（純本機 by IDs） ----------------
    public void loadDislikedPlacesFromPrefs() {
        isLoadingTrash.setValue(true);
        trashError.setValue(null);

        Set<String> ids = getDislikedIds();
        if (ids.isEmpty()) {
            dislikedPlaces.setValue(new ArrayList<>());
            isLoadingTrash.setValue(false);
            return;
        }

        // 透過 Room 以 ID 清單撈回（請確認你的 StoreRepository 有對應方法）
        LiveData<List<StoreEntity>> live = storeRepo.getByIds(new ArrayList<>(ids));
        Observer<List<StoreEntity>> once = new Observer<List<StoreEntity>>() {
            @Override
            public void onChanged(List<StoreEntity> entities) {
                live.removeObserver(this);
                try {
                    List<Place> list = StoreMappers.toPlaceList(entities);
                    dislikedPlaces.postValue(list != null ? list : new ArrayList<>());
                } catch (Exception e) {
                    Log.e(TAG, "loadDislikedPlacesFromPrefs mapping failed", e);
                    trashError.postValue("讀取垃圾桶失敗：" + e.getMessage());
                } finally {
                    isLoadingTrash.postValue(false);
                }
            }
        };
        live.observeForever(once);
    }

    /** 復原：從 prefs 移除，然後重新載入。 */
    public void removeFromDislikes(String placeId) {
        if (placeId == null) {
            trashError.setValue("無法復原：缺少 placeId");
            return;
        }
        Set<String> ids = new HashSet<>(prefs.getStringSet(DISLIKED_PLACES_KEY, Collections.emptySet()));
        if (ids.remove(placeId)) {
            prefs.edit().putStringSet(DISLIKED_PLACES_KEY, ids).apply();
        }
        loadDislikedPlacesFromPrefs();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
