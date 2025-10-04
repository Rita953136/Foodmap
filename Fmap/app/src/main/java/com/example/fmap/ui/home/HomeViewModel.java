package com.example.fmap.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.fmap.model.Place;

import java.util.Collections;
import java.util.List;

public class HomeViewModel extends ViewModel {

    private final MutableLiveData<List<Place>> places = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> exhausted = new MutableLiveData<>(false);

    public LiveData<List<Place>> getPlaces() { return places; }
    public LiveData<Boolean> getExhausted() { return exhausted; }

    public void loadRecommendations() { /* no-op: 改由 HomeFragment 直接讀 Firestore */ }
    public void devResetQuotaAndReload() { /* no-op */ }
}
