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
    private final ApiClient api;
    private final UserDao dao;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    // TODO: set your API root used by the web backend
    public AuthRepository(Context ctx) {
        this.api = new ApiClient("https://YOUR-API-HOST");
        this.dao = new UserDao(ctx);
    }

    public interface Callback<T> { void onSuccess(T data); void onError(String msg); }

    public void register(User u, String password, Callback<User> cb) {
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject()
                        .put("nic", u.nic)
                        .put("name", u.name)
                        .put("email", u.email)
                        .put("phone", u.phone)
                        .put("password", password);

                // Adjust to your C# route & response shape
                JSONObject res = api.post("/auth/register", body, null);

                JSONObject ju = res.getJSONObject("user");
                String access = res.optString("accessToken", null);
                String refresh = res.optString("refreshToken", null);

                User saved = new User();
                saved.nic = ju.getString("nic");
                saved.name = ju.optString("name", "");
                saved.email = ju.optString("email", "");
                saved.phone = ju.optString("phone", "");
                saved.status = ju.optInt("status", 1);

                dao.upsertUser(saved);
                if (access != null) dao.saveTokens(saved.nic, access, refresh);
                cb.onSuccess(saved);
            } catch (Exception e) { cb.onError(e.getMessage()); }
        });
    }

    public void login(String usernameOrNic, String password, Callback<User> cb) {
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject()
                        .put("username", usernameOrNic) // or "nic"/"email" per your API
                        .put("password", password);

                JSONObject res = api.post("/auth/login", body, null);

                JSONObject ju = res.getJSONObject("user");
                String access = res.optString("accessToken", null);
                String refresh = res.optString("refreshToken", null);

                User saved = new User();
                saved.nic = ju.getString("nic");
                saved.name = ju.optString("name", "");
                saved.email = ju.optString("email", "");
                saved.phone = ju.optString("phone", "");
                saved.status = ju.optInt("status", 1);

                dao.upsertUser(saved);
                if (access != null) dao.saveTokens(saved.nic, access, refresh);

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
