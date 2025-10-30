package com.example.fmap.data.local;

import com.example.fmap.model.Place;
import com.example.fmap.model.Store;
import com.example.fmap.model.TimeRange;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 負責在 Store (網路模型)、StoreEntity (資料庫模型)、Place (UI 模型) 之間互相轉換。
 */
public class StoreMappers {
    private static final Gson gson = new Gson();

    /**
     * Store (網路模型) → StoreEntity (資料庫模型)
     * 把從網路下載的資料，整理成可以存進資料庫的格式。
     */
    public static StoreEntity toEntity(Store s) {
        if (s == null) return null;

        StoreEntity entity = new StoreEntity();

        // --- 一對一的欄位複製 ---
        entity.id = s.getId() != null ? s.getId() : safeSlug(s.getStoreName());
        entity.storeName = s.getStoreName();
        entity.address = s.getAddress();
        entity.lat = s.getLat() != null ? s.getLat() : 0.0;
        entity.lng = s.getLng() != null ? s.getLng() : 0.0;
        entity.rating = s.getRating();
        entity.phone = s.getPhone();
        entity.phoneDisplay = s.getPhoneDisplay();
        entity.category = s.getCategory();
        entity.tags = s.getTags();
        entity.menuItems = s.getMenuItems();

        // 處理比較特別的 "images" 欄位，只取出封面圖 "cover" 的網址。
        if (s.getImages() != null && s.getImages().isJsonObject()) {
            com.google.gson.JsonObject imagesObj = s.getImages().getAsJsonObject();
            if (imagesObj.has("cover")) {
                entity.imageUrl = imagesObj.get("cover").getAsString();
            }
        }

        // 因為資料庫看不懂複雜物件，所以先把價位和營業時間轉換成通用的 Map<String, Object> 格式。
        // 後續會由 Converters.java 接手把它們轉成文字存入。
        entity.priceRange = convertObjectToMap(s.getPriceRange());
        entity.businessHours = convertObjectToMap(s.getBusinessHours());

        return entity;
    }

    /**
     * StoreEntity (資料庫模型) → Place (UI 模型)
     * 把從資料庫讀出來的資料，整理成 App 畫面可以直接顯示的格式。
     */
    public static Place toPlace(StoreEntity e) {
        if (e == null) return null;

        Place p = new Place();

        // --- 一對一的欄位複製 ---
        p.id = e.id;
        p.setName(e.storeName);
        p.setAddress(e.address);
        p.setLat(e.lat);
        p.setLng(e.lng);
        p.setRating(e.rating);
        p.setPhone(e.phone);
        p.setTagsTop3(e.category); // 把分類當作標籤來用
        p.setMenuItems(e.menuItems);
        p.setCoverImage(e.imageUrl);
        p.setPhoneDisplay(e.phoneDisplay);

        // 把 Map 格式的營業時間，轉成 UI 需要的特定格式。
        p.setBusinessHours(formatBusinessHours(e.businessHours));
        // 把 Map 格式的價位，組合成 "$200–$400" 這樣的文字。
        p.setPriceRange(buildPriceTextFromMap(e.priceRange));

        return p;
    }

    /**
     * 輔助方法：將一整個 StoreEntity 清單，轉換成 Place 清單。
     */
    public static List<Place> toPlaceList(List<StoreEntity> list) {
        List<Place> result = new ArrayList<>();
        if (list == null) return result;
        // 迴圈，對清單中的每一個項目都執行 toPlace() 轉換。
        for (StoreEntity e : list) {
            Place p = toPlace(e);
            if (p != null) result.add(p);
        }
        return result;
    }

    // --- 工廠內部使用的小工具 ---

    /**
     * 工具：利用 Gson 當橋樑，把任何物件轉成通用的 Map<String, Object> 格式。
     */
    private static Map<String, Object> convertObjectToMap(Object obj) {
        if (obj == null) return null;
        String json = gson.toJson(obj);
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        return gson.fromJson(json, type);
    }

    /**
     * 工具：把 Map 格式的價位 ({"min": 200, "max": 400})，組合成 "$200–$400" 這樣的文字。
     */
    private static String buildPriceTextFromMap(Map<String, Object> priceMap) {
        if (priceMap == null || priceMap.isEmpty()) return null;
        // 安全地取出 min 和 max 的值
        Object minObj = priceMap.get("min");
        Object maxObj = priceMap.get("max");
        Integer min = (minObj instanceof Number) ? ((Number) minObj).intValue() : null;
        Integer max = (maxObj instanceof Number) ? ((Number) maxObj).intValue() : null;

        // 根據 min 和 max 的有無，組合出不同的文字
        if (min == null && max == null) return null;
        if (min != null && Objects.equals(min, max)) return "$" + min;
        if (min != null && max != null) return "$" + min + "–" + max;
        if (min != null) return "≥$" + min;
        return "≤$" + max;
    }

    /**
     * 工具：把 Map 格式的營業時間，還原成 UI 層需要的 Map<String, List<TimeRange>> 格式。
     */
    private static Map<String, List<TimeRange>> formatBusinessHours(Map<String, Object> rawHours) {
        if (rawHours == null) return null;
        // 同樣利用 Gson 當橋樑，進行精確的型別轉換。
        String json = gson.toJson(rawHours);
        Type type = new TypeToken<Map<String, List<TimeRange>>>(){}.getType();
        return gson.fromJson(json, type);
    }

    /**
     * 工具：如果店家沒有 ID，就用店名產生一個安全的、可用於檔案路徑的 ID。
     * 例如："Hello World" -> "hello-world"
     */
    private static String safeSlug(String name) {
        if (name == null || name.isEmpty()) {
            // 如果連店名都沒有，就用目前時間當 ID，確保獨一無二。
            return "store_" + System.currentTimeMillis();
        }
        return name.replace(' ', '-').toLowerCase();
    }
}
