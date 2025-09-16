package com.example.fmap.data;

import com.example.fmap.model.Place;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface PlacesRepository {
    // 你原本的方法（若有）
    CompletableFuture<List<Place>> list(String q, int page, int pageSize, String sort);
    CompletableFuture<Place> get(String id);

    // 新增：推薦清單（同步取，方便現有程式碼呼叫）
    List<Place> getRecommendations();
}
