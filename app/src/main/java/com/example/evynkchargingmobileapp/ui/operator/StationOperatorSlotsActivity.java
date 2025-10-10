package com.example.evynkchargingmobileapp.ui.operator;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.net.ApiClient;
import com.example.evynkchargingmobileapp.util.StationOperatorPrefs;

import org.json.JSONArray;
import org.json.JSONObject;

public class StationOperatorSlotsActivity extends AppCompatActivity {

    private final Handler main = new Handler(Looper.getMainLooper());
    private ApiClient api;
    private LinearLayout container;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_station_operator_slots);

        api = new ApiClient("http://10.0.2.2:5000/");
        container = findViewById(R.id.slotContainer);

        loadSlots();
    }

    private void loadSlots() {
        new Thread(() -> {
            try {
                // TODO: replace with your real endpoint
                // e.g., GET api/operators/stations/{stationId}/slots  (or mine/slots)
                JSONObject resp = api.get("api/operators/mine/slots", StationOperatorPrefs.getToken(this));
                JSONArray items = resp.optJSONArray("items"); // allow both array or object
                if (items == null) items = new JSONArray().put(resp); // fallback if single

                JSONArray finalItems = items;
                main.post(() -> {
                    container.removeAllViews();
                    for (int i = 0; i < finalItems.length(); i++) {
                        JSONObject s = finalItems.optJSONObject(i);
                        if (s == null) continue;
                        String name = s.optString("name", "Slot " + (i + 1));
                        String status = s.optString("status", "unknown"); // available/occupied

                        TextView tv = new TextView(this);
                        tv.setText(name + " — " + status);
                        tv.setTextColor(0xFFE6EAF0);
                        tv.setTextSize(16);
                        tv.setPadding(16, 12, 16, 12);
                        container.addView(tv);
                    }
                });

            } catch (Exception e) {
                main.post(() -> Toast.makeText(this, "Slots load failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
