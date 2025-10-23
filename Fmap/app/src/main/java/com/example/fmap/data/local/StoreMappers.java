package com.example.fmap.data.local;

import com.example.fmap.model.Place;
import com.example.fmap.model.Store;
import com.example.fmap.model.TimeRange;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/** Entity / Store / Place 之間的轉換 */
public class StoreMappers {

    // 嘗試從 Store 讀 rating（若沒有此欄位/方法就回 null）
    private static Double readRatingSafely(Store s) {
        try {
            java.lang.reflect.Method m = s.getClass().getMethod("getRating");
            Object v = m.invoke(s);
            return (v instanceof Number) ? ((Number) v).doubleValue() : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    private static final Gson GSON = new Gson();
    private static final Type HOURS_TYPE =
            new TypeToken<Map<String, List<TimeRange>>>(){}.getType();

    // Store(JSON 模型) → Entity
    public static StoreEntity toEntity(Store s) {
        if (s == null) return null;

        // 標籤：以 category 為主，不足 3 個用 tags 補
        StringJoiner tagJoin = new StringJoiner(",");
        if (s.getCategory() != null) s.getCategory().forEach(tagJoin::add);
        if (tagJoin.length() < 3 && s.getTags() != null) {
            for (String t : s.getTags()) {
                if (tagJoin.length() >= 3) break;
                tagJoin.add(t);
            }
        }

        // 菜單
        StringJoiner menuJoin = new StringJoiner(",");
        if (s.getMenuItems() != null) s.getMenuItems().forEach(menuJoin::add);

        // 封面（相對路徑或 URL）
        String coverRaw = s.getCoverPathRaw();

        // 營業時間 → JSON
        String hoursJson = (s.getBusinessHours() != null)
                ? GSON.toJson(s.getBusinessHours(), HOURS_TYPE) : null;

        // 價位顯示字串（支援 PriceRange 只有欄位或有 getter 兩種）
        String priceText = null;
        if (s.getPriceRange() != null) {
            Integer min = readInt(s.getPriceRange(), "getMin", "min");
            Integer max = readInt(s.getPriceRange(), "getMax", "max");
            priceText = buildPrice(min, max);
        }

        return new StoreEntity(
                s.getId() != null ? s.getId() : safeSlug(s.getStoreName()),
                s.getStoreName(),
                s.getAddress(),
                readRatingSafely(s),              // 可能為 null
                priceText,                        // 友善顯示的價位
                tagJoin.length() > 0 ? tagJoin.toString() : null,
                menuJoin.length() > 0 ? menuJoin.toString() : null,
                s.getLat(), s.getLng(),
                s.getPhone(),                     // phone
                coverRaw,                         // coverImage（raw，不加前綴）
                hoursJson                         // businessHoursJson
        );
    }

    // 讀整數欄位：先找 getter，沒有就讀欄位
    private static Integer readInt(Object obj, String getter, String fieldName) {
        if (obj == null) return null;
        try {
            java.lang.reflect.Method m = obj.getClass().getMethod(getter);
            Object v = m.invoke(obj);
            return (v instanceof Number) ? ((Number) v).intValue() : null;
        } catch (Exception ignore) {
            try {
                java.lang.reflect.Field f = obj.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                Object v = f.get(obj);
                return (v instanceof Number) ? ((Number) v).intValue() : null;
            } catch (Exception e) {
                return null;
            }
        }
    }

    private static String buildPrice(Integer min, Integer max) {
        if (min == null && max == null) return null;
        if (min != null && max != null) return "$" + min + "–$" + max;
        if (min != null) return "≥$" + min;
        return "≤$" + max;
    }

    // Entity → Place（供 UI 使用）
    public static Place toPlace(StoreEntity e) {
        if (e == null) return null;
        Place p = new Place();
        p.id = e.id;
        p.setName(e.name);
        p.setAddress(e.address);
        p.setRating(e.rating);
        p.setPriceRange(e.priceRange);

        if (e.tags != null && !e.tags.isEmpty()) {
            p.setTagsTop3(Arrays.asList(e.tags.split(",")));
        }

        p.setLat(e.lat);
        p.setLng(e.lng);
        p.setPhone(e.phone);
        p.setCoverImage(e.coverImage);

        if (e.menuItems != null && !e.menuItems.isEmpty()) {
            p.setMenuItems(Arrays.asList(e.menuItems.split(",")));
        }

        if (e.businessHoursJson != null && !e.businessHoursJson.isEmpty()) {
            Map<String, List<TimeRange>> map = GSON.fromJson(e.businessHoursJson, HOURS_TYPE);
            p.setBusinessHours(map);
        }

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

    private static String safeSlug(String name) {
        if (name == null) return "store_" + System.currentTimeMillis();
        return name.replaceAll("\\s+", "_");
    }
}
