package com.example.fmap.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DailyQuotaManager {
    private static final String PREF_NAME = "daily_quota";
    private static final String KEY_DATE = "date";
    private static final String KEY_COUNT = "count";
    private static final String KEY_ENABLED = "enabled"; // 是否啟用每日限制（預設 true）

    private final SharedPreferences prefs;
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.TAIWAN);

    public DailyQuotaManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_ENABLED)) prefs.edit().putBoolean(KEY_ENABLED, true).apply();
        resetIfNewDay();
    }

    private void resetIfNewDay() {
        String today = fmt.format(new Date());
        String saved = prefs.getString(KEY_DATE, "");
        if (!today.equals(saved)) {
            prefs.edit().putString(KEY_DATE, today).putInt(KEY_COUNT, 0).apply();
        }
    }

    public int getCount() {
        resetIfNewDay();
        return prefs.getInt(KEY_COUNT, 0);
    }

    public void increment() {
        resetIfNewDay();
        int used = getCount() + 1;
        prefs.edit().putInt(KEY_COUNT, used).apply();
    }

    public int getRemaining(int dailyLimit) {
        resetIfNewDay();
        if (!isEnabled()) return Integer.MAX_VALUE; // 停用限制時當作無上限
        int used = getCount();
        return Math.max(0, dailyLimit - used);
    }

    // —— 開發者輔助 —— //
    public void setEnabled(boolean enabled) { prefs.edit().putBoolean(KEY_ENABLED, enabled).apply(); }
    public boolean isEnabled() { return prefs.getBoolean(KEY_ENABLED, true); }

    /** 只重置今天的計數（不改開關） */
    public void resetToday() {
        String today = fmt.format(new Date());
        prefs.edit().putString(KEY_DATE, today).putInt(KEY_COUNT, 0).apply();
    }

    /** 測試用：清空所有偏好 */
    public void resetForTesting() { prefs.edit().clear().apply(); }
}
