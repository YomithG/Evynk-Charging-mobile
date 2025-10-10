package com.example.evynkchargingmobileapp.ui.operator;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.util.StationOperatorPrefs;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

public class StationOperatorHomeActivity extends AppCompatActivity {

    private static final int REQ_SCAN = 2001;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_station_operator_home);

        // Guard: ensure operator is logged in
        if (StationOperatorPrefs.getId(this) == null) {
            Toast.makeText(this, "Please log in as station operator.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Toolbar actions
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_logout) {
                StationOperatorPrefs.clear(this);
                Toast.makeText(this, "Logged out.", Toast.LENGTH_SHORT).show();
                finish();
                return true;
            }
            return false;
        });

        // Scan QR
        MaterialButton btnScan = findViewById(R.id.btnScanQr);
        btnScan.setOnClickListener(v -> launchScan());

        // View station slots
        MaterialButton btnSlots = findViewById(R.id.btnViewSlots);
        btnSlots.setOnClickListener(v ->
                startActivity(new Intent(this, StationOperatorSlotsActivity.class)));

        // View Appointments (active)
        MaterialButton btnAppointments = findViewById(R.id.btnViewAppointments);
        btnAppointments.setOnClickListener(v ->
                startActivity(new Intent(this, AppointmentsActivity.class)));

        // View Reservation History (completed + cancelled)
        MaterialButton btnHistory = findViewById(R.id.btnViewHistory);
        if (btnHistory != null) {
            btnHistory.setOnClickListener(v ->
                    startActivity(new Intent(this, ReservationHistoryActivity.class)));
        }
    }

    private void launchScan() {
        Intent i = new Intent(this, StationOperatorScanQrActivity.class);
        startActivityForResult(i, REQ_SCAN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SCAN && resultCode == RESULT_OK && data != null) {
            String qr = data.getStringExtra("qr_text");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                if (qr == null || qr.isEmpty()) {
                    Toast.makeText(this, "QR not recognized.", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            Intent form = new Intent(this, StationOperatorUserFormActivity.class);
            form.putExtra("qr_payload", qr);
            startActivity(form);
        }
    }
}
