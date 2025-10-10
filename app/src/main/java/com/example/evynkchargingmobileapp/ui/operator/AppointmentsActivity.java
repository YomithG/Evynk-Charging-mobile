package com.example.evynkchargingmobileapp.ui.operator;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
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

public class AppointmentsActivity extends AppCompatActivity {

    private TextView tvActiveBookingsCount;
    private RecyclerView rvActiveBookings;
    private final List<Appointment> appointmentList = new ArrayList<>();
    private AppointmentsAdapter appointmentsAdapter;
    private ApiClient api;

    private String operatorToken;

    private static final int STATUS_COMPLETED = 3;
    private static final int STATUS_CANCELLED = 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appointments);

        String baseUrl = getString(R.string.base_url);
        api = new ApiClient(baseUrl);

        operatorToken = StationOperatorPrefs.getToken(this);

        tvActiveBookingsCount = findViewById(R.id.tvActiveBookingsCount);
        rvActiveBookings = findViewById(R.id.rvActiveBookings);

        rvActiveBookings.setLayoutManager(new LinearLayoutManager(this));
        appointmentsAdapter = new AppointmentsAdapter(appointmentList, this::showConfirmationDialog);
        rvActiveBookings.setAdapter(appointmentsAdapter);

        fetchActiveAppointments();
    }

    private void showConfirmationDialog(Appointment appointment, int pos, boolean isComplete) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_action, null);
        TextView title = dialogView.findViewById(R.id.dialogTitle);
        TextView msg = dialogView.findViewById(R.id.dialogMessage);
        TextView btnCancel = dialogView.findViewById(R.id.btnCancelDialog);
        TextView btnConfirm = dialogView.findViewById(R.id.btnConfirmDialog);

        title.setText(isComplete ? "Complete appointment?" : "Cancel appointment?");
        msg.setText("Once you do this, the booking will be removed from the active list.");

        AlertDialog d = new AlertDialog.Builder(this, R.style.CustomDialog)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnCancel.setOnClickListener(v -> d.dismiss());
        btnConfirm.setOnClickListener(v -> {
            d.dismiss();
            updateBookingStatus(appointment.getId(), isComplete ? STATUS_COMPLETED : STATUS_CANCELLED, pos);
        });

        d.show();
    }

    private void fetchActiveAppointments() {
        new Thread(() -> {
            try {
                JSONObject response = api.get("api/station-data/active-bookings", operatorToken);
                JSONArray data = response.optJSONArray("data");

                appointmentList.clear();

                if (data != null) {
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject booking = data.getJSONObject(i);
                        appointmentList.add(new Appointment(
                                booking.optString("_id"),
                                booking.optString("ownerNic"),
                                booking.optString("reservationAtUtc"),
                                booking.optString("status")
                        ));
                    }
                }

                runOnUiThread(() -> {
                    tvActiveBookingsCount.setText("Active Appointments: " + appointmentList.size());
                    appointmentsAdapter.notifyDataSetChanged();
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    private void updateBookingStatus(String bookingId, int newStatus, int pos) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject().put("status", newStatus);
                api.put("api/station-data/booking/" + bookingId + "/status", body, operatorToken);

                runOnUiThread(() -> {
                    appointmentsAdapter.removeAt(pos);
                    tvActiveBookingsCount.setText("Active Appointments: " + appointmentList.size());
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Action failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }
}
