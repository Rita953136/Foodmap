package com.example.fmap.model;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 統一的假資料來源：讓 Place 與 FavItem 顯示相同內容。
 * 放在 com.example.fmap.model，供 Adapter 直接呼叫 FakeData.fakePlaces()/fakeFavItems()。
 *
 * 注意：Place.java 在你提供的檔案中是不可變(final 欄位)，
 * 這裡使用反射去尋找一個符合 (String id, String name, double lat, double lng, Double rating,
 * Integer distanceMeters, List<String> tags, Integer priceLevel, String thumbnailUrl) 的建構子。
 * 如果你的 Place 建構子簽名不同，請到 makePlaceViaReflection() 依照實際參數調整。
 */
public final class FakeData {

    private FakeData() {}

    // -------- 共用假資料欄位（兩邊共用同一份） --------
    private static final String ID = "demo-id-001";
    private static final String NAME = "示範店家 Demo Bistro";
    private static final double LAT = 24.179;   // 逢甲大學附近隨意值
    private static final double LNG = 120.646;
    private static final Double RATING = 4.5;
    private static final Integer DIST_METERS = 350;
    private static final List<String> TAGS = Arrays.asList("拉麵", "豚骨", "濃湯");
    private static final Integer PRICE_LEVEL = 2; // 會顯示 "$$"
    private static final String THUMBNAIL = "";   // 空字串=不載入圖片（你也可以填一張測試圖）

    // ================== FavItem 假資料 ==================
    public static FavItem fakeFavItem() {
        FavItem f = new FavItem();
        f.id = ID;
        f.name = NAME;
        f.lat = LAT;
        f.lng = LNG;
        f.rating = RATING;
        f.distanceMeters = DIST_METERS;
        f.tags = TAGS;
        f.priceLevel = PRICE_LEVEL;
        f.thumbnailUrl = THUMBNAIL;
        return f;
    }

    public static List<FavItem> fakeFavItems(int count) {
        List<FavItem> list = new ArrayList<>();
        for (int i = 0; i < count; i++) list.add(fakeFavItem());
        return list;
    }

    // ================== Place 假資料 ==================
    public static Place fakePlace() {
        // 由於 Place 欄位是 final，推測有全參數建構子；用反射建立。
        Place p = makePlaceViaReflection(
                ID, NAME, LAT, LNG, RATING, DIST_METERS, TAGS, PRICE_LEVEL, THUMBNAIL
        );
        if (p == null) {
            // 如果反射失敗，拋出清楚的錯誤，提示你調整建構子簽名
            throw new IllegalStateException(
                    "FakeData: 無法建立 Place，請確認 Place 是否有對應的全參數建構子，" +
                            "或修改 FakeData.makePlaceViaReflection() 以符合你的 Place.java。"
            );
        }
        return p;
    }

    public static List<Place> fakePlaces(int count) {
        List<Place> list = new ArrayList<>();
        for (int i = 0; i < count; i++) list.add(fakePlace());
        return list;
    }

    /**
     * 透過反射建立 Place 物件。
     * 預設嘗試尋找 9 參數建構子：(String, String, double, double, Double, Integer, List, Integer, String)。
     * 若你的 Place 建構子順序/型別不同，請依照實際調整比對邏輯或直接 new。
     */
    @SuppressWarnings({"rawtypes","unchecked"})
    private static Place makePlaceViaReflection(
            String id,
            String name,
            double lat,
            double lng,
            Double rating,
            Integer distanceMeters,
            List<String> tags,
            Integer priceLevel,
            String thumbnailUrl
    ) {
        try {
            Class<?> clazz = Class.forName("com.example.fmap.model.Place");
            Constructor<?>[] ctors = clazz.getDeclaredConstructors();
            for (Constructor<?> c : ctors) {
                Class<?>[] ptypes = c.getParameterTypes();
                if (ptypes.length != 9) continue;

                // 粗略比對各型別（允許 double vs Double 的對應）
                if (isString(ptypes[0]) &&
                    isString(ptypes[1]) &&
                    isDoubleOrDoubleObj(ptypes[2]) &&
                    isDoubleOrDoubleObj(ptypes[3]) &&
                    isDoubleObj(ptypes[4]) &&
                    isIntegerObj(ptypes[5]) &&
                    isList(ptypes[6]) &&
                    isIntegerObj(ptypes[7]) &&
                    isString(ptypes[8])) {

                    c.setAccessible(true);
                    Object obj = c.newInstance(
                            id, name, lat, lng, rating, distanceMeters, tags, priceLevel, thumbnailUrl
                    );
                    return (Place) obj;
                }
            }
            return null;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    private static boolean isString(Class<?> c) {
        return c == String.class;
    }
    private static boolean isList(Class<?> c) {
        return List.class.isAssignableFrom(c);
    }
    private static boolean isIntegerObj(Class<?> c) {
        return c == Integer.class;
    }
    private static boolean isDoubleObj(Class<?> c) {
        return c == Double.class;
    }
    private static boolean isDoubleOrDoubleObj(Class<?> c) {
        return c == Double.TYPE || c == Double.class;
    }
}
