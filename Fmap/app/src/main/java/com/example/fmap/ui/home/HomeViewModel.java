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

public class HomeViewModel extends AndroidViewModel {

    private static final String COLLECTION = "stores_summary";
    private static final int MAX_ITEMS = 10;

    // LiveData for UI
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
                // Log if a specific document fails to parse
                // This makes your app more robust if one document has bad data
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
        // The rest of the fields are default null/0
        favStore.addOrUpdate(fav);
    }
}
