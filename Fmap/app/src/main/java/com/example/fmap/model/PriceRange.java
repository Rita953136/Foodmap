package com.example.fmap.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** 價位資訊（對應 JSON 的 price_range，可是文字或分級都可裝進來） */
public class PriceRange implements Serializable {

    /** 例如 "$1-200" 或 "$$" 等 */
    @SerializedName(value = "text", alternate = {"price", "range"})
    private String text;

    /** 可選：分級 1~3（便宜/中等/高） */
    @SerializedName("level")
    private Integer level;

    public PriceRange() {}

    public PriceRange(String text, Integer level) {
        this.text = text;
        this.level = level;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }

    @Override public String toString() {
        return text != null ? text : (level != null ? String.valueOf(level) : "");
    }
}
