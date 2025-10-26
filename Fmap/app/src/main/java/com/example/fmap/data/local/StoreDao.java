package com.example.fmap.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.fmap.data.local.StoreEntity;

import java.util.List;

/**
 * Data Access Object (DAO) for the stores table.
 * 定義所有與 "stores" 表格互動的方法。
 */
@Dao // 告訴 Room 這是個 DAO 介面
public interface StoreDao {

    /**
     * 插入多家店。如果店家已存在（根據主鍵 id），就取代它們。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<StoreEntity> stores);

    /**
     * 計算資料庫中有多少筆資料。
     */
    @Query("SELECT COUNT(*) FROM stores")
    int count();

    /**
     *【非 LiveData 版本】進階搜尋店家。
     * 這個方法會直接回傳一個 List，用來取代 observeForever 的用法。
     */
    @Query("SELECT * FROM stores WHERE " +
            "(:keyword = '' OR store_name LIKE '%' || :keyword || '%' OR address LIKE '%' || :keyword || '%') AND " + // "name" -> "store_name"
            "(:catCount = 0 OR category LIKE '%' || :category || '%') AND " + //  修正 category 的比對方式
            "(:dishLike = '' OR menuItems LIKE '%' || :dishLike || '%') AND " + // "dishLike" 是參數名, "menuItems" 是欄位名, 這部分正確
            "(:priceEq = '' OR price_range LIKE '%' || :priceEq || '%')") // "price_level" -> "price_range"
    List<StoreEntity> searchAdvancedBlocking(String keyword, String category, int catCount, String dishLike, String priceEq);

    /**
     *【非 LiveData 版本】根據 ID 列表，查詢多家店的資料。
     */
    @Query("SELECT * FROM stores WHERE id IN (:ids)")
    List<StoreEntity> getByIdsBlocking(List<String> ids);
}