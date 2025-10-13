package com.example.fmap.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.example.fmap.model.FavItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 本機收藏（SharedPreferences JSON 持久化） */
public class FavoritesStore {

    private static final String PREF = "favorites_store_v1";
    private static final String KEY_LIST = "fav_list";
    private final SharedPreferences sp;

    public FavoritesStore(@NonNull Context ctx) {
        this.sp = ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /* ---------- 對外 API ---------- */

    /** Upsert：存在就更新，不存在就新增（以 id 為 key） */
    public synchronized void addOrUpdate(@NonNull FavItem item) {
        if (item.id == null || item.id.isEmpty()) return;
        List<FavItem> all = getAll();
        int idx = indexOf(all, item.id);
        if (idx >= 0) all.set(idx, item);
        else all.add(0, item); // 新加入放最上
        saveAll(all);
    }

    /** 新增（不判重） */
    public synchronized void add(@NonNull FavItem item) {
        List<FavItem> all = getAll();
        all.add(0, item);
        saveAll(all);
    }

    public synchronized void remove(@NonNull String id) {
        List<FavItem> all = getAll();
        int idx = indexOf(all, id);
        if (idx >= 0) {
            all.remove(idx);
            saveAll(all);
        }
    }

    public synchronized void clear() {
        sp.edit().remove(KEY_LIST).apply();
    }

    /** 讀全部收藏（已排序，最新在前） */
    @NonNull
    public synchronized List<FavItem> getAll() {
        String raw = sp.getString(KEY_LIST, null);
        List<FavItem> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;

        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(fromJson(o));
            }
        } catch (Exception ignore) { /* 破檔容忍 */ }
        return out;
    }

    /* ---------- 內部 ---------- */

    private synchronized void saveAll(@NonNull List<FavItem> list) {
        JSONArray arr = new JSONArray();
        for (FavItem it : list) {
            if (it == null) continue;
            arr.put(toJson(it));
        }
        sp.edit().putString(KEY_LIST, arr.toString()).apply();
    }

    private int indexOf(List<FavItem> list, String id) {
        if (list == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            FavItem it = list.get(i);
            if (it != null && it.id != null && it.id.equals(id)) return i;
        }
        return -1;
    }

    private static JSONObject toJson(@NonNull FavItem it) {
        JSONObject o = new JSONObject();
        try {
            o.put("id", it.id);
            o.put("name", it.name);
            o.put("lat", it.lat);
            o.put("lng", it.lng);
            if (it.rating != null) o.put("rating", it.rating); else o.put("rating", JSONObject.NULL);
            if (it.distanceMeters != null) o.put("distanceMeters", it.distanceMeters); else o.put("distanceMeters", JSONObject.NULL);
            if (it.priceLevel != null) o.put("priceLevel", it.priceLevel); else o.put("priceLevel", JSONObject.NULL);
            o.put("thumbnailUrl", it.thumbnailUrl == null ? JSONObject.NULL : it.thumbnailUrl);

            JSONArray tags = new JSONArray();
            if (it.tags != null) for (String t : it.tags) tags.put(t);
            o.put("tags", tags);
        } catch (Exception ignore) {}
        return o;
    }

    private static FavItem fromJson(@NonNull JSONObject o) {
        FavItem it = new FavItem();
        it.id = o.optString("id", null);
        it.name = o.optString("name", null);
        it.lat = o.optDouble("lat", 0d);
        it.lng = o.optDouble("lng", 0d);

        if (!o.isNull("rating")) it.rating = o.optDouble("rating");
        if (!o.isNull("distanceMeters")) it.distanceMeters = o.optInt("distanceMeters");
        if (!o.isNull("priceLevel")) it.priceLevel = o.optInt("priceLevel");
        it.thumbnailUrl = o.isNull("thumbnailUrl") ? null : o.optString("thumbnailUrl", null);

        JSONArray arr = o.optJSONArray("tags");
        List<String> tags = new ArrayList<>();
        if (arr != null) for (int i = 0; i < arr.length(); i++) tags.add(String.valueOf(arr.opt(i)));
        it.tags = tags;

        return it;
    }
}
