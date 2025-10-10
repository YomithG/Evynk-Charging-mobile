package com.example.evynkchargingmobileapp.ui.home;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.ui.dashboard.DashboardActivity;
import com.google.android.material.appbar.MaterialToolbar;

public class HomeActivity extends AppCompatActivity {

    private static final String FQCN_FIND_STATION     = "com.example.evynkchargingmobileapp.ui.stations.FindStationActivity";
    private static final String FQCN_MY_RESERVATIONS  = "com.example.evynkchargingmobileapp.ui.reservations.OwnerReservationsActivity";
    private static final String FQCN_BOOK_RESERVATION = "com.example.evynkchargingmobileapp.ui.reservations.BookSlotActivity";
    private static final String FQCN_BOOKING_HISTORY  = "com.example.evynkchargingmobileapp.ui.reservations.BookingHistoryActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        if (topAppBar != null) setSupportActionBar(topAppBar);

        String fullName = getIntent().getStringExtra("full_name");
        if (fullName == null || fullName.trim().isEmpty()) {
            SharedPreferences sp = getSharedPreferences("user_profile", MODE_PRIVATE);
            fullName = sp.getString("fullName", "User");
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Welcome to EVynk");
        }

        // Quick action cards (IDs kept the same)
        setClickIfPresent(R.id.cardFindStation, v -> launchIfExists(FQCN_FIND_STATION, "Find Station"));

        setClickIfPresent(R.id.cardBookSlot, v -> {
            String token = readToken();
            try {
                Class<?> clazz = Class.forName(FQCN_BOOK_RESERVATION);
                Intent intent = new Intent(this, clazz);
                if (token != null && !token.trim().isEmpty()) intent.putExtra("token", token);
                startActivity(intent);
            } catch (ClassNotFoundException e) {
                Toast.makeText(this, "Book Reservation screen not available yet.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Unable to open Book Reservation: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        setClickIfPresent(R.id.cardMyReservations, v -> {
            String token = readToken();
            try {
                Class<?> clazz = Class.forName(FQCN_MY_RESERVATIONS);
                Intent intent = new Intent(this, clazz);
                if (token != null && !token.trim().isEmpty()) intent.putExtra("token", token);
                startActivity(intent);
            } catch (ClassNotFoundException e) {
                Toast.makeText(this, "My Reservations screen not available yet.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Unable to open My Reservations: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        setClickIfPresent(R.id.cardBookingHistory, v -> {
            String token = readToken();
            try {
                Class<?> clazz = Class.forName(FQCN_BOOKING_HISTORY);
                Intent intent = new Intent(this, clazz);
                if (token != null && !token.trim().isEmpty()) intent.putExtra("token", token);
                startActivity(intent);
            } catch (ClassNotFoundException e) {
                Toast.makeText(this, "Booking History screen not available yet.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Unable to open Booking History: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_profile) {
            startActivity(new Intent(this, DashboardActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setClickIfPresent(int viewId, View.OnClickListener l) {
        View v = findViewById(viewId);
        if (v != null) v.setOnClickListener(l);
    }

    private boolean launchIfExists(String fqcn, String screenLabel) {
        try {
            Class<?> clazz = Class.forName(fqcn);
            startActivity(new Intent(this, clazz));
            return true;
        } catch (ClassNotFoundException e) {
            Toast.makeText(this, screenLabel + " screen not available yet.", Toast.LENGTH_SHORT).show();
            return false;
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open " + screenLabel + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private @Nullable String readToken() {
        String nic = com.example.evynkchargingmobileapp.util.Prefs.getCurrentNic(this);
        if (nic != null && !nic.trim().isEmpty()) {
            com.example.evynkchargingmobileapp.data.db.UserDao dao =
                    new com.example.evynkchargingmobileapp.data.db.UserDao(getApplicationContext());
            return dao.getAccessToken(nic);
        }
        return null;
    }
}
