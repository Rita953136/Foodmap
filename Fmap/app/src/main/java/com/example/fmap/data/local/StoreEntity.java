package com.example.fmap.data.local;

import androidx.annotation.NonNull;

/**
 * 簡化的本地實體（現在不強制 Room，先當 POJO 使用）
 * 若要改用 Room，只需加上 @Entity/@PrimaryKey 等註解即可。
 */
public class StoreEntity {
    @NonNull public String id;

    public String name;
    public String address;
    public Double rating;       // 若 JSON 沒有就留 null
    public String priceRange;   // 存文字格式
    public String tags;         // 逗號分隔
    public String menuItems;    // 逗號分隔
    public Double lat;
    public Double lng;

    // ✅ 新增：封面圖片路徑（例如 "stores/鬼匠拉麵-逢甲店/cover.jpg"）
    public String imagePath;

    // ✅ 更新建構子：加入 imagePath 參數
    public StoreEntity(@NonNull String id, String name, String address, Double rating,
                       String priceRange, String tags, String menuItems,
                       Double lat, Double lng, String imagePath) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.rating = rating;
        this.priceRange = priceRange;
        this.tags = tags;
        this.menuItems = menuItems;
        this.lat = lat;
        this.lng = lng;
        this.imagePath = imagePath;
    }

    // ✅ 若之後需要簡化建立（沒有圖片的舊版本）
    public StoreEntity(@NonNull String id, String name, String address, Double rating,
                       String priceRange, String tags, String menuItems,
                       Double lat, Double lng) {
        this(id, name, address, rating, priceRange, tags, menuItems, lat, lng, null);
    }
}
