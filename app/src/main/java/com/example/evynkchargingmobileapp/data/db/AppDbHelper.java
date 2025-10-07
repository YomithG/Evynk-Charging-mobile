package com.example.evynkchargingmobileapp.data.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class AppDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "evcharge.db";
    private static final int DB_VERSION = 1;

    public AppDbHelper(Context ctx) { super(ctx, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS users(" +
                "nic TEXT PRIMARY KEY," +
                "name TEXT," +
                "email TEXT," +
                "phone TEXT," +
                "status INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE IF NOT EXISTS tokens(" +
                "nic TEXT PRIMARY KEY," +
                "accessToken TEXT," +
                "refreshToken TEXT)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) { }
}
