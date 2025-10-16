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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Home / Trash 共用 ViewModel（本機版垃圾桶）
 * 支援多標籤查找：
 *  - ANY：含任一標籤（OR）
 *  - ALL：同時含所有標籤（AND，透過 client-side 過濾）
 *
 * 垃圾桶僅使用 SharedPreferences 儲存不喜歡的店家 ID。
 */
public class HomeViewModel extends AndroidViewModel {

    private static final String TAG = "HomeViewModel";
    private static final String COLLECTION = "stores_summary"; // Firestore 集合
    private static final String FIELD_TAGS = "tags_top3";      // Array<String>
    private static final String FIELD_RATING = "rating";       // Number
    private static final int MAX_ITEMS = 30;                   // 每次載入數

    public enum TagMatchMode { ANY, ALL }

    // --- UI State ---
    private final MutableLiveData<List<Place>> places = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> emptyMessage = new MutableLiveData<>("點擊或滑動卡片來探索");
    private final MutableLiveData<List<String>> selectedTags = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<TagMatchMode> tagMatchMode = new MutableLiveData<>(TagMatchMode.ALL); // 預設 AND

    // Trash（本機版）
    private final MutableLiveData<List<Place>> dislikedPlaces = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoadingTrash = new MutableLiveData<>(false);
    private final MutableLiveData<String> trashError = new MutableLiveData<>();

    // --- Data sources ---
    private static final String PREFS_NAME = "FmapUserPrefs";
    private static final String DISLIKED_PLACES_KEY = "disliked_places";
    private final SharedPreferences prefs;
    private final FirebaseFirestore db;
    private final FavoritesStore favStore;

    public HomeViewModel(@NonNull Application app) {
        super(app);
        prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        db = FirebaseFirestore.getInstance();
        favStore = FavoritesStore.getInstance(app.getApplicationContext());
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

    // ---------------- Home ----------------

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
            // Firestore 限制：array-contains-any 最多 10 個值
            List<String> queryTags = tags.size() > 10 ? tags.subList(0, 10) : tags;
            q = q.whereArrayContainsAny(FIELD_TAGS, queryTags);
        }

        q = q.orderBy(FIELD_RATING, Query.Direction.DESCENDING)
                .limit(MAX_ITEMS);

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

    // Firestore 回傳處理（支援 ALL 模式的 client-side 過濾）
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
                    // 先取 tags_top3 做 ALL 過濾，再轉 Place
                    List<String> docTags = (List<String>) d.get(FIELD_TAGS);

                    if (requireAll) {
                        if (docTags == null || !docTags.containsAll(selectedTags)) {
                            continue; // 不符合 ALL
                        }
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

    /** 以 SharedPreferences 的 ID 清單載入垃圾桶列表（每批 10 筆 whereIn(docId)）。 */
    public void loadDislikedPlacesFromPrefs() {
        isLoadingTrash.setValue(true);
        trashError.setValue(null);

        Set<String> ids = getDislikedIds();
        if (ids == null || ids.isEmpty()) {
            dislikedPlaces.setValue(new ArrayList<>());
            isLoadingTrash.setValue(false);
            return;
        }

        List<String> idList = new ArrayList<>(ids);
        final int totalBatches = (idList.size() + 9) / 10;
        AtomicInteger done = new AtomicInteger(0);
        List<Place> collected = new ArrayList<>();

        for (int i = 0; i < idList.size(); i += 10) {
            List<String> batch = idList.subList(i, Math.min(i + 10, idList.size()));
            db.collection(COLLECTION)
                    .whereIn(FieldPath.documentId(), batch)
                    .get()
                    .addOnSuccessListener(snap -> {
                        if (snap != null) {
                            for (DocumentSnapshot d : snap.getDocuments()) {
                                try {
                                    Place p = d.toObject(Place.class);
                                    if (p != null) {
                                        p.id = d.getId();
                                        collected.add(p);
                                    }
                                } catch (Exception ignore) {}
                            }
                        }
                        if (done.incrementAndGet() == totalBatches) {
                            dislikedPlaces.setValue(collected);
                            isLoadingTrash.setValue(false);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "loadDislikedPlacesFromPrefs batch failed", e);
                        if (done.incrementAndGet() == totalBatches) {
                            trashError.setValue("讀取垃圾桶失敗：" + e.getMessage());
                            dislikedPlaces.setValue(collected);
                            isLoadingTrash.setValue(false);
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
}
