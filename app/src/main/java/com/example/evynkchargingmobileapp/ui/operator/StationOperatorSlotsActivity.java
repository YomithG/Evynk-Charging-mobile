package com.example.evynkchargingmobileapp.ui.operator;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.net.ApiClient;
import com.example.evynkchargingmobileapp.util.StationOperatorPrefs;

import org.json.JSONArray;
import org.json.JSONObject;

public class StationOperatorSlotsActivity extends AppCompatActivity {

    private final Handler main = new Handler(Looper.getMainLooper());
    private SwipeRefreshLayout swipe;
    private TextView tvLocation, tvType, tvAvailableSlots;
    private Button btnUpdateSlots;
    private ImageView ivEditSlots; // Edit icon
    private String stationId;  // Store the station ID

    private static final String BASE = "http://10.0.2.2:5000/"; // Ensure this is correct for emulator
    private ApiClient api;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_station_operator_slots);

        api = new ApiClient(BASE);

        swipe = findViewById(R.id.swipe);
        tvLocation = findViewById(R.id.tvLocation);
        tvType = findViewById(R.id.tvType);
        tvAvailableSlots = findViewById(R.id.tvAvailableSlots);
        ivEditSlots = findViewById(R.id.ivEditSlots); // Edit icon

        swipe.setOnRefreshListener(this::loadStation);
        swipe.setRefreshing(true);
        loadStation();

        // Handle the update button click
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

                // Fetch the station data
                JSONArray arr = api.getArray("api/station-data", token);
                JSONObject first = arr.length() > 0 ? arr.getJSONObject(0) : null;
                if (first == null) throw new IllegalStateException("No station data");

                stationId = first.optString("id", "");  // Retrieve and store the station ID
                final String location = first.optString("location", "—");
                final String type = first.optString("type", "—");
                final int slots = first.optInt("availableSlots", 0);

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
        // Create a dialog to edit the available slots
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Available Slots");

        // Create an EditText for input
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(tvAvailableSlots.getText().toString());
        builder.setView(input);

        // Add buttons to the dialog
        builder.setPositiveButton("OK", (dialog, which) -> {
            // Get the new slot value and update the available slots
            int newAvailableSlots = Integer.parseInt(input.getText().toString());
            updateAvailableSlots(newAvailableSlots);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        // Show the dialog
        builder.show();
    }

    private void updateAvailableSlots(int newAvailableSlots) {
        // Prepare the request payload to update the available slots
        JSONObject payload = new JSONObject();
        try {
            payload.put("availableSlots", newAvailableSlots);

            // Log the full URL and stationId before making the request
            String url = BASE + "api/station-data/" + stationId + "/slots";  // Full URL
            Log.d("StationOperator", "Calling URL: " + url);

            // Make the API request to update the slots using PUT
            new Thread(() -> {
                try {
                    final String token = StationOperatorPrefs.getToken(this);
                    if (token == null || token.isEmpty()) {
                        main.post(() -> Toast.makeText(this, "Missing operator token. Please log in again.", Toast.LENGTH_SHORT).show());
                        return;
                    }

                    String path = "api/station-data/" + stationId + "/slots";
                    JSONObject response = api.put(path, payload, token);  // PUT method

                    // Log the response for debugging
                    Log.d("StationOperator", "Response: " + response.toString());

                    // Handle the response
                    main.post(() -> {
                        if (response.has("message")) {
                            Toast.makeText(this, response.optString("message"), Toast.LENGTH_SHORT).show();
                            // Update the UI with new slot count
                            tvAvailableSlots.setText(String.valueOf(newAvailableSlots));
                        }
                    });

                } catch (Exception e) {
                    main.post(() -> Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }).start();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to prepare the request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
