package com.example.fmap.data;

import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fmap.data.local.StoreDao;
import com.example.fmap.data.local.StoreDatabase;
import com.example.fmap.data.local.StoreEntity;
import com.example.fmap.data.local.StoreMappers;
import com.example.fmap.model.Store;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 資料的「總管家」(Repository)。
 * 負責協調 App 的資料來源 (例如：從 App 內建的 JSON 檔案讀取資料，並存入資料庫)。
 */
public class StoresRepository {

    private static final String TAG = "StoresRepository"; // Logcat 用的標籤
    // 指定要讀取的 App 內建資料檔案名稱
    private static final String ASSET_FILE = "stores_info_normalized.json";

    // 一個可以被觀察的狀態，用來通知 App「資料庫是否準備好了」。
    private final MutableLiveData<Boolean> dbReady = new MutableLiveData<>(false);
    private final Gson gson = new Gson();

    // 持有「資料庫管家」(DAO) 和「背景工作執行緒池」的引用
    private final StoreDao storeDao;
    private final ExecutorService databaseExecutor;

    /**
     * Repository 的建構子，在 App 啟動時會被呼叫。
     * @param app 整個 App 的應用程式實例。
     */
    public StoresRepository(Application app) {
        // 1. 取得資料庫的唯一實例
        StoreDatabase db = StoreDatabase.getDatabase(app);
        // 2. 從資料庫實例中，拿到「店家資料庫的管家」
        this.storeDao = db.storeDao();
        // 3. 從 StoreDatabase 取得共用的「背景工作執行緒池」
        this.databaseExecutor = StoreDatabase.databaseWriteExecutor;
    }

    /**
     * 提供一個外部可以觀察「資料庫是否準備好」的方法。
     */
    public LiveData<Boolean> getDbReady() {
        return dbReady;
    }

    /**
     * 從 App 內建的 JSON 檔案初始化資料庫。
     * 這個方法會在背景執行緒中執行，避免卡住畫面。
     */
    public void initFromAssets(Context ctx) {
        databaseExecutor.execute(() -> {
            try {
                // 檢查：如果資料庫已經有資料了，就不用再初始化。
                if (storeDao.count() > 0) {
                    Log.d(TAG, "資料庫已存在，跳過初始化。");
                    dbReady.postValue(true); // 通知外面：資料庫已就緒
                    return;
                }

                // 開始從 JSON 檔讀取資料
                Log.d(TAG, "資料庫為空，開始從 JSON 初始化...");
                List<Store> raw = readStoresFromAssets(ctx.getAssets(), ASSET_FILE);
                List<StoreEntity> entities = new ArrayList<>();
                for (Store s : raw) {
                    // 使用轉換工廠，把網路模型 (Store) 轉成資料庫模型 (StoreEntity)
                    StoreEntity e = StoreMappers.toEntity(s);
                    if (e != null) entities.add(e);
                }

                // 將整理好的資料一次性寫入資料庫
                storeDao.insertAll(entities);
                Log.d(TAG, "資料庫初始化完成，共寫入 " + entities.size() + " 筆資料。");

                dbReady.postValue(true); // 通知外面：資料庫已就緒

            } catch (Exception e) {
                Log.e(TAG, "初始化資料庫失敗", e);
                dbReady.postValue(false); // 通知外面：資料庫準備失敗
            }
        });
    }

    /**
     * 執行進階搜尋。
     * 這個方法只是個傳聲筒，直接把收到的參數，傳給 DAO 去資料庫裡執行真正的搜尋。
     * "Blocking" 意味著這個方法會等待資料庫搜尋完成才回傳結果。
     */
    public List<StoreEntity> searchAdvancedBlocking(
            String keyword,
            String dbCategory,
            int someFlag,
            String keyword2,
            String somethingElse
    ) {
        String kw = (keyword == null) ? "" : keyword.trim();
        String pattern = "%" + kw + "%"; // ⭐ 模糊搜尋
        return storeDao.searchFuzzy(pattern);
    }
    // === 新增：模糊搜尋（不改原本 searchAdvancedBlocking） ===
    public List<StoreEntity> searchFuzzyBlocking(String keyword) {
        final String kw = norm(keyword);
        final String pattern = "%" + kw + "%";

        // 1) 先用 SQL 對 name/address 做模糊查
        List<StoreEntity> base = storeDao.searchByNameOrAddressFuzzy(pattern);

        // 2) 若 keyword 很短或沒結果，你也可以把全部撈出來再做補強（可保留/可刪）
        if ((base == null || base.isEmpty()) && kw.isEmpty()) {
            base = storeDao.getAll();
        }

        // 3) 這裡只回傳 SQL 結果；如要再把 category/tags/menuItems 一起模糊比對，
        //    可改為呼叫 searchFuzzyWithFieldsBlocking(keyword)（見下方進階版）
        return base != null ? base : java.util.Collections.emptyList();
    }

    /** （可選進階）同時對 category/tags/menuItems 做 in-memory 模糊補強 */
    public List<StoreEntity> searchFuzzyWithFieldsBlocking(String keyword) {
        final String kw = norm(keyword);
        final String pattern = "%" + kw + "%";

        List<StoreEntity> base = storeDao.searchByNameOrAddressFuzzy(pattern);
        if (base == null) base = new ArrayList<>();
        if (kw.isEmpty()) return base; // 空關鍵字就不額外過濾

        List<StoreEntity> out = new ArrayList<>();
        for (StoreEntity e : base) {
            if (e == null) continue;
            if (containsIgnoreCase(e.storeName, kw)) { out.add(e); continue; }
            if (containsIgnoreCase(e.address, kw))   { out.add(e); continue; }
            if (e.category != null) {
                for (String c : e.category) { if (containsIgnoreCase(c, kw)) { out.add(e); break; } }
            }
            if (e.tags != null) {
                for (String t : e.tags) { if (containsIgnoreCase(t, kw)) { out.add(e); break; } }
            }
            if (e.menuItems != null) {
                for (String m : e.menuItems) { if (containsIgnoreCase(m, kw)) { out.add(e); break; } }
            }
        }
        return out;
    }

    // ====== 新增：工具函式（不影響既有程式） ======
    private static String norm(String s) {
        if (s == null) return "";
        String x = s.replace("\ufeff","")
                .replace("\u200b","").replace("\u200c","").replace("\u200d","")
                .trim();
        x = java.text.Normalizer.normalize(x, java.text.Normalizer.Form.NFKC);
        return x.toLowerCase(java.util.Locale.ROOT);
    }
    private static boolean containsIgnoreCase(String text, String kw) {
        if (text == null) return false;
        return norm(text).contains(kw);
    }


    /**
     * 透過 ID 列表查詢多家店。
     * 同樣也是個傳聲筒，直接呼叫 DAO 的方法。
     */
    public List<StoreEntity> getByIdsBlocking(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList(); // 如果 ID 列表是空的，就直接回傳空清單，避免查詢資料庫。
        }
        return storeDao.getByIdsBlocking(ids);
    }

    /**
     * 內部工具：從 App 的 assets 資料夾讀取並解析 JSON 檔案。
     * @return 一個 Store (網路模型) 的清單。
     */
    private List<Store> readStoresFromAssets(AssetManager am, String filename) throws Exception {
        // 使用 try-with-resources 自動關閉檔案讀取流
        try (InputStream is = am.open(filename);
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            // 告訴 Gson 我們要把 JSON 轉成一個「Store 的清單」
            Type listType = new TypeToken<List<Store>>() {}.getType();
            return gson.fromJson(br, listType);
        }
    }
}
