package com.example.evynkchargingmobileapp.ui.operator;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.repo.StationOperatorReservationRepository;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

public class StationOperatorUserFormActivity extends AppCompatActivity {

    private final Handler main = new Handler(Looper.getMainLooper());
    private StationOperatorReservationRepository repo;

    private TextView tvUserTitle, tvUserEmail, tvStatus, tvStation;
    private MaterialButton btnComplete;

    private String bookingId;  // set after verify

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_station_operator_user_form);

        repo = new StationOperatorReservationRepository(this);

        tvUserTitle = findViewById(R.id.tvUserTitle);
        tvUserEmail = findViewById(R.id.tvUserEmail);
        tvStatus    = findViewById(R.id.tvStatus);
        tvStation   = findViewById(R.id.tvStation);
        btnComplete = findViewById(R.id.btnStartSession); // reused id
        btnComplete.setText("Complete session");
        btnComplete.setEnabled(false);

        String qrPayload = getIntent().getStringExtra("qr_payload");
        if (qrPayload == null || qrPayload.isEmpty()) {
            Toast.makeText(this, "Invalid QR.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 1) Verify with backend
        repo.verify(qrPayload, new StationOperatorReservationRepository.Result<JSONObject>() {
            @Override public void onSuccess(JSONObject resp) {
                main.post(() -> {
                    try {
                        // resp expected: { message, data: { bookingId, status, ownerNic, stationId, reservationAtUtc, email? } }
                        JSONObject data = resp.optJSONObject("data");
                        if (data == null) data = resp; // fallback if API returns plain object

                        bookingId = firstNonEmpty(
                                data.optString("bookingId", ""),
                                data.optString("id", ""),
                                data.optString("_id", "")
                        );
                        String ownerNic  = firstNonEmpty(
                                data.optString("ownerNic", ""),
                                data.optString("ownerNIC", ""),
                                data.optString("nic", "")
                        );
                        String stationId = firstNonEmpty(
                                data.optString("stationId", ""),
                                data.optString("stationID", "")
                        );
                        String status    = firstNonEmpty(
                                data.optString("status", ""),
                                data.optString("bookingStatus", "")
                        );
                        String email     = firstNonEmpty(
                                data.optString("email", ""),
                                data.optString("ownerEmail", "")
                        );

                        tvUserTitle.setText(!ownerNic.isEmpty() ? ownerNic : "User");
                        tvUserEmail.setText(email);
                        tvStatus.setText("Status: " + (status.isEmpty() ? "-" : status));
                        tvStation.setText("Station: " + (stationId.isEmpty() ? "-" : stationId));

                        btnComplete.setEnabled(bookingId != null && !bookingId.isEmpty());
                    } catch (Exception ex) {
                        Toast.makeText(StationOperatorUserFormActivity.this,
                                "Parse error: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override public void onError(String message) {
                main.post(() -> Toast.makeText(StationOperatorUserFormActivity.this,
                        "Verify failed: " + message, Toast.LENGTH_LONG).show());
            }
        });

        // 2) Complete session
        btnComplete.setOnClickListener(v -> {
            if (bookingId == null || bookingId.isEmpty()) {
                Toast.makeText(this, "No booking to complete.", Toast.LENGTH_SHORT).show();
                return;
            }
            btnComplete.setEnabled(false);

            repo.complete(bookingId, new StationOperatorReservationRepository.Result<JSONObject>() {
                @Override public void onSuccess(JSONObject resp) {
                    main.post(() -> {
                        Toast.makeText(StationOperatorUserFormActivity.this,
                                "Charging session completed.", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }

                @Override public void onError(String message) {
                    main.post(() -> {
                        btnComplete.setEnabled(true);
                        Toast.makeText(StationOperatorUserFormActivity.this,
                                "Complete failed: " + message, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }

    private static String firstNonEmpty(String... xs) {
        if (xs == null) return "";
        for (String x : xs) if (x != null && !x.trim().isEmpty()) return x;
        return "";
    }
}
