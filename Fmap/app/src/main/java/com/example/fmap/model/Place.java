package com.example.fmap.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Place：UI 層使用的店家資料模型。
 * - 來源可為 StoreEntity（本地）或 Firestore（若未來擴充）。
 * - 不依賴 Android 元件，可安全序列化或放入 LiveData。
 */
public class Place implements Serializable {

    // ---- 基本資料 ----
    public String id;              // 唯一 ID
    private String name;           // 店名
    private String address;        // 地址
    private Double rating;         // 評分
    private Integer ratingCount;   // 評論數

    // ---- 標籤與分類 ----
    private List<String> tagsTop3; // 主要標籤 (如 ["拉麵","湯頭好","環境佳"])
    private String priceRange;     // 價位字串（例如 "$200-400"）

    // ---- 其他屬性 ----
    private Double lat;
    private Double lng;
    private String phone;
    private String coverImage;     // 主要圖片 URL
    private List<String> menuItems; // 菜單項目


    // ---- 建構子 ----
    public Place() {}

    public Place(String id, String name, String address, Double rating) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.rating = rating;
    }

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

    public String getCoverImage() { return coverImage; }
    public void setCoverImage(String coverImage) { this.coverImage = coverImage; }

    // ---- 封面圖片完整路徑（自動補上 assets 前綴）----
    public String getCoverImageFullPath() {
        if (coverImage == null || coverImage.isEmpty()) return null;
        if (!coverImage.startsWith("http") && !coverImage.startsWith("file:///")) {
            return "file:///android_asset/" + coverImage;
        }
        return coverImage;
    }
    // ---- 營業時間 ----
    private java.util.Map<String, java.util.List<com.example.fmap.model.TimeRange>> businessHours;

    public java.util.Map<String, java.util.List<com.example.fmap.model.TimeRange>> getBusinessHours() {
        return businessHours;
    }

    public void setBusinessHours(java.util.Map<String, java.util.List<com.example.fmap.model.TimeRange>> businessHours) {
        this.businessHours = businessHours;
    }
    public List<String> getMenuItems() { return menuItems; }
    public void setMenuItems(List<String> menuItems) { this.menuItems = menuItems; }

    // ---- 實用方法 ----
    public boolean hasAllTags(List<String> required) {
        if (required == null || required.isEmpty()) return true;
        if (tagsTop3 == null) return false;
        return tagsTop3.containsAll(required);
    }

    public boolean hasAnyTag(List<String> any) {
        if (any == null || any.isEmpty()) return true;
        if (tagsTop3 == null) return false;
        for (String t : any) if (tagsTop3.contains(t)) return true;
        return false;
    }

    public List<String> safeTags() {
        return tagsTop3 != null ? new ArrayList<>(tagsTop3) : new ArrayList<>();
    }

    @Override
    public String toString() {
        return "Place{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", rating=" + rating +
                ", tags=" + tagsTop3 +
                '}';
    }

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
