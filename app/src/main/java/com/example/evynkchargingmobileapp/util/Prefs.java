package com.example.evynkchargingmobileapp.util;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private static final String P = "auth_prefs";
    private static final String KEY_CURRENT_NIC = "current_nic";

    public static void setCurrentNic(Context ctx, String nic) {
        SharedPreferences sp = ctx.getSharedPreferences(P, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_CURRENT_NIC, nic).apply();
    }

    public static String getCurrentNic(Context ctx) {
        return ctx.getSharedPreferences(P, Context.MODE_PRIVATE).getString(KEY_CURRENT_NIC, null);
    }

    public static void clear(Context ctx) {
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit().clear().apply();
    }
}
