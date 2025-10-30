package com.example.fmap.data.local;

import androidx.room.TypeConverter; // 引入 Room 的「翻譯官」標籤
import com.google.gson.Gson; // 引入 Google 的 JSON 翻譯工具
import com.google.gson.reflect.TypeToken; // 輔助工具，用來處理 List<T> 這種複雜類型
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Type Converters
 * 讓 Room 能儲存它看不懂的複雜資料 (如 List 或 Map)
 * 原理：存入時變文字 (JSON)，取出時變回物件
 */
public class Converters {
    private static final Gson gson = new Gson();

    //  List<String>
    /**
     * @TypeConverter 標籤：告訴 Room 這是「存入」時要用的翻譯方法
     * 把 List 物件翻譯成純文字
     */
    @TypeConverter
    public static String fromStringList(List<String> list) {
        return list == null ? null : gson.toJson(list);
    }

    /**
     * @TypeConverter 標籤：告訴 Room 這是「讀取」時要用的翻譯方法
     * 把純文字翻譯回 List 物件
     */
    @TypeConverter
    public static List<String> toStringList(String json) {
        if (json == null) {
            return Collections.emptyList();
        }
        Type listType = new TypeToken<List<String>>() {}.getType();
        return gson.fromJson(json, listType);
    }

    // Map<String, Object>

    /**
     * 「存入」時用：把 Map 物件翻譯成純文字
     */
    @TypeConverter
    public static String fromMap(Map<String, Object> map) {
        return map == null ? null : gson.toJson(map);
    }

    /**
     * 「讀取」時用：把純文字翻譯回 Map 物件
     */
    @TypeConverter
    public static Map<String, Object> toMap(String json) {
        if (json == null) {
            return Collections.emptyMap();
        }
        Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
        return gson.fromJson(json, mapType);
    }
}
