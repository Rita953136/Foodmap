package com.example.fmap.model;

import android.content.Context;
import android.content.SharedPreferences; // 引入 Android 內建的「輕量級儲存」工具

import androidx.annotation.Nullable;

import com.google.gson.Gson; // 引入 JSON 翻譯工具
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 「收藏功能的總管家」(使用 SharedPreferences 儲存)。
 * 採用單例模式 (Singleton)，確保整個 App 的收藏資料都是同一份。
 */
public class FavoritesStore {

    // 定義儲存檔案的名稱和收藏資料的「鑰匙」(Key)
    private static final String PREFS_NAME = "FmapUserPrefs";
    private static final String KEY_FAVORITES = "favorites_places";

    // 用來存放唯一的總管家實例
    private static FavoritesStore INSTANCE;

    // --- 成員變數 ---
    private final SharedPreferences prefs; // 輕量級儲存工具的實例
    private final Gson gson = new Gson();  // JSON 翻譯工具
    // 預先告訴 Gson，我們要處理的類型是「Place 物件的清單」
    private final Type listType = new TypeToken<List<Place>>(){}.getType();

    /**
     * 建構子是 private，代表不允許外面直接 new 一個新的，
     * 只能透過 getInstance() 取得。
     */
    private FavoritesStore(Context appCtx) {
        // 初始化 SharedPreferences，讓它可以讀寫 "FmapUserPrefs" 這個檔案
        this.prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 取得「收藏總管家」唯一實例的官方入口。
     */
    public static synchronized FavoritesStore getInstance(Context ctx) {
        // 如果還沒有實例，就建立一個新的。
        if (INSTANCE == null) {
            // 使用 ApplicationContext 避免 Memory Leak
            INSTANCE = new FavoritesStore(ctx.getApplicationContext());
        }
        // 回傳唯一的實例
        return INSTANCE;
    }

    /**
     * 取得所有收藏店家。
     * synchronized 關鍵字確保多個執行緒同時讀取時資料是安全的。
     */
    public synchronized List<Place> getAll() {
        // 從檔案中讀取之前存的 JSON 文字，如果沒有就給一個 "[]" (空清單的文字)。
        String json = prefs.getString(KEY_FAVORITES, "[]");
        // 把 JSON 文字翻譯回 Place 的清單物件。
        List<Place> list = gson.fromJson(json, listType);
        // 安全檢查：如果翻譯失敗變成 null，就回傳一個新的空清單。
        return list != null ? list : new ArrayList<>();
    }

    /**
     * 新增或更新一筆收藏 (用 ID 來判斷是新增還是更新)。
     */
    public synchronized void add(@Nullable Place place) {
        if (place == null || place.id == null) return; // 保護：無效資料就直接跳過

        List<Place> list = new ArrayList<>(getAll()); // 取得目前所有的收藏
        int idx = indexOfId(list, place.id);          // 檢查這家店是否已經在清單裡

        if (idx >= 0) {
            list.set(idx, place);   // 如果已經存在，就用新的資料覆蓋掉舊的 (更新)
        } else {
            list.add(place);        // 如果不存在，就加到清單尾巴 (新增)
        }
        save(list); // 把更新後的清單存回手機
    }

    /**
     * 透過店家 ID 來移除一筆收藏。
     */
    public synchronized void removeById(String id) {
        if (id == null) return; // 保護

        List<Place> list = new ArrayList<>(getAll()); // 取得目前清單
        int idx = indexOfId(list, id);                // 找到要移除的店家位置

        if (idx >= 0) {
            list.remove(idx); // 從清單中移除
            save(list);       // 存檔
        }
    }

    /**
     * removeById 的另一個別名，功能完全相同。
     */
    public synchronized void remove(String id) {
        removeById(id);
    }


    /**
     * 檢查某個 ID 的店家是否已經被收藏。
     */
    public synchronized boolean contains(String id) {
        return indexOfId(getAll(), id) >= 0; // 只要找得到位置(>=0)，就代表已收藏
    }

    // ---------------- 內部使用的工具方法 ----------------

    /**
     * 內部專用：將店家清單翻譯成 JSON 文字並存入 SharedPreferences。
     */
    private void save(List<Place> list) {
        prefs.edit() // 開始編輯
                .putString(KEY_FAVORITES, gson.toJson(list, listType)) // 寫入資料
                .apply(); // 在背景執行存檔
    }

    /**
     * 內部專用：在店家清單中，尋找特定 ID 的店家位於第幾個位置。
     * @return 找到就回傳位置 (0, 1, 2...)，找不到就回傳 -1。
     */
    private static int indexOfId(List<Place> list, String id) {
        if (list == null || id == null) return -1;
        // 迴圈，一個一個比對
        for (int i = 0; i < list.size(); i++) {
            Place p = list.get(i);
            if (p != null && id.equals(p.id)) {
                return i; // 找到了，回傳當前位置
            }
        }
        return -1; // 全部比對完都沒找到
    }
}
