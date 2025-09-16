package com.example.fmap.model;

public class SwipeRecord {
    public final String placeId;
    public final SwipeAction action;
    public final long ts;
    public SwipeRecord(String placeId, SwipeAction action, long ts) {
        this.placeId = placeId; this.action = action; this.ts = ts;
    }
}