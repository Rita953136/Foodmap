package com.example.fmap.model;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 本機收藏資料儲存（Singleton）
 * - 以 SharedPreferences + Gson 存取
 * - HomeViewModel 用到的方法：getInstance(), add(Place), getAll()
 */
public class FavoritesStore {

    private static final String PREFS_NAME = "FmapUserPrefs";
    private static final String KEY_FAVORITES = "favorites_places";

    private static FavoritesStore INSTANCE;

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private final Type listType = new TypeToken<List<Place>>(){}.getType();

    private FavoritesStore(Context appCtx) {
        this.prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized FavoritesStore getInstance(Context ctx) {
        if (INSTANCE == null) {
            INSTANCE = new FavoritesStore(ctx.getApplicationContext());
        }
        return INSTANCE;
    }

    /** 取得所有收藏（若沒有就回傳空清單） */
    public synchronized List<Place> getAll() {
        String json = prefs.getString(KEY_FAVORITES, "[]");
        List<Place> list = gson.fromJson(json, listType);
        return list != null ? list : new ArrayList<>();
    }

    /** 新增或更新收藏（以 id 去重） */
    public synchronized void add(@Nullable Place place) {
        if (place == null || place.id == null) return;
        List<Place> list = new ArrayList<>(getAll());
        int idx = indexOfId(list, place.id);
        if (idx >= 0) {
            list.set(idx, place);   // 已存在 → 覆蓋
        } else {
            list.add(place);        // 新增
        }
        save(list);
    }

    /** 取消收藏 */
    public synchronized void removeById(String id) {
        if (id == null) return;
        List<Place> list = new ArrayList<>(getAll());
        int idx = indexOfId(list, id);
        if (idx >= 0) {
            list.remove(idx);
            save(list);
        }
    }
    // 放在 FavoritesStore 內任意位置（public methods 區）
    public synchronized void remove(String id) {
        removeById(id);
    }


    /** 是否已收藏 */
    public synchronized boolean contains(String id) {
        return indexOfId(getAll(), id) >= 0;
    }

    // ---------------- internal ----------------
    private void save(List<Place> list) {
        prefs.edit().putString(KEY_FAVORITES, gson.toJson(list, listType)).apply();
    }

    private static int indexOfId(List<Place> list, String id) {
        if (list == null || id == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            Place p = list.get(i);
            if (p != null && id.equals(p.id)) return i;
        }
        return -1;
    }
}
