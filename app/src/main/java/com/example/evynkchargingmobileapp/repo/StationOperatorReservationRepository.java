package com.example.evynkchargingmobileapp.repo;

import android.content.Context;
import android.util.Log;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.net.ApiClient;
import com.example.evynkchargingmobileapp.util.StationOperatorPrefs;

import org.json.JSONException;
import org.json.JSONObject;

public class StationOperatorReservationRepository {

    public interface Result<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    private static final String TAG = "OperatorReservationRepo";

    private final ApiClient api;
    private final Context ctx;

    public StationOperatorReservationRepository(Context ctx) {
        this.ctx = ctx.getApplicationContext();

        // Read base URL from resources and ensure it ends with a slash
        String raw = this.ctx.getString(R.string.base_url);
        String base = ensureTrailingSlash(raw);

        this.api = new ApiClient(base);
    }

    /** POST /api/operator/reservations/verify
     * - If QR is JSON: send it as-is.
     * - If QR is plain text: wrap it into {"code": "..."} (adjust if backend differs).
     * Returns the full server JSON so callers can render it.
     */
    public void verify(String qrPayload, Result<JSONObject> cb) {
        new Thread(() -> {
            try {
                final String token = StationOperatorPrefs.getToken(ctx);
                if (token == null || token.isEmpty()) {
                    cb.onError("Missing operator token. Please log in again.");
                    return;
                }

                JSONObject body;
                try {
                    body = new JSONObject(qrPayload); // QR is already JSON
                } catch (JSONException ignored) {
                    // Fallback structure
                    body = new JSONObject();
                    body.put("code", qrPayload);
                }

                JSONObject resp = api.post("api/operator/reservations/verify", body, token);
                Log.d(TAG, "Verify response: " + resp);
                cb.onSuccess(resp);

            } catch (Exception e) {
                Log.e(TAG, "Verify error", e);
                cb.onError(e.getMessage());
            }
        }).start();
    }

    /** PATCH /api/operator/reservations/{id}/complete */
    public void complete(String bookingId, Result<JSONObject> cb) {
        new Thread(() -> {
            try {
                final String token = StationOperatorPrefs.getToken(ctx);
                if (token == null || token.isEmpty()) {
                    cb.onError("Missing operator token. Please log in again.");
                    return;
                }

                String path = "api/operator/reservations/" + bookingId + "/complete";
                JSONObject resp = api.patch(path, new JSONObject(), token);
                Log.d(TAG, "Complete response: " + resp);
                cb.onSuccess(resp);

            } catch (Exception e) {
                Log.e(TAG, "Complete error", e);
                cb.onError(e.getMessage());
            }
        }).start();
    }

    // ---- helpers ----
    private static String ensureTrailingSlash(String s) {
        if (s == null || s.trim().isEmpty()) return "";
        return s.endsWith("/") ? s : (s + "/");
    }
}
