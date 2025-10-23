package com.example.fmap.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonElement;

/** 對應 assets/stores_info_normalized.json 的資料模型 */
public class Store {

    @SerializedName("store_name")
    private String storeName;

    // JSON: ["西式","甜點",...]
    @SerializedName("category")
    private List<String> category;

    // JSON: ["義式","披薩",...]
    @SerializedName("tags")
    private List<String> tags;

    @SerializedName("menu_items")
    private List<String> menuItems;

    @SerializedName("services")
    private List<String> services;

    // JSON: { "currency":"TWD","min":400,"max":800 }
    @SerializedName("price_range")
    private PriceRange priceRange;

    private String address;

    private String phone;

    // JSON 裡是字串（裡面長得像物件，但實際就是字串），先當成 String 存
    @SerializedName("phone_display")
    private String phoneDisplay;

    // ★ 這是重點：JSON 是 { "星期一":[{open,close},...], "星期二":[...], ... }
    @SerializedName("business_hours")
    private Map<String, List<TimeRange>> businessHours;

    // 欄位內容是多個群組的字串陣列，這裡先整包吃成 Map<String, List<String>>
    @SerializedName("extra_info")
    private Map<String, List<String>> extraInfo;

    // JSON: { "cover": "stores/.../cover.jpg", ... }
    @SerializedName("images")
    private JsonElement images;

    public JsonElement getImages() { return images; }
    public void setImages(JsonElement images) { this.images = images; }


    private String imagePath;

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }


    // 如果你的 JSON 沒提供就會是 null，沒差
    private Double lat;
    private Double lng;

    // ---- getters / setters ----
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }

    public List<String> getCategory() { return category; }
    public void setCategory(List<String> category) { this.category = category; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public List<String> getMenuItems() { return menuItems; }
    public void setMenuItems(List<String> menuItems) { this.menuItems = menuItems; }

    public List<String> getServices() { return services; }
    public void setServices(List<String> services) { this.services = services; }

    public PriceRange getPriceRange() { return priceRange; }
    public void setPriceRange(PriceRange priceRange) { this.priceRange = priceRange; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPhoneDisplay() { return phoneDisplay; }
    public void setPhoneDisplay(String phoneDisplay) { this.phoneDisplay = phoneDisplay; }

    public Map<String, List<TimeRange>> getBusinessHours() { return businessHours; }
    public void setBusinessHours(Map<String, List<TimeRange>> businessHours) { this.businessHours = businessHours; }

    public Map<String, List<String>> getExtraInfo() { return extraInfo; }
    public void setExtraInfo(Map<String, List<String>> extraInfo) { this.extraInfo = extraInfo; }
    /** 取得 JSON 原始封面路徑（優先 imagePath，其次 images.cover），例如：stores/xxx/cover.jpg 或 https://... */
    /** 取得 JSON 原始封面路徑（優先 imagePath，其次 images 內可能的 cover/generic 值） */
    public String getCoverPathRaw() {
        // 1) 若 imagePath 有值就用它
        if (imagePath != null && !imagePath.trim().isEmpty()) {
            return imagePath.trim();
        }

        // 2) 沒有 imagePath，檢查 images 欄位
        if (images == null) return null;

        try {
            // 2-1) images 是物件：可能有 cover 或 gallery
            if (images.isJsonObject()) {
                var obj = images.getAsJsonObject();

                // cover
                if (obj.has("cover") && !obj.get("cover").isJsonNull()) {
                    String s = obj.get("cover").getAsString().trim();
                    if (!s.isEmpty()) return s;
                }
                // gallery: 取第一張
                if (obj.has("gallery") && obj.get("gallery").isJsonArray()) {
                    var arr = obj.get("gallery").getAsJsonArray();
                    if (arr.size() > 0 && arr.get(0).isJsonPrimitive()) {
                        String s = arr.get(0).getAsString().trim();
                        if (!s.isEmpty()) return s;
                    }
                }
            }

            // 2-2) images 是陣列：可能直接是 ["stores/.../cover.jpg", ...]
            if (images.isJsonArray()) {
                var arr = images.getAsJsonArray();
                for (var el : arr) {
                    if (el != null && el.isJsonPrimitive()) {
                        String s = el.getAsString().trim();
                        if (!s.isEmpty()) return s;
                    }
                    // 或者是陣列元素是物件 { "cover": "..." }
                    if (el != null && el.isJsonObject()) {
                        var obj = el.getAsJsonObject();
                        if (obj.has("cover") && !obj.get("cover").isJsonNull()) {
                            String s = obj.get("cover").getAsString().trim();
                            if (!s.isEmpty()) return s;
                        }
                    }
                }
            }

            // 2-3) images 竟然是字串（也支援）
            if (images.isJsonPrimitive()) {
                String s = images.getAsString().trim();
                if (!s.isEmpty()) return s;
            }
        } catch (Exception ignore) {
            // 解析失敗就當沒圖
        }
        return null;
    }


    /** 取得可直接給 Glide 使用的完整路徑：
     *  - 若是 assets 相對路徑 → 自動補 "file:///android_asset/"
     *  - 若已是 http(s) 或 file:/// → 原樣回傳
     */
    public String getCoverAssetPath() {
        String raw = getCoverPathRaw();
        if (raw == null || raw.isEmpty()) return null;
        if (raw.startsWith("http") || raw.startsWith("file:///")) return raw;
        return "file:///android_asset/" + raw;
    }
    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    // 你的資料用店名當主鍵即可；若需要 id，可在載入時用店名做 slug
    public String getId() {
        // 若後續你有真的 id 欄位，再改這裡
        return storeName != null ? storeName.replaceAll("\\s+", "_") : null;
    }
}
