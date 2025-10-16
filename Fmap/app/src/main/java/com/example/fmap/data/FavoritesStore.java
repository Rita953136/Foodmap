package com.example.fmap.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.fmap.model.Place;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 管理收藏店家的本地儲存（SharedPreferences）
 * Singleton：請用 getInstance(context) 取得；建構子為 private。
 */
public final class FavoritesStore {

    private static volatile FavoritesStore INSTANCE;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    private static final String PREF_NAME = "favorites_store";
    private static final String KEY_FAVORITES = "favorites_list";

    private FavoritesStore(Context context) {
        Context app = context.getApplicationContext();
        prefs = app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static FavoritesStore getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (FavoritesStore.class) {
                if (INSTANCE == null) {
                    INSTANCE = new FavoritesStore(context);
                }
            }
        }
        return INSTANCE;
    }

    /** 讀取全部收藏 */
    public List<Place> getAll() {
        String json = prefs.getString(KEY_FAVORITES, null);
        if (json == null) return new ArrayList<>();
        Type listType = new TypeToken<List<Place>>() {}.getType();
        List<Place> list = gson.fromJson(json, listType);
        return list != null ? list : new ArrayList<>();
    }

    /** 只取所有收藏的 id（給過濾/比對用） */
    public Set<String> getAllIds() {
        List<Place> list = getAll();
        Set<String> ids = new HashSet<>();
        for (Place p : list) {
            if (p != null && p.id != null) ids.add(p.id);
        }
        return ids;
    }

    /** 依 id 取得收藏的 Place；找不到回傳 null */
    public Place get(String id) {
        if (id == null) return null;
        for (Place p : getAll()) {
            if (p != null && id.equals(p.id)) return p;
        }
        return null;
    }

    /** 檢查是否為收藏 */
    public boolean isFavorite(String id) {
        if (id == null) return false;
        for (Place p : getAll()) {
            if (p != null && id.equals(p.id)) return true;
        }
        return false;
    }

    /** 新增（若已存在相同 id 則忽略） */
    public void add(Place place) {
        if (place == null || place.id == null) return;
        List<Place> list = getAll();
        for (Place p : list) {
            if (p != null && place.id.equals(p.id)) return; // 已存在
        }
        list.add(place);
        save(list);
    }

    /** 新增或更新（若存在相同 id 則覆蓋） */
    public void addOrUpdate(Place place) {
        if (place == null || place.id == null) return;
        List<Place> list = getAll();
        boolean updated = false;
        for (int i = 0; i < list.size(); i++) {
            Place p = list.get(i);
            if (p != null && place.id.equals(p.id)) {
                list.set(i, place);
                updated = true;
                break;
            }
        }
        if (!updated) list.add(place);
        save(list);
    }

    /** 依 id 移除收藏 */
    public void remove(String id) {
        if (id == null) return;
        List<Place> list = getAll();
        list.removeIf(p -> p != null && id.equals(p.id));
        save(list);
    }

    /** 寫回 SharedPreferences */
    private void save(List<Place> list) {
        prefs.edit().putString(KEY_FAVORITES, gson.toJson(list)).apply();
    }
}
