package com.example.fmap.model;

import com.google.firebase.firestore.PropertyName;
import java.util.List;

public class Place {
    public String id;
    public String name;
    public Double rating;

    @PropertyName("tags_top3")
    public List<String> tags;     // ← 讓 Firestore 的 top3_tags 映射到這個欄位

    @PropertyName("photo_url")
    public String photoUrl;

    @PropertyName("intro")
    public String introLine;

    public Place() {}
}
