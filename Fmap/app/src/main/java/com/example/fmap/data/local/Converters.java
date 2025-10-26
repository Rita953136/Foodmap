package com.example.fmap.data.local;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Room 的型別轉換器。
 * 讓 Room 能夠儲存和讀取它原本不支援的複雜型別，例如 List<String> 或 Map。
 * 原理是將物件用 Gson 轉換成 JSON 字串來儲存，讀取時再反向轉換回來。
 */
public class Converters {
    private static final Gson gson = new Gson();

    // --- List<String> 的轉換器 ---
    @TypeConverter
    public static String fromStringList(List<String> list) {
        if (list == null) {
            return null;
        }
        return gson.toJson(list);
    }

    @TypeConverter
    public static List<String> toStringList(String json) {
        if (json == null) {
            return Collections.emptyList();
        }
        Type listType = new TypeToken<List<String>>() {}.getType();
        return gson.fromJson(json, listType);
    }

    // --- Map<String, Object> 的轉換器 (用來儲存 business_hours, price_range 等) ---
    @TypeConverter
    public static String fromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        return gson.toJson(map);
    }

    @TypeConverter
    public static Map<String, Object> toMap(String json) {
        if (json == null) {
            return Collections.emptyMap();
        }
        Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
        return gson.fromJson(json, mapType);
    }
}
