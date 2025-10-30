package com.example.fmap.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * 價位資訊的「資料模型」。
 * 用來裝從 JSON 下載的 price_range 資訊，可以同時處理文字或數字等級。
 * implements Serializable: 讓物件可以在不同元件間傳遞。
 */
public class PriceRange implements Serializable {

    /**
     * @SerializedName: 告訴 Gson，JSON 裡的 "text", "price", "range" 這三種 key，
     *                都可以對應到這個 'text' 變數。
     *                用來存放價位的文字描述，例如 "$1-200" 或 "$$"。
     */
    @SerializedName(value = "text", alternate = {"price", "range"})
    private String text;

    /**
     * @SerializedName: 對應 JSON 中的 "level" key。
     *                用來存放價位的數字分級 (例如 1=便宜, 2=中等, 3=高)。
     */
    @SerializedName("level")
    private Integer level;

    // --- Getter 和 Setter 方法 ---
    // 提供外部程式碼安全存取內部私有(private)屬性的管道。

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }


    /**
     * 覆寫 toString 方法，定義當這個物件被當成文字使用時，應該顯示什麼。
     * 優先顯示 text，如果 text 是空的，才顯示 level。
     */
    @Override
    public String toString() {
        // 如果 text 有內容，就回傳 text
        if (text != null) {
            return text;
        }
        // 如果 level 有內容，就回傳 level 的數字文字
        if (level != null) {
            return String.valueOf(level);
        }
        // 如果都沒有，就回傳空字串
        return "";
    }
}
