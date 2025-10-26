
package com.example.fmap.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map; // 確保 import 了 Map
import java.util.Objects;

public class Place implements Serializable {

    // ---- 基本資料 ----
    public String id;
    private String name;
    private String address;
    private Double rating;
    private Integer ratingCount;

    // ---- 標籤與分類 ----
    private List<String> tagsTop3;
    private String priceRange;

    // ---- 其他屬性 ----
    private Double lat;
    private Double lng;
    private String phone;
    private String phoneDisplay;
    private String coverImage;
    private List<String> menuItems;

    // ---- 營業時間 ----
    private Map<String, List<TimeRange>> businessHours;

    // ---- 建構子 ----
    public Place() {}

    // ... 其他建構子 ...

    // ---- Getter / Setter ----
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }

    public Integer getRatingCount() { return ratingCount; }
    public void setRatingCount(Integer ratingCount) { this.ratingCount = ratingCount; }

    public List<String> getTagsTop3() { return tagsTop3; }
    public void setTagsTop3(List<String> tagsTop3) { this.tagsTop3 = tagsTop3; }

    public String getPriceRange() { return priceRange; }
    public void setPriceRange(String priceRange) { this.priceRange = priceRange; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    // ✨ 2. 為 phoneDisplay 新增 getter 和 setter
    public String getPhoneDisplay() { return phoneDisplay; }
    public void setPhoneDisplay(String phoneDisplay) { this.phoneDisplay = phoneDisplay; }

    public String getCoverImage() { return coverImage; }
    public void setCoverImage(String coverImage) { this.coverImage = coverImage; }

    public String getCoverImageFullPath() {
        if (coverImage == null || coverImage.isEmpty()) return null;
        if (!coverImage.startsWith("http") && !coverImage.startsWith("file:///")) {
            return "file:///android_asset/" + coverImage;
        }
        return coverImage;
    }

    public Map<String, List<TimeRange>> getBusinessHours() {
        return businessHours;
    }

    public void setBusinessHours(Map<String, List<TimeRange>> businessHours) {
        this.businessHours = businessHours;
    }

    public List<String> getMenuItems() { return menuItems; }
    public void setMenuItems(List<String> menuItems) { this.menuItems = menuItems; }

    // ... 其他實用方法 ...

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Place)) return false;
        Place place = (Place) o;
        return Objects.equals(id, place.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}