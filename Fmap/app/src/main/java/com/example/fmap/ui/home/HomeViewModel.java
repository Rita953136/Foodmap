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
import com.example.fmap.model.Swipe; // 假設你有這個 Enum
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * App 的核心 ViewModel，管理 HomeFragment 和 TrashFragment 的所有業務邏輯。
 */
public class HomeViewModel extends AndroidViewModel {

    private static final String COLLECTION = "stores_summary";
    private static final int MAX_ITEMS = 20; // 增加每次載入的數量
    private static final String TAG = "HomeViewModel";

    // --- LiveData for UI state ---
    private final MutableLiveData<List<Place>> places = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> emptyMessage = new MutableLiveData<>("點擊或滑動卡片來探索");
    private final MutableLiveData<List<String>> selectedTags = new MutableLiveData<>();

    // LiveData for TrashFragment
    private final MutableLiveData<List<Place>> dislikedPlaces = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoadingTrash = new MutableLiveData<>(false);
    private final MutableLiveData<String> trashError = new MutableLiveData<>();

    // --- SharedPreferences for disliked places ---
    private static final String PREFS_NAME = "FmapUserPrefs";
    private static final String DISLIKED_PLACES_KEY = "disliked_places";
    private final SharedPreferences prefs;

    // --- Data Sources ---
    private final FirebaseFirestore db;
    private final FavoritesStore favStore;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.db = FirebaseFirestore.getInstance();
        this.favStore = FavoritesStore.getInstance(application.getApplicationContext());
    }

    // --- Public LiveData Getters ---
    public LiveData<List<Place>> getPlaces() { return places; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getError() { return error; }
    public LiveData<String> getEmptyMessage() { return emptyMessage; }
    public LiveData<List<Place>> getDislikedPlaces() { return dislikedPlaces; }
    public LiveData<Boolean> getIsLoadingTrash() { return isLoadingTrash; }
    public LiveData<String> getTrashError() { return trashError; }

    // --- HomeFragment Business Logic ---

    /**
     * 從 Firestore 載入推薦店家列表
     */
    public void loadPlaces() {
        if (Boolean.TRUE.equals(isLoading.getValue())) return;
        isLoading.setValue(true);
        error.setValue(null);
        emptyMessage.setValue("正在載入店家...");

        Query q = db.collection(COLLECTION)
                .orderBy("rating", Query.Direction.DESCENDING)
                .limit(MAX_ITEMS);

        q.get()
                .addOnSuccessListener(this::onPlacesLoaded)
                .addOnFailureListener(this::onLoadFailed);
    }

    /**
     * 處理卡片滑動操作
     */
    public void handleSwipeAction(Swipe.Action action, Place place) {
        if (place == null) return;

        if (action == Swipe.Action.LIKE) {
            favStore.add(place);
        } else if (action == Swipe.Action.NOPE) {
            addToDislikes(place);
        }
    }


    // --- TrashFragment Business Logic ---

    /**
     * 載入所有被標記為「不喜歡」的店家
     */
    public void loadDislikedPlaces() {
        isLoadingTrash.setValue(true);
        trashError.setValue(null);

        Set<String> idSet = getDislikedIds();
        if (idSet.isEmpty()) {
            dislikedPlaces.setValue(new ArrayList<>());
            isLoadingTrash.setValue(false);
            return;
        }

        // Firestore's "in" query is limited to 10 items per query.
        // We must batch the requests if there are more than 10 disliked items.
        // For now, let's assume we won't hit this limit often and query the first 10.
        // A more robust solution would involve batching.
        List<String> idList = new ArrayList<>(idSet);
        if (idList.size() > 10) {
            idList = idList.subList(0, 10);
        }

        db.collection(COLLECTION)
                .whereIn(FieldPath.documentId(), idList)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Place> resultList = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        try {
                            Place p = doc.toObject(Place.class);
                            if (p != null) {
                                p.id = doc.getId();
                                resultList.add(p);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to parse disliked place: " + doc.getId(), e);
                        }
                    }
                    dislikedPlaces.setValue(resultList);
                    isLoadingTrash.setValue(false);
                })
                .addOnFailureListener(e -> {
                    trashError.setValue("讀取失敗: " + e.getMessage());
                    isLoadingTrash.setValue(false);
                });
    }

    /**
     * 將一個店家從「不喜歡」列表中移除（還原）
     */
    public void removeFromDislikes(String placeId) {
        if (placeId == null || placeId.isEmpty()) return;

        Set<String> dislikedIds = getDislikedIds();
        if (dislikedIds.remove(placeId)) {
            prefs.edit().putStringSet(DISLIKED_PLACES_KEY, dislikedIds).apply();
            // 直接從當前的 LiveData 列表中移除，避免重新網路請求，反應更即時
            List<Place> currentList = dislikedPlaces.getValue();
            if (currentList != null) {
                List<Place> newList = currentList.stream()
                        .filter(p -> !placeId.equals(p.id))
                        .collect(Collectors.toList());
                dislikedPlaces.setValue(newList);
            }
        }
    }

    // --- Private Helper Methods ---

    private void addToDislikes(Place place) {
        if (place == null || place.id == null) return;
        Set<String> dislikedIds = getDislikedIds();
        if (dislikedIds.add(place.id)) {
            prefs.edit().putStringSet(DISLIKED_PLACES_KEY, dislikedIds).apply();
        }
    }

    private Set<String> getDislikedIds() {
        // 複製一份，避免直接修改原始 Set
        return new HashSet<>(prefs.getStringSet(DISLIKED_PLACES_KEY, Collections.emptySet()));
    }

    private void onPlacesLoaded(QuerySnapshot snap) {
        // 過濾已「不喜歡」與「已收藏」的店家
        Set<String> dislikedIds = getDislikedIds();
        Set<String> favoriteIds = new HashSet<>(favStore.getAllIds()); // 從優化後的 favStore 獲取 ID 列表

        List<Place> newList = new ArrayList<>();
        for (DocumentSnapshot d : snap.getDocuments()) {
            try {
                Place p = d.toObject(Place.class);
                if (p != null) {
                    p.id = d.getId();
                    boolean isDisliked = dislikedIds.contains(p.id);
                    boolean isFavorite = favoriteIds.contains(p.id);

                    if (!isDisliked && !isFavorite) {
                        newList.add(p);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse place: " + d.getId(), e);
            }
        }

        if (newList.isEmpty()) {
            emptyMessage.setValue("太棒了！您已滑完所有推薦店家！");
        }

        places.setValue(newList);
        isLoading.setValue(false);
    }

    private void onLoadFailed(Exception e) {
        isLoading.setValue(false);
        error.setValue("讀取店家資料失敗，請檢查網路連線。");
        Log.e(TAG, "Firestore load failed", e);
    }

    public void applyTagFilter(List<String> selected) {
        selectedTags.setValue(selected);
    }
    public LiveData<List<String>> getSelectedTags() {
        return selectedTags;
    }
}
