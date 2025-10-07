package com.example.evynkchargingmobileapp.data.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.evynkchargingmobileapp.data.model.User;

public class UserDao {
    private final AppDbHelper helper;
    public UserDao(Context ctx) { helper = new AppDbHelper(ctx); }

    public void upsertUser(User u) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("nic", u.nic);
        cv.put("name", u.name);
        cv.put("email", u.email);
        cv.put("phone", u.phone);
        cv.put("status", u.status);
        db.insertWithOnConflict("users", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public User getUser(String nic) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT nic,name,email,phone,status FROM users WHERE nic=?",
                new String[]{nic})) {
            if (c.moveToFirst()) {
                User u = new User();
                u.nic   = c.getString(0);
                u.name  = c.getString(1);
                u.email = c.getString(2);
                u.phone = c.getString(3);
                u.status= c.getInt(4);
                return u;
            }
        }
        return null;
    }

    public void saveTokens(String nic, String access, String refresh) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("nic", nic);
        cv.put("accessToken", access);
        cv.put("refreshToken", refresh);
        db.insertWithOnConflict("tokens", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String getAccessToken(String nic) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT accessToken FROM tokens WHERE nic=?",
                new String[]{nic})) {
            if (c.moveToFirst()) return c.getString(0);
        }
        return null;
    }
}
