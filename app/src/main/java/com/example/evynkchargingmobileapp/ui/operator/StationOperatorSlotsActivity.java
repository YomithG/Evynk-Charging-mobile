package com.example.evynkchargingmobileapp.ui.operator;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.net.ApiClient;
import com.example.evynkchargingmobileapp.util.StationOperatorPrefs;

import org.json.JSONArray;
import org.json.JSONObject;

public class StationOperatorSlotsActivity extends AppCompatActivity {

    private final Handler main = new Handler(Looper.getMainLooper());
    private SwipeRefreshLayout swipe;
    private TextView tvLocation, tvType, tvAvailableSlots;
    private ImageView ivEditSlots;

    private String stationId;   // server id for this station
    private ApiClient api;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_station_operator_slots);

        // ✅ Base URL from resources (values/urls.xml)
        String baseUrl = getString(R.string.base_url);
        api = new ApiClient(baseUrl);

        swipe = findViewById(R.id.swipe);
        tvLocation = findViewById(R.id.tvLocation);
        tvType = findViewById(R.id.tvType);
        tvAvailableSlots = findViewById(R.id.tvAvailableSlots);
        ivEditSlots = findViewById(R.id.ivEditSlots);

        swipe.setOnRefreshListener(this::loadStation);
        swipe.setRefreshing(true);
        loadStation();

        ivEditSlots.setOnClickListener(v -> showEditDialog());
    }

    private void loadStation() {
        new Thread(() -> {
            try {
                final String token = StationOperatorPrefs.getToken(this);
                if (token == null || token.isEmpty()) {
                    main.post(() -> {
                        swipe.setRefreshing(false);
                        Toast.makeText(this, "Missing operator token. Please log in again.", Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                // GET /api/station-data → expect an array; take the first station
                JSONArray arr = api.getArray("api/station-data", token);
                JSONObject first = (arr != null && arr.length() > 0) ? arr.getJSONObject(0) : null;
                if (first == null) throw new IllegalStateException("No station data");

                // Some APIs send "_id", others "id" — handle both safely
                stationId = first.optString("_id", first.optString("id", ""));

                final String location = first.optString("location", "—");
                final String type = first.optString("type", "—");
                // prefer "availableSlots"; fallback to "slots" if your API uses that
                final int slots = first.has("availableSlots")
                        ? first.optInt("availableSlots", 0)
                        : first.optInt("slots", 0);

                main.post(() -> {
                    tvLocation.setText("Location: " + location);
                    tvType.setText("Type: " + type);
                    tvAvailableSlots.setText(String.valueOf(slots));
                    swipe.setRefreshing(false);
                });

            } catch (Exception e) {
                main.post(() -> {
                    swipe.setRefreshing(false);
                    Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showEditDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Edit Available Slots");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(tvAvailableSlots.getText().toString());
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String raw = input.getText() != null ? input.getText().toString().trim() : "";
            if (raw.isEmpty()) {
                Toast.makeText(this, "Please enter a number.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                int newAvailableSlots = Integer.parseInt(raw);
                if (newAvailableSlots < 0) {
                    Toast.makeText(this, "Value cannot be negative.", Toast.LENGTH_SHORT).show();
                    return;
                }
                updateAvailableSlots(newAvailableSlots);
            } catch (NumberFormatException nfe) {
                Toast.makeText(this, "Invalid number.", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void updateAvailableSlots(int newAvailableSlots) {
        try {
            JSONObject payload = new JSONObject().put("availableSlots", newAvailableSlots);

            new Thread(() -> {
                try {
                    final String token = StationOperatorPrefs.getToken(this);
                    if (token == null || token.isEmpty()) {
                        main.post(() -> Toast.makeText(this, "Missing operator token. Please log in again.", Toast.LENGTH_SHORT).show());
                        return;
                    }
                    if (stationId == null || stationId.isEmpty()) {
                        main.post(() -> Toast.makeText(this, "Station ID missing. Refresh and try again.", Toast.LENGTH_SHORT).show());
                        return;
                    }

                    // PUT /api/station-data/{stationId}/slots
                    String path = "api/station-data/" + stationId + "/slots";
                    JSONObject response = api.put(path, payload, token);

                    Log.d("StationOperator", "PUT " + path + " → " + response);

                    main.post(() -> {
                        if (response != null && response.has("message")) {
                            Toast.makeText(this, response.optString("message"), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Updated.", Toast.LENGTH_SHORT).show();
                        }
                        // Update UI count after success
                        tvAvailableSlots.setText(String.valueOf(newAvailableSlots));
                    });

                } catch (Exception e) {
                    main.post(() -> Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }).start();

        } catch (Exception e) {
            Toast.makeText(this, "Failed to prepare the request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
