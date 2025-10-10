package com.example.evynkchargingmobileapp.repo;

import android.content.Context;
import android.util.Log;

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
        // TODO: set your base URL
        this.api = new ApiClient("http://10.0.2.2:5000/");
    }

    /**
     * POST /api/operator/reservations/verify
     * - If QR is JSON: send it as-is.
     * - If QR is plain text: wrap it into a best-guess DTO ("code": "..."), adjust if backend requires different fields.
     * Returns the entire server JSON so the caller can render it.
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
                    body = new JSONObject(qrPayload); // QR already JSON
                } catch (JSONException ignored) {
                    // Fallback: backend must accept a "code" field (adjust if needed)
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

    /**
     * PATCH /api/operator/reservations/{id}/complete
     */

    public void complete(String bookingId, Result<JSONObject> cb) {
        new Thread(() -> {
            try {
                final String token = StationOperatorPrefs.getToken(ctx);
                if (token == null || token.isEmpty()) {
                    cb.onError("Missing operator token. Please log in again.");
                    return;
                }

                String path = "api/operator/reservations/" + bookingId + "/complete";
                JSONObject resp = api.patch(path, new JSONObject(), token); // This should now work
                Log.d(TAG, "Complete response: " + resp);
                cb.onSuccess(resp);

            } catch (Exception e) {
                Log.e(TAG, "Complete error", e);
                cb.onError(e.getMessage());
            }
        }).start();
    }

}
