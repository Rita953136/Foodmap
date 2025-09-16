package com.example.fmap.model;

import java.util.List;

public class Place {
    private final String id;
    private final String name;
    private final double lat;   // 先保留欄位，未來加地圖可直接用
    private final double lng;
    private final Double rating;
    private final Integer distanceMeters;
    private final List<String> tags;
    private final Integer priceLevel; // 1~4
    private final String thumbnailUrl;

    public Place(String id, String name, double lat, double lng, Double rating, Integer distanceMeters,
                 List<String> tags, Integer priceLevel, String thumbnailUrl) {
        this.id = id; this.name = name; this.lat = lat; this.lng = lng;
        this.rating = rating; this.distanceMeters = distanceMeters; this.tags = tags;
        this.priceLevel = priceLevel; this.thumbnailUrl = thumbnailUrl;
    }
    public String getId() { return id; }
    public String getName() { return name; }
    public double getLat() { return lat; }
    public double getLng() { return lng; }
    public Double getRating() { return rating; }
    public Integer getDistanceMeters() { return distanceMeters; }
    public List<String> getTags() { return tags; }
    public Integer getPriceLevel() { return priceLevel; }
    public String getThumbnailUrl() { return thumbnailUrl; }
}