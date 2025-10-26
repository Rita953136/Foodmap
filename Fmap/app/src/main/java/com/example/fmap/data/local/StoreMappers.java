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
 * Entity / Store / Place 之間的轉換 (已更新至 Room TypeConverter 版本)
 */
public class StoreMappers {

    private static final Gson gson = new Gson();

    // Store (JSON 模型) → StoreEntity (資料庫模型)
    public static StoreEntity toEntity(Store s) {
        if (s == null) return null;

        StoreEntity entity = new StoreEntity();

        entity.id = s.getId() != null ? s.getId() : safeSlug(s.getStoreName());
        entity.storeName = s.getStoreName();
        entity.address = s.getAddress();
        entity.lat = s.getLat() != null ? s.getLat() : 0.0;
        entity.lng = s.getLng() != null ? s.getLng() : 0.0;
        entity.rating = readRatingSafely(s);
        entity.phone = s.getPhone();
        entity.phoneDisplay = s.getPhoneDisplay();
        entity.category = s.getCategory();
        entity.tags = s.getTags();
        entity.menuItems = s.getMenuItems();

        if (s.getImages() != null && s.getImages().isJsonObject()) {
            // 1. 先將通用的 JsonElement 轉換為更具體的 JsonObject
            com.google.gson.JsonObject imagesObj = s.getImages().getAsJsonObject();

            // 2. 檢查這個 JsonObject 中是否存在 "cover" 這個鍵
            if (imagesObj.has("cover")) {
                // 3. 取出 "cover" 的值，並轉換成字串
                entity.imageUrl = imagesObj.get("cover").getAsString();
            }
        }

        // 使用 Gson 作為中間人，將任何物件轉換成 Map<String, Object>
        entity.priceRange = convertObjectToMap(s.getPriceRange());
        entity.businessHours = convertObjectToMap(s.getBusinessHours());

        return entity;
    }

    // StoreEntity (資料庫模型) → Place (UI 顯示模型)
    public static Place toPlace(StoreEntity e) {
        if (e == null) return null;

        Place p = new Place();
        p.id = e.id;
        p.setName(e.storeName);
        p.setAddress(e.address);
        p.setLat(e.lat);
        p.setLng(e.lng);
        p.setRating(e.rating);
        p.setPhone(e.phone);
        p.setTagsTop3(e.category);
        p.setMenuItems(e.menuItems);
        p.setBusinessHours(formatBusinessHours(e.businessHours));
        p.setPriceRange(buildPriceTextFromMap(e.priceRange));
        p.setCoverImage(e.imageUrl);
        p.setPhoneDisplay(e.phoneDisplay);
        return p;
    }

    public static List<Place> toPlaceList(List<StoreEntity> list) {
        List<Place> result = new ArrayList<>();
        if (list == null) return result;
        for (StoreEntity e : list) {
            Place p = toPlace(e);
            if (p != null) result.add(p);
        }
        return result;
    }

    /**
     * 使用 Gson 作為橋樑，將任意物件轉換為 Map<String, Object>。
     */
    private static Map<String, Object> convertObjectToMap(Object obj) {
        if (obj == null) return null;
        String json = gson.toJson(obj);
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        return gson.fromJson(json, type);
    }

    private static double readRatingSafely(Store s) {
        try {
            java.lang.reflect.Method m = s.getClass().getMethod("getRating");
            Object v = m.invoke(s);
            return (v instanceof Number) ? ((Number) v).doubleValue() : 0.0;
        } catch (Exception ignore) {
            return 0.0;
        }
    }

    private static String buildPriceTextFromMap(Map<String, Object> priceMap) {
        if (priceMap == null || priceMap.isEmpty()) return null;
        Object minObj = priceMap.get("min");
        Object maxObj = priceMap.get("max");
        Integer min = (minObj instanceof Number) ? ((Number) minObj).intValue() : null;
        Integer max = (maxObj instanceof Number) ? ((Number) maxObj).intValue() : null;
        if (min == null && max == null) return null;
        if (min != null && Objects.equals(min, max)) return "$" + min;
        if (min != null && max != null) return "$" + min + "–" + max;
        if (min != null) return "≥$" + min;
        return "≤$" + max;
    }

    private static Map<String, List<TimeRange>> formatBusinessHours(Map<String, Object> rawHours) {
        if (rawHours == null) return null;
        String json = gson.toJson(rawHours);
        // ✨【同時修正這裡】直接使用正確的 TypeToken
        Type type = new TypeToken<Map<String, List<TimeRange>>>(){}.getType();
        return gson.fromJson(json, type);
    }

    private static String safeSlug(String name) {
        if (name == null || name.isEmpty()) {
            return "store_" + System.currentTimeMillis();
        }
        return name.replace(' ', '-').toLowerCase();
    }
}
