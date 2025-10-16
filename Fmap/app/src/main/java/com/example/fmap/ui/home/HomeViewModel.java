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
import com.example.fmap.model.FavItem;
import com.example.fmap.model.Place;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class HomeViewModel extends AndroidViewModel {

    private static final String COLLECTION = "stores_summary";
    private static final int MAX_ITEMS = 10;
    private static final String TAG = "HomeViewModel";

    // LiveData for HomeFragment
    private final MutableLiveData<List<Place>> places = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> emptyMessage = new MutableLiveData<>("目前沒有可顯示的店家");

    // LiveData for TrashFragment
    private final MutableLiveData<List<Place>> dislikedPlaces = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoadingTrash = new MutableLiveData<>();
    private final MutableLiveData<String> trashError = new MutableLiveData<>();

    // SharedPreferences for disliked places
    private static final String PREFS_NAME = "FmapUserPrefs";
    private static final String DISLIKED_PLACES_KEY = "disliked_places";
    private final SharedPreferences prefs;

    // Data sources
    private final FirebaseFirestore db;
    private final FavoritesStore favStore;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.db = FirebaseFirestore.getInstance();
        this.favStore = new FavoritesStore(application.getApplicationContext());
    }

    // --- Public LiveData Getters ---
    public LiveData<List<Place>> getPlaces() { return places; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getError() { return error; }
    public LiveData<String> getEmptyMessage() { return emptyMessage; }
    public LiveData<List<Place>> getDislikedPlaces() { return dislikedPlaces; }
    public LiveData<Boolean> getIsLoadingTrash() { return isLoadingTrash; }
    public LiveData<String> getTrashError() { return trashError; }


    // --- HomeFragment Methods ---

    public void setNoMorePlacesMessage() {
        emptyMessage.setValue("今日已沒有店家");
    }

    public void loadPlaces() {
        if (Boolean.TRUE.equals(isLoading.getValue())) return;
        isLoading.setValue(true);
        error.setValue(null);

        Query q = db.collection(COLLECTION)
                .orderBy("rating", Query.Direction.DESCENDING)
                .limit(MAX_ITEMS);

        q.get()
                .addOnSuccessListener(this::onPageLoaded)
                .addOnFailureListener(this::onLoadFailed);
    }

    public void applyTagFilter(@NonNull List<String> tags) {
        List<String> cleaned = sanitizeTags(tags);

        if (cleaned.isEmpty()) {
            loadPlaces(); // No tags, load default popular list
            return;
        }

        if (Boolean.TRUE.equals(isLoading.getValue())) return;
        isLoading.setValue(true);
        error.setValue(null);

        Query q = db.collection(COLLECTION)
                .whereArrayContainsAny("tags_top3", cleaned) // Firestore limit is 10
                .orderBy("rating", Query.Direction.DESCENDING)
                .limit(MAX_ITEMS);

        q.get()
                .addOnSuccessListener(this::onPageLoaded)
                .addOnFailureListener(this::onLoadFailed);
    }

    public void addToFavorites(Place p) {
        if (p == null) return;
        FavItem fav = new FavItem();
        fav.id = p.id;
        fav.name = p.name;
        fav.thumbnailUrl = p.photoUrl;
        fav.rating = p.rating;
        fav.tags = p.tags;
        favStore.addOrUpdate(fav);
    }

    // --- Trash Related Methods ---

    /** Adds a place to the dislike list (trash bin) */
    public void addToDislikes(Place place) {
        if (place == null || place.id == null) return;

        Set<String> dislikedIds = new HashSet<>(prefs.getStringSet(DISLIKED_PLACES_KEY, new HashSet<>()));
        if (dislikedIds.add(place.id)) {
            prefs.edit().putStringSet(DISLIKED_PLACES_KEY, dislikedIds).apply();
        }

        // Instantly remove the item from the current list for immediate UI feedback
        List<Place> currentList = places.getValue();
        if (currentList != null && !currentList.isEmpty()) {
            List<Place> newList = currentList.stream()
                    .filter(p -> !place.id.equals(p.id))
                    .collect(Collectors.toList());
            places.setValue(newList);
        }
    }

    /** Clears the dislike list (trash bin) */
    public void clearDislikes() {
        prefs.edit().remove(DISLIKED_PLACES_KEY).apply();
        // Update the live data to reflect the change
        dislikedPlaces.setValue(new ArrayList<>());
    }

    /** Removes a place from the dislike list (for restoring from trash) */
    public void removeFromDislikes(String placeId) {
        Set<String> dislikedIds = new HashSet<>(prefs.getStringSet(DISLIKED_PLACES_KEY, new HashSet<>()));
        if (dislikedIds.remove(placeId)) {
            prefs.edit().putStringSet(DISLIKED_PLACES_KEY, dislikedIds).apply();
            // Reload the disliked list to update the UI
            loadDislikedPlaces();
        }
    }

    /** Loads the full Place objects for the TrashFragment */
    public void loadDislikedPlaces() {
        isLoadingTrash.setValue(true);
        trashError.setValue(null);

        Set<String> idSet = getDislikedIds();
        if (idSet.isEmpty()) {
            dislikedPlaces.setValue(new ArrayList<>());
            isLoadingTrash.setValue(false);
            return;
        }

        // Firestore 'in' query is limited to 30 items at a time.
        // If you might have more, you need to batch the requests.
        // For now, we assume fewer than 30.
        db.collection(COLLECTION).whereIn(com.google.firebase.firestore.FieldPath.documentId(), new ArrayList<>(idSet))
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

    // --- Private Helper Methods ---

    /** Gets the set of disliked place IDs from SharedPreferences */
    private Set<String> getDislikedIds() {
        return prefs.getStringSet(DISLIKED_PLACES_KEY, new HashSet<>());
    }

    private void onPageLoaded(QuerySnapshot snap) {
        Set<String> dislikedIds = getDislikedIds();

        List<Place> newList = new ArrayList<>();
        for (DocumentSnapshot d : snap.getDocuments()) {
            try {
                Place p = d.toObject(Place.class);
                if (p != null) {
                    p.id = d.getId();
                    // Filter out disliked and already favorited places
                    if (!dislikedIds.contains(p.id)) {
                        newList.add(p);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse place: " + d.getId(), e);
                error.setValue("無法解析店家資料: " + d.getId());
            }
        }

        if (newList.isEmpty()) {
            emptyMessage.setValue("目前沒有可顯示的店家");
        }

        places.setValue(newList);
        isLoading.setValue(false);
    }

    private void onLoadFailed(Exception e) {
        isLoading.setValue(false);
        error.setValue("讀取失敗: " + e.getMessage());
        Log.e(TAG, "Firestore load failed", e);
    }

    /** Sanitizes a list of tags for Firestore query */
    private List<String> sanitizeTags(List<String> input) {
        if (input == null || input.isEmpty()) return Collections.emptyList();
        Set<String> set = new LinkedHashSet<>();
        for (String s : input) {
            if (s != null) {
                String t = s.trim();
                if (!t.isEmpty()) {
                    set.add(t);
                }
            }
            if (set.size() == 10) break; // whereArrayContainsAny limit
        }
        return new ArrayList<>(set);
    }
}
