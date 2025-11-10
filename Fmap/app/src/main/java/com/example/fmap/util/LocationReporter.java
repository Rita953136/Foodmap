package com.example.fmap.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import okhttp3.*;

public class LocationReporter {
    private static final String ENDPOINT =
            "https://rattly-excuseless-judie.ngrok-free.dev/api/tools/stores_search";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient http = new OkHttpClient();

    // 快取上次座標（沿用策略）
    private static final String PREF = "loc_pref";
    private static final String K_LAT="last_lat", K_LNG="last_lng", K_ACC="last_acc", K_TS="last_ts";

    // 本進程只送一次
    private static boolean sentInThisProcess = false;

    /** 主頁面冷啟動時呼叫一次 */
    public static void reportOnceOnHome(@NonNull Context ctx, @NonNull List<String> tags) {
        if (sentInThisProcess) return;
        sentInThisProcess = true;

        boolean fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (fine || coarse) {
            CancellationTokenSource cts = new CancellationTokenSource();
            LocationServices.getFusedLocationProviderClient(ctx)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                    .addOnSuccessListener(loc -> {
                        Location use = (loc != null) ? loc : loadLast(ctx);
                        if (use == null) return;
                        if (loc != null) saveLast(ctx, loc);
                        post(ctx, use, tags, false);
                    })
                    .addOnFailureListener(e -> {
                        Location use = loadLast(ctx);
                        if (use != null) post(ctx, use, tags, false);
                    });
        } else {
            Location use = loadLast(ctx);
            if (use != null) post(ctx, use, tags, false);
        }
    }

    private static void saveLast(@NonNull Context ctx, @NonNull Location loc){
        ctx.getSharedPreferences(PREF, 0).edit()
                .putLong(K_TS, System.currentTimeMillis())
                .putFloat(K_ACC, loc.getAccuracy())
                .putString(K_LAT, String.valueOf(loc.getLatitude()))
                .putString(K_LNG, String.valueOf(loc.getLongitude()))
                .apply();
    }
    private static Location loadLast(@NonNull Context ctx){
        var sp = ctx.getSharedPreferences(PREF, 0);
        if(!sp.contains(K_LAT) || !sp.contains(K_LNG)) return null;
        Location loc = new Location("cached");
        loc.setLatitude(Double.parseDouble(sp.getString(K_LAT,"0")));
        loc.setLongitude(Double.parseDouble(sp.getString(K_LNG,"0")));
        loc.setAccuracy(sp.getFloat(K_ACC, 0f));
        loc.setTime(sp.getLong(K_TS, System.currentTimeMillis()));
        return loc;
    }

    private static void post(@NonNull Context ctx, @NonNull Location loc, @NonNull List<String> tags, boolean rankByDistance){
        try {
            JSONObject root = new JSONObject();
            root.put("intent", "store_by_tag");

            org.json.JSONArray arr = new org.json.JSONArray();
            for (String t : tags) arr.put(t);
            root.put("tags", arr);

            JSONObject locObj = new JSONObject();
            locObj.put("lat", loc.getLatitude());
            locObj.put("lng", loc.getLongitude());
            locObj.put("accuracy_m", (double) loc.getAccuracy());
            locObj.put("source", "android_gps");
            locObj.put("timestamp", System.currentTimeMillis());
            root.put("location", locObj);

            root.put("rank_by_distance", rankByDistance);

            RequestBody body = RequestBody.create(root.toString(), JSON);
            Request req = new Request.Builder()
                    .url(ENDPOINT)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            http.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { /* 可加 Log */ }
                @Override public void onResponse(@NonNull Call call, @NonNull Response resp) throws IOException {
                    if (resp.body()!=null) resp.body().close();
                }
            });
        } catch (Exception ignored) {}
    }
}
