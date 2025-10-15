package com.example.fmap.model;

import android.os.Parcel;
import android.os.Parcelable;

public class Swipe implements Parcelable {
    public enum Action { LIKE, NOPE }
    private String placeId;
    private Action action;
    private long ts;
    public Swipe() {}

    public Swipe(String placeId, Action action, long ts) {
        if (placeId == null || placeId.isEmpty()) {
            throw new IllegalArgumentException("placeId cannot be null or empty");
        }
        if (action == null) {
            throw new IllegalArgumentException("action cannot be null");
        }
        this.placeId = placeId;
        this.action = action;
        this.ts = ts;
    }

    public static Swipe now(String placeId, Action action) {
        return new Swipe(placeId, action, System.currentTimeMillis());
    }

    // --- 小幫手 ---
    public boolean isLike() { return action == Action.LIKE; }
    public boolean isNope() { return action == Action.NOPE; }

    // --- Getter / Setter（Firestore / Adapter 取用方便） ---
    public String getPlaceId() { return placeId; }
    public void setPlaceId(String placeId) { this.placeId = placeId; }

    public Action getAction() { return action; }
    public void setAction(Action action) { this.action = action; }

    public long getTs() { return ts; }
    public void setTs(long ts) { this.ts = ts; }

    // --- toString / equals / hashCode ---
    @Override
    public String toString() {
        return "Swipe{placeId='" + placeId + "', action=" + action + ", ts=" + ts + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Swipe)) return false;
        Swipe other = (Swipe) o;
        if (ts != other.ts) return false;
        if (!placeId.equals(other.placeId)) return false;
        return action == other.action;
    }

    @Override
    public int hashCode() {
        int result = placeId.hashCode();
        result = 31 * result + action.hashCode();
        result = 31 * result + Long.hashCode(ts);
        return result;
    }

    // --- Parcelable ---
    protected Swipe(Parcel in) {
        placeId = in.readString();
        String act = in.readString();
        action = (act == null) ? null : Action.valueOf(act);
        ts = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(placeId);
        dest.writeString(action == null ? null : action.name());
        dest.writeLong(ts);
    }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<Swipe> CREATOR = new Creator<Swipe>() {
        @Override
        public Swipe createFromParcel(Parcel in) { return new Swipe(in); }
        @Override
        public Swipe[] newArray(int size) { return new Swipe[size]; }
    };
}
