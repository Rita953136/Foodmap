package com.example.fmap.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonElement;

/**
 * 網路原始資料的模型 (Data Transfer Object, DTO)。
 * 結構完全對應 assets/stores_info_normalized.json 的 JSON 格式。
 */
public class Store {

    /**
     * @SerializedName: 告訴 Gson，JSON 裡的 "store_name" 這個 key，
     *                要對應到這個 'storeName' 變數。
     */
    @SerializedName("store_name")
    private String storeName;

    @SerializedName("rating")
    private Double rating;

    // 對應 JSON 中的字串清單，例如 ["日式", "拉麵"]
    @SerializedName("category")
    private List<String> category;

    // 對應 JSON 中的字串清單，例如 ["可外帶", "寵物友善"]
    @SerializedName("tags")
    private List<String> tags;

    // 對應 JSON 中的推薦菜單清單
    @SerializedName("menu_items")
    private List<String> menuItems;

    // 對應 JSON 中的價位物件，例如 { "min": 200, "max": 400 }
    @SerializedName("price_range")
    private PriceRange priceRange;

    private String address;

    private String phone;

    // 對應 JSON 中的 phone_display 欄位，用來顯示格式化過的電話號碼
    @SerializedName("phone_display")
    private String phoneDisplay;

    // 對應 JSON 中的營業時間物件，結構是 {"星期一": [時段], "星期二": [時段], ...}
    @SerializedName("business_hours")
    private Map<String, List<TimeRange>> businessHours;

    /**
     * @SerializedName: 對應 JSON 中的 "images" 欄位。
     * 使用 JsonElement 是因為這個欄位的格式不固定，可能是物件、陣列或字串，
     * 讓 Gson 先把它當作一個通用的「JSON 元素」來讀取，後續再手動解析。
     */
    @SerializedName("images")
    private JsonElement images;

    // 如果 JSON 沒提供經緯度，這些欄位會是 null
    private Double lat;
    private Double lng;

    // ---- Getters 和 Setters ----
    // 提供外部程式碼安全存取內部私有(private)屬性的管道。

    public String getStoreName() { return storeName; }

    public Double getRating() { return rating;  }

    public List<String> getCategory() { return category; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public List<String> getMenuItems() { return menuItems; }

    public PriceRange getPriceRange() { return priceRange; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPhoneDisplay() { return phoneDisplay; }
    public void setPhoneDisplay(String phoneDisplay) { this.phoneDisplay = phoneDisplay; }

    public Map<String, List<TimeRange>> getBusinessHours() { return businessHours; }

    public JsonElement getImages() { return images; }

    public Double getLat() { return lat; }

    public Double getLng() { return lng; }

    /**
     * 取得這家店的唯一識別碼 (ID)。
     * 這裡的策略是：直接用店名取代空白後當作 ID。
     * 如果未來 JSON 有提供真正的 id 欄位，只需修改這裡即可。
     */
    public String getId() {
        return storeName != null ? storeName.replaceAll("\\s+", "_") : null;
    }
}
