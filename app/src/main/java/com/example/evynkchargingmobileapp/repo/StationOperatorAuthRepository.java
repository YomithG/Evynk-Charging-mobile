package com.example.evynkchargingmobileapp.repo;

import android.content.Context;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.data.model.StationOperatorUser;
import com.example.evynkchargingmobileapp.net.ApiClient;

import org.json.JSONObject;

public class StationOperatorAuthRepository {

    public interface Result<T> {
        void onSuccess(T data, String tokenOrNull);
        void onError(String message);
    }

    private final ApiClient api;

    public StationOperatorAuthRepository(Context ctx) {
        Context app = ctx.getApplicationContext();
        String raw = app.getString(R.string.base_url);
        this.api = new ApiClient(ensureTrailingSlash(raw));
    }

    public void loginStationOperator(String email, String password, Result<StationOperatorUser> cb) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject()
                        .put("email", email)
                        .put("password", password);

                // Adjust the path if your backend differs
                JSONObject resp = api.post("api/Auth/login", body, null);

                // Map JSON -> User
                StationOperatorUser user = new StationOperatorUser();
                user.id        = resp.optString("id", resp.optString("_id", ""));
                user.email     = resp.optString("email", "");
                user.role      = resp.optString("role", "StationOperator");
                user.name      = resp.optString("name", "");
                user.createdAt = resp.optString("createdAt", "");
                user.updatedAt = resp.optString("updatedAt", "");

                // Try common token field names
                String token = firstNonEmpty(
                        resp.optString("accessToken", null),
                        resp.optString("token", null),
                        resp.optString("jwt", null)
                );

                cb.onSuccess(user, token);

            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }

    // ---- helpers ----
    private static String ensureTrailingSlash(String s) {
        if (s == null || s.trim().isEmpty()) return "";
        return s.endsWith("/") ? s : (s + "/");
    }

    private static String firstNonEmpty(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (v != null && !v.trim().isEmpty() && !"null".equalsIgnoreCase(v)) return v.trim();
        }
        return null;
    }
}
