package com.example.evynkchargingmobileapp.repo;

import android.content.Context;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.evynkchargingmobileapp.data.db.UserDao;
import com.example.evynkchargingmobileapp.data.model.User;
import com.example.evynkchargingmobileapp.net.ApiClient;
import com.example.evynkchargingmobileapp.util.Prefs;

public class AuthRepository {
    private static final String BASE = "http://10.0.2.2:5000/api";
    private static final String PATH_REGISTER_OWNER = "/Auth/register-owner";
    private static final String PATH_LOGIN_OWNER    = "/Auth/login-owner";

    private final ApiClient api;
    private final UserDao dao;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public AuthRepository(Context ctx) {
        this.api = new ApiClient(BASE);
        this.dao = new UserDao(ctx);
    }

    public interface Callback<T> { void onSuccess(T data); void onError(String msg); }

    // Register (Owner self-register)
    public void register(User u, String password, Callback<User> cb) {
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject()
                        .put("nic", u.nic)
                        .put("fullName", u.name == null ? "" : u.name)
                        .put("email", u.email == null ? "" : u.email)
                        .put("phone", u.phone == null ? "" : u.phone)
                        .put("password", password);

                JSONObject res = api.post(PATH_REGISTER_OWNER, body, null);

                String token = res.optString("token", null);
                JSONObject owner = res.optJSONObject("owner");

                User saved = new User();
                if (owner != null) {
                    saved.nic   = owner.optString("nic", u.nic);
                    saved.name  = owner.optString("fullName", u.name);
                    saved.email = owner.optString("email", u.email);
                    saved.phone = owner.optString("phone", u.phone);
                    saved.status= owner.optBoolean("isActive", true) ? 1 : 0;
                } else {
                    saved = u; saved.status = 1;
                }

                dao.upsertUser(saved);
                if (token != null) dao.saveTokens(saved.nic, token, null);
                cb.onSuccess(saved);
            } catch (Exception e) { cb.onError(e.getMessage()); }
        });
    }

    // Login (Owner)
    public void login(String email, String password, Callback<User> cb) {
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject()
                        .put("email", email)
                        .put("password", password);

                JSONObject res = api.post(PATH_LOGIN_OWNER, body, null);

                String token = res.optString("token", null);
                JSONObject owner = res.optJSONObject("owner");

                User saved = new User();
                if (owner != null) {
                    saved.nic   = owner.optString("nic", email);
                    saved.name  = owner.optString("fullName", "");
                    saved.email = owner.optString("email", email);
                    saved.phone = owner.optString("phone", "");
                    saved.status= owner.optBoolean("isActive", true) ? 1 : 0;
                } else {
                    saved.nic = email; saved.name = ""; saved.email = email; saved.phone = ""; saved.status = 1;
                }

                dao.upsertUser(saved);
                if (token != null) dao.saveTokens(saved.nic, token, null);

                cb.onSuccess(saved);
            } catch (Exception e) { cb.onError(e.getMessage()); }
        });
    }

    public void setCurrent(Context ctx, String nic) { Prefs.setCurrentNic(ctx, nic); }
    public String getAccessTokenForCurrent(Context ctx) {
        String nic = Prefs.getCurrentNic(ctx);
        return nic == null ? null : dao.getAccessToken(nic);
    }
}
