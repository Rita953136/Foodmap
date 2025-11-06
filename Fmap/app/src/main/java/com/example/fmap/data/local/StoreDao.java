package com.example.fmap.data.local;

import androidx.room.Dao;import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Data Access Object
 * @Dao 標籤：資料庫管家介面
 */
@Dao
public interface StoreDao {

    /**
     * 新增一整批店家資料
     * @Insert 標籤： 新增，
     *
     * onConflict = OnConflictStrategy.REPLACE：如果店家已經存在 (ID 相同)，就用新的資料蓋掉舊的。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<StoreEntity> stores);

    /**
     * 計算資料庫裡總共有幾家店。
     * @Query 標籤：查詢
     */
    @Query("SELECT COUNT(*) FROM stores")
    int count();

    /**
     * 這個方法會直接執行搜尋，然後立刻回傳一個 List<StoreEntity> 結果。
     * 適合用在不需要即時更新畫面的地方。
     *
     * @param keyword  搜尋關鍵字 (店名或地址)
     * @param category 分類關鍵字 (日式、火鍋...)
     * @param catCount 分類數量 (如果為 0，就忽略分類條件)
     * @param dishLike 喜歡的菜色關鍵字
     * @param priceEq  價位關鍵字
     * @return 符合所有條件的店家清單。
     */
    @Query("SELECT * FROM stores WHERE " +
            "(:keyword = '' OR store_name LIKE '%' || :keyword || '%' OR address LIKE '%' || :keyword || '%') AND " +
            "(:catCount = 0 OR category LIKE '%' || :category || '%') AND " +
            "(:dishLike = '' OR menuItems LIKE '%' || :dishLike || '%') AND " +
            "(:priceEq = '' OR price_range LIKE '%' || :priceEq || '%')")
    List<StoreEntity> searchAdvancedBlocking(String keyword, String category, int catCount, String dishLike, String priceEq);

    /**
     * 用 ID 列表查詢多家店。
     * 你給它一個 ID 清單 (例如 ["id1", "id2"])，它會回傳這些店家的完整資料。
     */
    @Query("SELECT * FROM stores WHERE id IN (:ids)")
    List<StoreEntity> getByIdsBlocking(List<String> ids);

    // 模糊搜尋（名稱 / 地址 / 分類字串），分類參數可留空
    @Query("SELECT * FROM stores WHERE store_name LIKE :pattern OR address LIKE :pattern")
    List<StoreEntity> searchFuzzy(String pattern);

    @Query("SELECT * FROM stores WHERE store_name LIKE :pattern OR address LIKE :pattern")
    List<StoreEntity> searchByNameOrAddressFuzzy(String pattern);

    @Query("SELECT * FROM stores")
    List<StoreEntity> getAll();
}
