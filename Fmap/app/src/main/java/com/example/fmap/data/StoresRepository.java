package com.example.fmap.data;

import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 純本機 JSON 版資料倉儲：
 * - 讀取 assets/stores_info_normalized.json
 * - 對外提供 LiveData 查詢
 * - 與 HomeViewModel 的介面一致（initFromAssets / getDbReady / searchAdvanced / getByIds）
 */
public class StoresRepository {

    private static final String TAG = "StoresRepository";
    private static final String ASSET_FILE = "stores_info_normalized.json";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Boolean> dbReady = new MutableLiveData<>(false);

    /** 以 Entity 形式存於記憶體（若改用 Room，可直接替換來源） */
    private final List<StoreEntity> inMemory = new ArrayList<>();
    private final Gson gson = new Gson();

    public StoresRepository(Application app) {}

    public LiveData<Boolean> getDbReady() { return dbReady; }

    /** 第一次啟動時呼叫；讀取 assets JSON 載入到記憶體 */
    public void initFromAssets(Context ctx) {
        io.execute(() -> {
            try {
                List<Store> raw = readStoresFromAssets(ctx.getAssets(), ASSET_FILE);
                inMemory.clear();
                for (Store s : raw) {
                    StoreEntity e = StoreMappers.toEntity(s);
                    if (e != null) inMemory.add(e);
                }
                dbReady.postValue(true);
            } catch (Exception e) {
                Log.e(TAG, "initFromAssets failed", e);
                dbReady.postValue(false);
            }
        });
    }
    // 在 StoresRepository 裡加：
    public LiveData<List<StoreEntity>> getAll() {
        MutableLiveData<List<StoreEntity>> live = new MutableLiveData<>();
        io.execute(() -> live.postValue(new ArrayList<>(inMemory)));
        return live;
    }


    /** 關鍵字 + 類別(用 tags 判斷) + 菜名包含 + 價位等值 之簡易查詢 */
    public LiveData<List<StoreEntity>> searchAdvanced(String keyword,
                                                      List<String> categories,
                                                      String dishLike,
                                                      String priceEq) {
        MutableLiveData<List<StoreEntity>> live = new MutableLiveData<>();
        io.execute(() -> {
            boolean noKeyword  = keyword == null  || keyword.trim().isEmpty();
            boolean noCats     = categories == null || categories.isEmpty();
            boolean noDish     = dishLike == null || dishLike.trim().isEmpty();
            boolean noPrice    = priceEq == null || priceEq.trim().isEmpty();

            // ★ 沒有任何條件 → 直接回全部
            if (noKeyword && noCats && noDish && noPrice) {
                live.postValue(new ArrayList<>(inMemory));
                return;
            }

            // 有條件 → 做原本的過濾
            String kw   = safeLower(keyword);
            String dish = safeLower(dishLike);
            String price= safeLower(priceEq);

            List<StoreEntity> result = new ArrayList<>();
            for (StoreEntity s : inMemory) {
                boolean ok = true;

                if (!noKeyword) {
                    String source = (safeLower(s.name) + " " + safeLower(s.address));
                    ok = source.contains(kw);
                    if (!ok) continue;
                }

                if (!noCats) {
                    ok = false;
                    String tagStr = safeLower(s.tags);
                    for (String c : categories) {
                        if (c != null && !c.isEmpty() && tagStr.contains(safeLower(c))) {
                            ok = true; break;
                        }
                    }
                    if (!ok) continue;
                }

                if (!noDish) {
                    String menu = safeLower(s.menuItems);
                    ok = menu.contains(dish);
                    if (!ok) continue;
                }

                if (!noPrice && s.priceRange != null) {
                    ok = safeLower(s.priceRange).equals(price);
                    if (!ok) continue;
                }

                result.add(s);
            }
            live.postValue(result);
        });
        return live;
    }


    /** 依多個 id 取回資料（供垃圾桶頁面用） */
    public LiveData<List<StoreEntity>> getByIds(List<String> ids) {
        MutableLiveData<List<StoreEntity>> live = new MutableLiveData<>();
        io.execute(() -> {
            if (ids == null || ids.isEmpty()) { live.postValue(new ArrayList<>()); return; }
            List<StoreEntity> out = new ArrayList<>();
            for (StoreEntity s : inMemory) {
                if (ids.contains(s.id)) out.add(s);
            }
            live.postValue(out);
        });
        return live;
    }

    // ---------------- internal helpers ----------------
    private List<Store> readStoresFromAssets(AssetManager am, String filename) throws Exception {
        try (InputStream is = am.open(filename);
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            Type listType = new TypeToken<List<Store>>(){}.getType();
            return gson.fromJson(br, listType);
        }
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
