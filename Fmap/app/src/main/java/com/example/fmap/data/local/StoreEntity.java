// ✨✨✨ 請複製這整段程式碼，完全覆蓋你的 StoreEntity.java (最終版) ✨✨✨

package com.example.fmap.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.List;
import java.util.Map;

@Entity(tableName = "stores")
public class StoreEntity {

    @PrimaryKey
    @NonNull
    public String id = ""; // 給一個預設值避免 null

    @ColumnInfo(name = "store_name")
    public String storeName;

    // 使用轉換器儲存 List<String>
    public List<String> category;
    public List<String> tags;
    public List<String> menuItems;

    // 使用轉換器儲存複雜的 Map 物件
    @ColumnInfo(name = "price_range")
    public Map<String, Object> priceRange;
    @ColumnInfo(name = "image_url")
    public String imageUrl;
    public String address;
    public String phone;

    @ColumnInfo(name = "phone_display")
    public String phoneDisplay; // 這個本身就是字串，可以直接存

    @ColumnInfo(name = "business_hours")
    public Map<String, Object> businessHours;

    public double lat;
    public double lng;
    public double rating;

    // Room 需要一個無參數的建構子
    public StoreEntity() {}
}
