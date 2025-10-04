package com.example.fmap.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Place {

    public Place() {}

    // 基本資料（Firestore 來）
    public String id;          // Firestore docId
    public String name;        // 商家名稱
    public Double  rating;      // 評分
    public String photoUrl;    // 圖片 URL（縮圖）
    public String introLine;   // 例如 "#西式 #麵食 #清淡"

    // 供 FavoritesStore 取用的額外欄位（目前可能沒有實值，給預設即可）
    public Double lat;         // 緯度
    public Double lng;         // 經度
    public Integer priceLevel; // 價位等級（$=1 ~ $$$$=4）
    public Integer distanceMeters; // 距離（公尺）

    // 標籤清單（用於側邊篩選）
    public List<String> tags = new ArrayList<>();

    // ---------- getters：讓 FavoritesStore 編譯通過 ----------
    public String getId() { return id; }
    public String getName() { return name; }
    public Double  getRating() { return rating != null ? rating : 0f; }
    public String getThumbnailUrl() { return photoUrl; }
    public Double getLat() { return lat != null ? lat : 0d; }
    public Double getLng() { return lng != null ? lng : 0d; }
    public Integer getPriceLevel() { return priceLevel != null ? priceLevel : 0; }
    public Integer getDistanceMeters() { return distanceMeters != null ? distanceMeters : 0; }

    public List<String> getTags() {
        return tags != null ? tags : Collections.emptyList();
    }

    // ✅ 如果其他地方需要一行文字（#西式 #麵食 #清淡），用這個
    public String getTagsText() {
        if (introLine != null && !introLine.isEmpty()) return introLine;
        if (tags == null || tags.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String t : tags) {
            if (t == null || t.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append('#').append(t.trim());
        }
        return sb.toString();
    }
}
