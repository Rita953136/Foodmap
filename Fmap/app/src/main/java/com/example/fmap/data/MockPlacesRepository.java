package com.example.fmap.data;

import android.content.res.AssetManager;

import com.example.fmap.data.json.PlaceJson;
import com.example.fmap.model.Place;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class MockPlacesRepository implements PlacesRepository {

    private final List<Place> db;

    public MockPlacesRepository(AssetManager assets) {
        this.db = loadFromAssets(assets);
    }

    private List<Place> loadFromAssets(AssetManager assets) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(assets.open("sampledata/places.json"), StandardCharsets.UTF_8))) {

            Type listType = new TypeToken<List<PlaceJson>>(){}.getType();
            List<PlaceJson> raw = new Gson().fromJson(br, listType);
            if (raw == null) return Collections.emptyList();

            List<Place> out = new ArrayList<>();
            for (PlaceJson r : raw) {
                out.add(new Place(
                        r.id, r.name, r.lat, r.lng, r.rating, r.distanceMeters,
                        r.tags == null ? Collections.emptyList() : r.tags,
                        r.priceLevel, r.thumbnailUrl
                ));
            }
            return out;

        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    @Override
    public CompletableFuture<List<Place>> list(String q, int page, int pageSize, String sort) {
        return CompletableFuture.supplyAsync(() -> {
            List<Place> data = db;

            if (q != null && !q.isEmpty()) {
                String ql = q.toLowerCase(Locale.ROOT);
                data = data.stream().filter(p ->
                        p.getName().toLowerCase(Locale.ROOT).contains(ql) ||
                                p.getTags().stream().anyMatch(t -> t.toLowerCase(Locale.ROOT).contains(ql))
                ).collect(Collectors.toList());
            }

            if ("rating_desc".equals(sort)) {
                data = data.stream()
                        .sorted((a, b) -> {
                            double ra = a.getRating() == null ? -1 : a.getRating();
                            double rb = b.getRating() == null ? -1 : b.getRating();
                            return Double.compare(rb, ra);
                        })
                        .collect(Collectors.toList());
            } else if ("distance_asc".equals(sort)) {
                data = data.stream()
                        .sorted(Comparator.comparingInt(p ->
                                p.getDistanceMeters() == null ? Integer.MAX_VALUE : p.getDistanceMeters()))
                        .collect(Collectors.toList());
            }

            int start = Math.max((page - 1) * pageSize, 0);
            int end = Math.min(start + pageSize, data.size());
            return start >= end ? Collections.emptyList() : data.subList(start, end);
        });
    }

    @Override
    public List<Place> getRecommendations() {
        return db.subList(0, Math.min(50, db.size()));
    }

    @Override
    public CompletableFuture<Place> get(String id) {
        return CompletableFuture.supplyAsync(() ->
                db.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null));
    }
}
