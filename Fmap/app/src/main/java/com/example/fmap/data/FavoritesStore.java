package com.example.fmap.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.fmap.model.FavItem;
import com.example.fmap.model.Place;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** 簡易收藏儲存（SharedPreferences + Gson） */
public class FavoritesStore {
    private static final String PREF = "favorites_store";
    private static final String KEY  = "items_v1";

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private final Type listType = new TypeToken<List<FavItem>>(){}.getType();

    public FavoritesStore(Context ctx) {
        prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 讀取全部收藏清單 */
    public synchronized List<FavItem> getAll() {
        String json = prefs.getString(KEY, "[]");
        List<FavItem> list = gson.fromJson(json, listType);
        return list != null ? list : new ArrayList<FavItem>();
    }

    /** 是否已收藏 */
    public synchronized boolean isFavorite(String id) {
        for (FavItem f : getAll()) if (id.equals(f.id)) return true;
        return false;
    }

    /** 加入收藏（由 Place 轉 FavItem） */
    public synchronized void add(Place p) {
        List<FavItem> list = getAll();
        for (FavItem f : list) if (p.getId().equals(f.id)) return; // 已存在
        FavItem fi = new FavItem();
        fi.id = p.getId();
        fi.name = p.getName();
        fi.lat = p.getLat();
        fi.lng = p.getLng();
        fi.rating = p.getRating() != null ? p.getRating().doubleValue() : 0d;
        fi.distanceMeters = p.getDistanceMeters();
        fi.tags = p.getTags();
        fi.priceLevel = p.getPriceLevel();
        fi.thumbnailUrl = p.getThumbnailUrl();
        list.add(fi);
        prefs.edit().putString(KEY, gson.toJson(list)).apply();
    }

    /** 取消收藏 */
    public synchronized void remove(String id) {
        List<FavItem> list = getAll();
        for (Iterator<FavItem> it = list.iterator(); it.hasNext(); ) {
            if (id.equals(it.next().id)) it.remove();
        }
        prefs.edit().putString(KEY, gson.toJson(list)).apply();
    }

    /** 切換收藏狀態：已收藏則移除，未收藏則加入 */
    public synchronized void toggle(Place p) {
        if (isFavorite(p.getId())) remove(p.getId()); else add(p);
    }

    /** 清空（開發/測試用） */
    public synchronized void clearAll() {
        prefs.edit().putString(KEY, "[]").apply();
    }

    public synchronized void addByFields(String id, String name, Double rating, Integer distance,
                                         Integer price, java.util.List<String> tags,
                                         String thumb, double lat, double lng) {
        List<FavItem> list = getAll();
        for (FavItem f : list) if (id.equals(f.id)) return;
        FavItem fi = new FavItem();
        fi.id=id; fi.name=name; fi.rating=rating; fi.distanceMeters=distance;
        fi.priceLevel=price; fi.tags=tags; fi.thumbnailUrl=thumb; fi.lat=lat; fi.lng=lng;
        list.add(fi);
        prefs.edit().putString(KEY, gson.toJson(list)).apply();
    }
}
