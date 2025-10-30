package com.example.fmap.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 準備顯示在 App 畫面的「店家模型」(UI Model)。
 * implements Serializable: 讓這個物件可以被序列化，方便在不同 Activity 或 Fragment 之間傳遞。
 */
public class Place implements Serializable {

    // --- 基本資料，會對應到 UI 上的各個 TextView 或 RatingBar ---
    public String id;          // 唯一ID
    private String name;       // 店名
    private String address;    // 地址
    private Double rating;     // 評分 (例如 4.6)

    // --- 標籤與價位 ---
    private List<String> tagsTop3; // 顯示用的標籤
    private String priceRange;     // 價位區間的文字 (例如 "$200–$400")

    // --- 地圖與聯絡資訊 ---
    private Double lat;            // 緯度
    private Double lng;            // 經度
    private String phone;          // 原始電話號碼 (可能用於撥號)
    private String phoneDisplay;   // 顯示用的電話號碼 (格式化過的)
    private String coverImage;     // 封面圖網址或路徑
    private List<String> menuItems;// 推薦菜單

    // --- 營業時間 (結構較複雜) ---
    private Map<String, List<TimeRange>> businessHours;

    // --- 建構子 (Constructor) ---
    // Room 或 Gson 等工具需要一個空的建構子來建立物件
    public Place() {}

    // --- Getter 和 Setter 方法 ---
    // 這些公開的方法，讓其他程式碼可以安全地存取或修改這個物件的私有(private)屬性。

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }

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

    public String getPhoneDisplay() { return phoneDisplay; }
    public void setPhoneDisplay(String phoneDisplay) { this.phoneDisplay = phoneDisplay; }

    public String getCoverImage() { return coverImage; }
    public void setCoverImage(String coverImage) { this.coverImage = coverImage; }

    /**
     * 取得圖片的完整路徑。
     * 如果圖片是內建的，會自動加上 "file:///android_asset/" 前綴。
     */
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


    // --- 物件比對相關方法 ---

    /**
     * 覆寫 equals 方法，用來判斷兩個 Place 物件是否代表「同一家店」。
     * 這裡的判斷標準是：只要 id 相同，就視為同一家店。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Place)) return false;
        Place place = (Place) o;
        return Objects.equals(id, place.id);
    }

    /**
     * 配合 equals，覆寫 hashCode 方法。
     * 讓具有相同 id 的 Place 物件有相同的雜湊碼。
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
