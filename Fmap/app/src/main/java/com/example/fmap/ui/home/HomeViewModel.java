package com.example.fmap.ui.home;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fmap.data.FavoritesStore;
import com.example.fmap.model.FavItem;
import com.example.fmap.model.Place;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
public class HomeViewModel extends AndroidViewModel {

    private static final String COLLECTION = "stores_summary";
    private static final int MAX_ITEMS = 10;
    private final MutableLiveData<List<Place>> places = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> emptyMessage = new MutableLiveData<>("目前沒有可顯示的店家");

    // Repository/Data sources
    private final FirebaseFirestore db;
    private final FavoritesStore favStore;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        this.db = FirebaseFirestore.getInstance();
        this.favStore = new FavoritesStore(application.getApplicationContext());
    }

    // Public LiveData getters for the Fragment to observe
    public LiveData<List<Place>> getPlaces() {
        return places;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<String> getEmptyMessage() {
        return emptyMessage;
    }

    public void setNoMorePlacesMessage() {
        emptyMessage.setValue("今日已沒有店家");
    }

    public void loadPlaces() {
        if (Boolean.TRUE.equals(isLoading.getValue())) return;
        isLoading.setValue(true);

        Query q = db.collection(COLLECTION)
                .orderBy("rating", Query.Direction.DESCENDING)
                .limit(MAX_ITEMS); // Fetch only the items you need

        q.get()
                .addOnSuccessListener(this::onPageLoaded)
                .addOnFailureListener(this::onLoadFailed);
    }

    /** 依多選標籤篩選 */
    public void applyTagFilter(@NonNull List<String> tags) {
        // 去重、去空字串、保留順序
        List<String> cleaned = sanitizeTags(tags);

        if (cleaned.isEmpty()) {
            loadPlaces();           // 預設熱門清單
            return;
        }

        if (Boolean.TRUE.equals(isLoading.getValue())) return;
        isLoading.setValue(true);
        error.setValue(null);       // 清掉舊錯誤（可選）

        Query q = db.collection(COLLECTION)
                .whereArrayContainsAny("tags_top3", cleaned) // <= 最多 10 個
                .orderBy("rating", Query.Direction.DESCENDING)
                .limit(MAX_ITEMS);

        q.get()
                .addOnSuccessListener(this::onPageLoaded)
                .addOnFailureListener(this::onLoadFailed);
    }

    /** 去掉空字串、去重、最多取 10 個*/
    private List<String> sanitizeTags(List<String> input) {
        if (input == null || input.isEmpty()) return Collections.emptyList();
        Set<String> set = new LinkedHashSet<>();
        for (String s : input) {
            if (s == null) continue;
            String t = s.trim();
            if (!t.isEmpty()) set.add(t);
            if (set.size() == 10) break; // whereArrayContainsAny 上限
        }
        return new ArrayList<>(set);
    }
    private void onPageLoaded(QuerySnapshot snap) {
        List<Place> newList = new ArrayList<>();
        for (DocumentSnapshot d : snap.getDocuments()) {
            try {
                Place p = d.toObject(Place.class); // Automatic mapping
                if (p != null) {
                    p.id = d.getId(); // Set ID manually as it's not part of the document fields
                    newList.add(p);
                }
            } catch (Exception e) {
                error.setValue("無法解析店家資料: " + d.getId());
            }
        }
        places.setValue(newList);
        isLoading.setValue(false);
    }

    private void onLoadFailed(Exception e) {
        isLoading.setValue(false);
        error.setValue(e.getMessage());
    }

    public void addToFavorites(Place p) {
        FavItem fav = new FavItem();
        fav.id = p.id;
        fav.name = p.name;
        fav.thumbnailUrl = p.photoUrl;
        fav.rating = p.rating;
        fav.tags = p.tags;
        favStore.addOrUpdate(fav);
    }
}
