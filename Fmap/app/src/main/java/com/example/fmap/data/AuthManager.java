package com.example.fmap.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class AuthManager {
    private static final String PREF = "auth_prefs";
    private static final String K_LOGIN = "is_login";
    private static final String K_NAME  = "name";
    private static final String K_MAIL  = "email";

    private AuthManager(){}

    private static SharedPreferences sp(Context c){
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static boolean isLoggedIn(Context c){ return sp(c).getBoolean(K_LOGIN, false); }
    public static String name(Context c){ return sp(c).getString(K_NAME, "Guest"); }
    public static String email(Context c){ return sp(c).getString(K_MAIL, "Tap to login"); }

    // Demo：一鍵假登入（之後換成真正登入流程）
    public static void loginDemo(Context c){
        sp(c).edit()
                .putBoolean(K_LOGIN, true)
                .putString(K_NAME, "Happy Food Map")
                .putString(K_MAIL, "contact@happyfoodmap.com")
                .apply();
    }

    public static void logout(Context c){
        sp(c).edit().clear().apply();
    }
}
