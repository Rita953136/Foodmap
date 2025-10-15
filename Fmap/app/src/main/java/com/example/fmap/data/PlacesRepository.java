package com.example.fmap.data;

import com.example.fmap.model.Place;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface PlacesRepository {
    CompletableFuture<List<Place>> list(String q, int page, int pageSize, String sort);
    CompletableFuture<Place> get(String id);
    List<Place> getRecommendations();
}
