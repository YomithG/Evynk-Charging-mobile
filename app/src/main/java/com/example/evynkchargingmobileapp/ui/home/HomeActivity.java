package com.example.evynkchargingmobileapp.ui.home;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.ui.dashboard.DashboardActivity;
import com.google.android.material.appbar.MaterialToolbar;

public class HomeActivity extends AppCompatActivity {

    // FQCN targets (adjust to your actual package names if different)
    private static final String FQCN_FIND_STATION    = "com.example.evynkchargingmobileapp.ui.stations.FindStationActivity";
    private static final String FQCN_MY_RESERVATIONS = "com.example.evynkchargingmobileapp.ui.reservations.OwnerReservationsActivity";
    private static final String FQCN_SCAN_QR         = "com.example.evynkchargingmobileapp.ui.scan.ScanQrActivity";
    private static final String FQCN_SUPPORT         = "com.example.evynkchargingmobileapp.ui.support.SupportActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        setSupportActionBar(topAppBar);

        // Resolve the user's name (Intent extra > SharedPreferences > fallback)
        String fullName = getIntent().getStringExtra("full_name");
        if (fullName == null || fullName.trim().isEmpty()) {
            SharedPreferences sp = getSharedPreferences("user_profile", MODE_PRIVATE);
            fullName = sp.getString("fullName", "User");
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Welcome, " + fullName);
        }

        // Quick actions
        findViewById(R.id.cardFindStation).setOnClickListener(v ->
                launchIfExists(FQCN_FIND_STATION, "Find Station"));

        // Pass JWT to "My reservations"
        findViewById(R.id.cardMyReservations).setOnClickListener(v -> {
            String token = readToken();
            try {
                Class<?> clazz = Class.forName(FQCN_MY_RESERVATIONS);
                Intent intent = new Intent(this, clazz);
                if (token != null && !token.trim().isEmpty()) {
                    intent.putExtra("token", token);
                }
                startActivity(intent);
            } catch (ClassNotFoundException e) {
                Toast.makeText(this, "My Reservations screen not available yet.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Unable to open My Reservations: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        findViewById(R.id.cardScanQr).setOnClickListener(v ->
                launchIfExists(FQCN_SCAN_QR, "Scan QR"));

        findViewById(R.id.cardSupport).setOnClickListener(v -> {
            // If SupportActivity exists, launch it; otherwise open email composer
            if (!launchIfExists(FQCN_SUPPORT, "Support")) {
                Intent email = new Intent(Intent.ACTION_SENDTO);
                email.setData(Uri.parse("mailto:"));
                email.putExtra(Intent.EXTRA_EMAIL, new String[]{"support@evynk.app"});
                email.putExtra(Intent.EXTRA_SUBJECT, "EVynk Support");
                try {
                    startActivity(Intent.createChooser(email, "Contact support"));
                } catch (Exception ex) {
                    Toast.makeText(this, "No email app installed.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_home, menu); // profile icon
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

    /** Try launching activity by FQCN; if not found, show a friendly toast. */
    private boolean launchIfExists(String fqcn, String screenLabel) {
        try {
            Class<?> clazz = Class.forName(fqcn);
            startActivity(new Intent(this, clazz));
            return true;
        } catch (ClassNotFoundException e) {
            Toast.makeText(this, screenLabel + " screen not available yet.", Toast.LENGTH_SHORT).show();
            return false;
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, screenLabel + " not declared in AndroidManifest.xml.", Toast.LENGTH_LONG).show();
            return false;
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open " + screenLabel + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    // in HomeActivity
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
