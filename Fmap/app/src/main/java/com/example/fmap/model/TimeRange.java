package com.example.fmap.model;

// 為了讓這個物件可以在 Activity 或 Fragment 之間傳遞，
// 後續可以加上 implements Serializable
public class TimeRange {

    // 公開的成員變數，可以直接存取
    public String open;     // 開店時間，例如 "11:00"
    public String close;    // 關店時間，例如 "14:00"

    /**
     * 空的建構子。
     * 這是 Gson 或其他框架在自動建立物件時所必需的。
     */
    public TimeRange() {}

    /**
     * 方便我們手動建立時段物件的建構子。
     * @param open  開店時間
     * @param close 關店時間
     */
    public TimeRange(String open, String close) {
        this.open = open;
        this.close = close;
    }
}
