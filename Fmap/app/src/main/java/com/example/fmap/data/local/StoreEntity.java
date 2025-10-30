package com.example.fmap.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.List;
import java.util.Map;

/**
 * @Entity(tableName = "stores"): 告訴 Room，這是一個資料庫的實體。
 *                            並指定它對應的資料庫表格名稱為 "stores"。
 */
@Entity(tableName = "stores")
public class StoreEntity {

    /**
     * @PrimaryKey: 指定 'id' 這個欄位是「主鍵」，也就是每家店獨一無二的識別碼。
     * @NonNull:    表示這個欄位不能是空的 (null)。
     */
    @PrimaryKey
    @NonNull
    public String id = "";

    /**
     * @ColumnInfo(name = "store_name"): 把這個 'storeName' 變數，
     *                                 對應到資料庫表格中一個叫做 "store_name" 的欄位。
     */
    @ColumnInfo(name = "store_name")
    public String storeName;

    // 店家的其他基本資料欄位
    public Double rating;       // 評分 (數字)
    public String address;      // 地址 (文字)
    public String phone;        // 電話 (文字)
    public double lat;          // 緯度 (數字)
    public double lng;          // 經度 (數字)

    @ColumnInfo(name = "image_url")
    public String imageUrl;     // 圖片網址

    @ColumnInfo(name = "phone_display")
    public String phoneDisplay; // 顯示用的電話號碼格式

    // List<String> 翻譯成純文字。
    public List<String> category;  // 分類 (例: ["日式", "拉麵"])
    public List<String> tags;      // 標籤 (例: ["寵物友善", "可外帶"])
    public List<String> menuItems; // 菜單項目

    // Map 翻譯成純文字。
    @ColumnInfo(name = "price_range")
    public Map<String, Object> priceRange; // 價位區間

    @ColumnInfo(name = "business_hours")
    public Map<String, Object> businessHours; // 營業時間

    /**
     * Room 資料庫規定，每個 Entity 都必須有一個「沒有參數的建構子」。
     * 這樣 Room 才能在從資料庫讀取資料並建立物件時，正確地初始化它。
     */
    public StoreEntity() {}
}
