package com.example.fmap.data.local;

import com.example.fmap.model.Place;
import com.example.fmap.model.Store;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

/** Entity / Store / Place 之間的轉換 */
public class StoreMappers {

    // Store(JSON 模型) → Entity
    public static StoreEntity toEntity(Store s) {
        if (s == null) return null;

        StringJoiner tagJoin = new StringJoiner(",");
        // ★ 改：以 category 為主要標籤來源（舊的是 s.getTags()）
        if (s.getCategory() != null) s.getCategory().forEach(tagJoin::add);

        StringJoiner menuJoin = new StringJoiner(",");
        if (s.getMenuItems() != null) s.getMenuItems().forEach(menuJoin::add);

        // ✅ 新增封面圖片路徑（若 JSON 有 images.cover）
        String coverRaw = s.getCoverPathRaw();

        return new StoreEntity(
                s.getId() != null ? s.getId() : safeSlug(s.getStoreName()),
                s.getStoreName(),
                s.getAddress(),
                null, // rating 若 JSON 有再補
                s.getPriceRange() != null ? s.getPriceRange().toString() : null,
                tagJoin.length() > 0 ? tagJoin.toString() : null,
                menuJoin.length() > 0 ? menuJoin.toString() : null,
                s.getLat(),
                s.getLng(),
                coverRaw // ✅ 封面圖片路徑
        );
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

        // ★ 同樣保持從 Entity.tags（即 category）轉出
        if (e.tags != null && !e.tags.isEmpty()) {
            p.setTagsTop3(Arrays.asList(e.tags.split(",")));
        }

        p.setLat(e.lat);
        p.setLng(e.lng);
        if (e.menuItems != null && !e.menuItems.isEmpty()) {
            p.setMenuItems(Arrays.asList(e.menuItems.split(",")));
        }

        // ✅ 加上封面圖片路徑
        if (e.imagePath != null && !e.imagePath.isEmpty()) {
            p.setCoverImage(e.imagePath);
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
