package com.example.fmap.ui.home;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fmap.data.FavoritesStore;
import com.example.fmap.model.FavItem;

import java.util.ArrayList;
import java.util.List;

public class FavoritesViewModel extends AndroidViewModel {
    private final FavoritesStore store;
    private final MutableLiveData<List<FavItem>> favorites = new MutableLiveData<>(new ArrayList<>());

    public FavoritesViewModel(@NonNull Application app) {
        super(app);
        store = new FavoritesStore(app.getApplicationContext());
    }

    public LiveData<List<FavItem>> getFavorites() { return favorites; }

    public void load() { favorites.setValue(store.getAll()); }

    public void removeById(String id) { store.remove(id); load(); }
}
