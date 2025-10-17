package com.example.fmap.ui.home;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fmap.data.FavoritesStore;
import com.example.fmap.model.Place;
import com.example.fmap.model.Swipe;
import com.example.fmap.util.OpenAIClient; // ✨ 1. 匯入我們剛剛建立的 OpenAIClient
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;    // ✨ 2. 匯入 ExecutorService
import java.util.concurrent.Executors;      // ✨ 2. 匯入 Executors
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class HomeViewModel extends AndroidViewModel {

    private static final String TAG = "HomeViewModel";
    private static final String COLLECTION = "stores_summary";
    private static final String FIELD_TAGS = "tags_top3";
    private static final String FIELD_RATING = "rating";
    private static final int MAX_ITEMS = 30;

    public enum TagMatchMode { ANY, ALL }

    // --- UI State LiveData ---
    private final MutableLiveData<List<Place>> places = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> emptyMessage = new MutableLiveData<>("點擊或滑動卡片來探索");
    private final MutableLiveData<List<String>> selectedTags = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<TagMatchMode> tagMatchMode = new MutableLiveData<>(TagMatchMode.ALL);

    // --- Trash LiveData (本機版) ---
    private final MutableLiveData<List<Place>> dislikedPlaces = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoadingTrash = new MutableLiveData<>(false);
    private final MutableLiveData<String> trashError = new MutableLiveData<>();

    // --- AI Advisor LiveData (保持不變) ---
    private final MutableLiveData<String> _aiResponse = new MutableLiveData<>();
    public LiveData<String> aiResponse = _aiResponse;
    private final MutableLiveData<Boolean> _isAiLoading = new MutableLiveData<>(false);
    public LiveData<Boolean> isAiLoading = _isAiLoading;

    // --- Data sources & Services ---
    private static final String PREFS_NAME = "FmapUserPrefs";
    private static final String DISLIKED_PLACES_KEY = "disliked_places";
    private final SharedPreferences prefs;
    private final FirebaseFirestore db;
    private final FavoritesStore favStore;
    private final Gson gson;

    // ✨ 3. 移除 Retrofit，改用 ExecutorService 處理背景任務
    private final ExecutorService executor;

    public HomeViewModel(@NonNull Application app) {
        super(app);
        prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        db = FirebaseFirestore.getInstance();
        favStore = FavoritesStore.getInstance(app.getApplicationContext());
        gson = new Gson();

        // 初始化單一執行緒的執行緒池
        executor = Executors.newSingleThreadExecutor();
    }

    // --- LiveData Getters (保持不變) ---
    public LiveData<List<Place>> getPlaces() { return places; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getError() { return error; }
    public LiveData<String> getEmptyMessage() { return emptyMessage; }
    public LiveData<List<String>> getSelectedTags() { return selectedTags; }
    public LiveData<TagMatchMode> getTagMatchMode() { return tagMatchMode; }
    public LiveData<List<Place>> getDislikedPlaces() { return dislikedPlaces; }
    public LiveData<Boolean> getIsLoadingTrash() { return isLoadingTrash; }
    public LiveData<String> getTrashError() { return trashError; }

    // ---------------- AI Recommendation ----------------

    /**
     * ✨ 4. 【核心修改】修改此方法，使用 OpenAIClient 取得推薦
     * @param userQuestion 使用者的問題。
     * @param availablePlaces 當前可用於推薦的店家列表。
     */
    public void getAiRecommendation(String userQuestion, List<Place> availablePlaces) {
        if (Boolean.TRUE.equals(_isAiLoading.getValue())) return;

        _isAiLoading.postValue(true); // 開始載入
        _aiResponse.postValue("AI 思考中...");

        // 在背景執行緒中執行網路請求
        executor.execute(() -> {
            // 將店家列表轉換為 JSON 字串
            String placesJson = gson.toJson(availablePlaces);

            // 組裝我們精心設計的 Prompt
            String prompt = String.format(
                    "你是「Foodmap」App 的美食顧問。請根據以下 JSON 格式的店家資料，為使用者回答問題。" +
                            "你的回答必須簡潔、友善，並且只能從我提供的資料中選擇，不許自己編造。\n\n" +
                            "[店家資料]\n%s\n\n" +
                            "[使用者問題]\n%s\n\n" +
                            "請從店家資料中，推薦最符合的1-2個店家，並簡單說明原因。",
                    placesJson,
                    userQuestion
            );

            // 直接呼叫你的 OpenAIClient
            String reply = OpenAIClient.ask(prompt);

            // 將結果切換回主執行緒來更新 LiveData
            _aiResponse.postValue(reply);
            _isAiLoading.postValue(false); // 結束載入
        });
    }


    // ---------------- Home (以下程式碼保持不變) ----------------

    /** 依目前 selectedTags + tagMatchMode 以索引查詢 Firestore。 */
    public void loadPlaces() {
        if (Boolean.TRUE.equals(isLoading.getValue())) return;

        isLoading.setValue(true);
        error.setValue(null);
        emptyMessage.setValue("正在載入店家...");

        List<String> tags = selectedTags.getValue() != null ? selectedTags.getValue() : Collections.emptyList();
        TagMatchMode mode = tagMatchMode.getValue() != null ? tagMatchMode.getValue() : TagMatchMode.ALL;

        Query q = db.collection(COLLECTION);

        if (!tags.isEmpty()) {
            List<String> queryTags = tags.size() > 10 ? tags.subList(0, 10) : tags;
            q = q.whereArrayContainsAny(FIELD_TAGS, queryTags);
        }

        q = q.orderBy(FIELD_RATING, Query.Direction.DESCENDING).limit(MAX_ITEMS);

        final List<String> selectedCopy = new ArrayList<>(tags);
        final TagMatchMode modeCopy = mode;

        q.get()
                .addOnSuccessListener(snap -> onPlacesLoaded(snap, selectedCopy, modeCopy))
                .addOnFailureListener(this::onLoadFailed);
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
            favStore.add(place);
        } else if (action == Swipe.Action.NOPE) {
            addToDislikes(place);
        }
    }

    private void onPlacesLoaded(QuerySnapshot snap, List<String> selectedTags, TagMatchMode mode) {
        Set<String> dislikedIds = getDislikedIds();
        Set<String> favoriteIds = favStore.getAll().stream()
                .map(p -> p.id)
                .collect(Collectors.toSet());

        boolean requireAll = mode == TagMatchMode.ALL && !selectedTags.isEmpty();

        List<Place> list = new ArrayList<>();
        if (snap != null) {
            for (DocumentSnapshot d : snap.getDocuments()) {
                try {
                    List<String> docTags = (List<String>) d.get(FIELD_TAGS);
                    if (requireAll && (docTags == null || !docTags.containsAll(selectedTags))) {
                        continue;
                    }
                    Place p = d.toObject(Place.class);
                    if (p != null) {
                        p.id = d.getId();
                        if (!dislikedIds.contains(p.id) && !favoriteIds.contains(p.id)) {
                            list.add(p);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析店家資料失敗: " + d.getId(), e);
                }
            }
        }

        if (list.isEmpty()) {
            emptyMessage.setValue(requireAll
                    ? "沒有同時包含所有已選標籤的店家，試著減少標籤吧！"
                    : "沒有符合的結果，換個標籤試試。");
        }
        places.setValue(list);
        isLoading.setValue(false);
    }

    private void onLoadFailed(@NonNull Exception e) {
        isLoading.setValue(false);
        error.setValue("讀取店家資料失敗，請檢查網路連線。");
        Log.e(TAG, "Firestore 讀取失敗", e);
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

    // ---------------- Trash：用本機 prefs 的 ID 批次撈回 Place ----------------

    public void loadDislikedPlacesFromPrefs() {
        isLoadingTrash.setValue(true);
        trashError.setValue(null);

        Set<String> ids = getDislikedIds();
        if (ids.isEmpty()) {
            dislikedPlaces.setValue(new ArrayList<>());
            isLoadingTrash.setValue(false);
            return;
        }

        List<String> idList = new ArrayList<>(ids);
        final int totalBatches = (idList.size() + 9) / 10;
        AtomicInteger done = new AtomicInteger(0);
        final List<Place> collected = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < idList.size(); i += 10) {
            List<String> batch = idList.subList(i, Math.min(i + 10, idList.size()));
            db.collection(COLLECTION)
                    .whereIn(FieldPath.documentId(), batch)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            for (DocumentSnapshot d : task.getResult()) {
                                try {
                                    Place p = d.toObject(Place.class);
                                    if (p != null) {
                                        p.id = d.getId();
                                        collected.add(p);
                                    }
                                } catch (Exception ignore) {}
                            }
                        } else {
                            Log.e(TAG, "loadDislikedPlacesFromPrefs batch failed", task.getException());
                        }

                        if (done.incrementAndGet() == totalBatches) {
                            dislikedPlaces.postValue(collected);
                            isLoadingTrash.postValue(false);
                            if (!task.isSuccessful()){
                                trashError.postValue("讀取垃圾桶失敗：" + task.getException().getMessage());
                            }
                        }
                    });
        }
    }

    /** 復原：從本機 prefs 移除，然後以 prefs 重新載入。 */
    public void removeFromDislikes(String placeId) {
        if (placeId == null) {
            trashError.setValue("Cannot restore item: place ID is missing.");
            return;
        }

        Set<String> ids = new HashSet<>(prefs.getStringSet(DISLIKED_PLACES_KEY, Collections.emptySet()));
        if (ids.remove(placeId)) {
            prefs.edit().putStringSet(DISLIKED_PLACES_KEY, ids).apply();
        }

        loadDislikedPlacesFromPrefs();
    }

    // ✨ 5. 當 ViewModel 被銷毀時，關閉執行緒池，避免記憶體洩漏
    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
