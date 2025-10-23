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
    public String phone;               // 電話
    public String coverImage;          // 封面圖相對路徑或 URL（e.g. stores/.../cover.jpg 或 https://...）
    public String businessHoursJson;   // 營業時間 Map 的 JSON 字串
    public String imagePath;
    public StoreEntity(@NonNull String id, String name, String address, Double rating,
                       String priceRange, String tags, String menuItems,
                       Double lat, Double lng,
                       String phone, String coverImage, String businessHoursJson) {
        this.id = id; this.name = name; this.address = address; this.rating = rating;
        this.priceRange = priceRange; this.tags = tags; this.menuItems = menuItems;
        this.lat = lat; this.lng = lng;
        this.phone = phone; this.coverImage = coverImage; this.businessHoursJson = businessHoursJson;
    }
}
