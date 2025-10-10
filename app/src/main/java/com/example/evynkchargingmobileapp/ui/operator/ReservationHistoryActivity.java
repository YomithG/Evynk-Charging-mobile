package com.example.evynkchargingmobileapp.ui.operator;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.data.model.Appointment;
import com.example.evynkchargingmobileapp.net.ApiClient;
import com.example.evynkchargingmobileapp.util.StationOperatorPrefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ReservationHistoryActivity extends AppCompatActivity {

    private TextView tvHistoryCount;
    private RecyclerView rvHistory;
    private final List<Appointment> items = new ArrayList<>();
    private ReservationHistoryAdapter adapter;

    private ApiClient api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reservation_history);

        // Base URL from resources (values/urls.xml)
        String baseUrl = getString(R.string.base_url);
        api = new ApiClient(baseUrl);

        tvHistoryCount = findViewById(R.id.tvHistoryCount);
        rvHistory = findViewById(R.id.rvHistory);

        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReservationHistoryAdapter(items);
        rvHistory.setAdapter(adapter);

        fetchHistory();
    }

    private void fetchHistory() {
        new Thread(() -> {
            try {
                String token = StationOperatorPrefs.getToken(this);
                if (token == null || token.isEmpty()) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "Missing operator token. Please log in.", Toast.LENGTH_LONG).show()
                    );
                    return;
                }

                JSONObject response = api.get("api/station-data/reservation-history", token);
                JSONArray data = response.optJSONArray("data");

                items.clear();

                if (data != null) {
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject b = data.optJSONObject(i);
                        if (b == null) continue;
                        items.add(new Appointment(
                                b.optString("_id", ""),
                                b.optString("ownerNic", ""),
                                b.optString("reservationAtUtc", ""),
                                b.optString("status", "")
                        ));
                    }
                }

                runOnUiThread(() -> {
                    tvHistoryCount.setText("Reservations: " + items.size());
                    adapter.notifyDataSetChanged();
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }
}
