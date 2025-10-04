package com.example.fmap.model;

import java.util.List;

public class StoreSummary {
    public StoreSummary() {}

    // Firestore 五個欄位
    public String name;              // 商家名稱
    public String intro;             // 商家基本介紹
    public List<String> tags_top3;   // Top3 標籤（Array<String>）
    public Double rating;            // 評分(0.0~5.0)
    public String photo_url;         // 圖片（可先為空字串）
}
