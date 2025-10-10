package com.example.evynkchargingmobileapp.repo;

import android.content.Context;

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
        // Change base URL to your backend root, e.g. "http://10.0.2.2:5000/"
        api = new ApiClient("http://10.0.2.2:5000/");
    }

    public void loginStationOperator(String email, String password, Result<StationOperatorUser> cb) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("password", password);

                JSONObject resp = api.post("api/Auth/login", body, null);

                // Map JSON -> User
                StationOperatorUser user = new StationOperatorUser();
                user.id = resp.optString("id", resp.optString("_id", ""));
                user.email = resp.optString("email", "");
                user.role = resp.optString("role", "StationOperator");
                user.name = resp.optString("name", "");
                user.createdAt = resp.optString("createdAt", "");
                user.updatedAt = resp.optString("updatedAt", "");

                // Optional token if your backend returns it
                String token = resp.optString("token", null);

                cb.onSuccess(user, token);

            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }
}
