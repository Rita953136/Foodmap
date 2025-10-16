package com.example.fmap.model;

import com.google.firebase.firestore.PropertyName;
import java.util.List;

public class Place {
    public String id;
    public String name;
    public Double rating;

    @PropertyName("tags_top3")
    public List<String> tags;

    @PropertyName("photo_url")
    public String photoUrl;

    @PropertyName("intro")
    public String introLine;
    public  String address = "台中市西屯區";;

    public Place() {}
}
