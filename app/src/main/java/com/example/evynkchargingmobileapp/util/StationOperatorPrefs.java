package com.example.evynkchargingmobileapp.util;

import android.content.Context;
import android.content.SharedPreferences;

public class StationOperatorPrefs {
    private static final String FILE = "station_operator_prefs";
    private static final String KEY_ID = "station_operator_id";
    private static final String KEY_EMAIL = "station_operator_email";
    private static final String KEY_ROLE = "station_operator_role";
    private static final String KEY_TOKEN = "station_operator_token";
    private static final String KEY_STATION_ID = "station_operator_station_id"; // optional

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static void set(String id, String email, String role, String token, Context c) {
        sp(c).edit()
                .putString(KEY_ID, id)
                .putString(KEY_EMAIL, email)
                .putString(KEY_ROLE, role)
                .putString(KEY_TOKEN, token)
                .apply();
    }

    public static void setStationId(Context c, String stationId) {
        sp(c).edit().putString(KEY_STATION_ID, stationId).apply();
    }

    public static String getId(Context c)       { return sp(c).getString(KEY_ID, null); }
    public static String getEmail(Context c)    { return sp(c).getString(KEY_EMAIL, null); }
    public static String getRole(Context c)     { return sp(c).getString(KEY_ROLE, null); }
    public static String getToken(Context c)    { return sp(c).getString(KEY_TOKEN, null); }
    public static String getStationId(Context c){ return sp(c).getString(KEY_STATION_ID, null); }

    public static void clear(Context c) { sp(c).edit().clear().apply(); }
}
