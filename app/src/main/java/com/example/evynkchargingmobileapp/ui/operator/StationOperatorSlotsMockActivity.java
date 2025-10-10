package com.example.evynkchargingmobileapp.ui.operator;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.evynkchargingmobileapp.R;

import java.util.ArrayList;
import java.util.List;

public class StationOperatorSlotsMockActivity extends AppCompatActivity {

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_station_operator_slots_mock);

        RecyclerView rv = findViewById(R.id.rvSlots);
        rv.setLayoutManager(new LinearLayoutManager(this));

        // Hard-coded demo data
        List<SlotItem> slots = new ArrayList<>();
        slots.add(new SlotItem("A1", "Available", "CCS2", "60 kW", "Now"));
        slots.add(new SlotItem("A2", "Occupied",  "CCS2", "60 kW", "1 min ago"));
        slots.add(new SlotItem("B1", "Available", "Type2", "22 kW", "Now"));
        slots.add(new SlotItem("B2", "Offline",   "CHAdeMO", "50 kW", "10 min ago"));
        slots.add(new SlotItem("C1", "Occupied",  "Type2", "11 kW", "3 min ago"));

        rv.setAdapter(new SlotAdapter(slots));
    }

    // --- Simple model for this mock page ---
    public static class SlotItem {
        public final String name;
        public final String status;       // Available | Occupied | Offline
        public final String connector;    // CCS2 / Type2 / CHAdeMO...
        public final String power;        // e.g., 60 kW
        public final String updated;      // e.g., "Now"

        public SlotItem(String name, String status, String connector, String power, String updated) {
            this.name = name;
            this.status = status;
            this.connector = connector;
            this.power = power;
            this.updated = updated;
        }
    }
}
