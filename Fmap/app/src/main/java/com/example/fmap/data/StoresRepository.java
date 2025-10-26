// ✨✨✨ 請複製這整段程式碼，完全覆蓋你的 StoresRepository.java ✨✨✨

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

public class StoresRepository {

    private static final String TAG = "StoresRepository";
    private static final String ASSET_FILE = "stores_info_normalized.json";

    private final MutableLiveData<Boolean> dbReady = new MutableLiveData<>(false);
    private final Gson gson = new Gson();

    private final StoreDao storeDao;
    // ✨【改造點 1】: 取得資料庫的背景執行緒池
    private final ExecutorService databaseExecutor;

    public StoresRepository(Application app) {
        StoreDatabase db = StoreDatabase.getDatabase(app);
        this.storeDao = db.storeDao();
        this.databaseExecutor = StoreDatabase.databaseWriteExecutor; // 從 StoreDatabase 取得共用的執行緒池
    }

    public LiveData<Boolean> getDbReady() {
        return dbReady;
    }

    public void initFromAssets(Context ctx) {
        databaseExecutor.execute(() -> {
            try {
                if (storeDao.count() > 0) {
                    Log.d(TAG, "資料庫已存在，跳過初始化。");
                    dbReady.postValue(true);
                    return;
                }

                Log.d(TAG, "資料庫為空，開始從 JSON 初始化...");
                List<Store> raw = readStoresFromAssets(ctx.getAssets(), ASSET_FILE);
                List<StoreEntity> entities = new ArrayList<>();
                for (Store s : raw) {
                    // 使用我們更新過的 StoreMappers
                    StoreEntity e = StoreMappers.toEntity(s);
                    if (e != null) entities.add(e);
                }

                storeDao.insertAll(entities);
                Log.d(TAG, "資料庫初始化完成，共寫入 " + entities.size() + " 筆資料。");

                dbReady.postValue(true);
            } catch (Exception e) {
                Log.e(TAG, "initFromAssets failed", e);
                dbReady.postValue(false);
            }
        });
    }

    /**
     * 在背景執行緒中，從 Room 資料庫執行進階搜尋。
     * 這個方法接收 ViewModel 傳來的 5 個參數，然後把它們傳遞給 DAO。
     * @return 一個包含 StoreEntity 的列表 (注意：不是 LiveData)。
     */
    public List<StoreEntity> searchAdvancedBlocking(String keyword, String category, int catCount, String dishLike, String priceEq) {
        Log.d("StoresRepository", "在資料庫中執行搜尋: keyword=" + keyword + ", category=" + category);

        // 直接呼叫 DAO 的 blocking 方法，將接收到的 5 個參數，原封不動地傳遞給 DAO。
        return storeDao.searchAdvancedBlocking(keyword, category, catCount, dishLike, priceEq);
    }

    // ✨✨✨【核心改造 3：getByIdsBlocking】✨✨✨
    /**
     * 在背景執行緒中，根據 ID 列表從 Room 資料庫取得店家資料。
     * @return 一個包含 StoreEntity 的列表。
     */
    public List<StoreEntity> getByIdsBlocking(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        // 直接呼叫 DAO 的 blocking 方法
        return storeDao.getByIdsBlocking(ids);
    }


    // --- internal helpers (保持不變) ---
    private List<Store> readStoresFromAssets(AssetManager am, String filename) throws Exception {
        try (InputStream is = am.open(filename);
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            Type listType = new TypeToken<List<Store>>() {}.getType();
            return gson.fromJson(br, listType);
        }
    }
}
