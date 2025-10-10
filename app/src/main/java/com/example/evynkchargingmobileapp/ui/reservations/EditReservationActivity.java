package com.example.evynkchargingmobileapp.ui.reservations;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.data.db.UserDao;
import com.example.evynkchargingmobileapp.util.Prefs;
import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

public class EditReservationActivity extends AppCompatActivity {

    private static final String TAG = "EditReservation";
    private static final String API_BASE = "http://10.0.2.2:5000";

    // UI
    private EditText edtStationDisplay;
    private TextView txtDate, txtTime;
    private Button btnPickStation, btnPickDate, btnPickTime, btnSave, btnCancel;
    private ProgressBar progress;

    // State
    private String reservationId;
    private String selectedStationId;     // id to submit
    private String selectedStationLabel;  // pretty label
    private final Calendar cal = Calendar.getInstance();

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_reservation);

        MaterialToolbar top = findViewById(R.id.topAppBar);
        if (top != null) {
            top.setTitle("Edit reservation");
            setSupportActionBar(top);
            if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            top.setNavigationOnClickListener(v -> onBackPressed());
        }

        // Bind
        edtStationDisplay = findViewById(R.id.edtStationDisplay);
        txtDate = findViewById(R.id.txtDate);
        txtTime = findViewById(R.id.txtTime);
        btnPickStation = findViewById(R.id.btnPickStation);
        btnPickDate = findViewById(R.id.btnPickDate);
        btnPickTime = findViewById(R.id.btnPickTime);
        btnSave = findViewById(R.id.btnSave);
        btnCancel = findViewById(R.id.btnCancel);
        progress = findViewById(R.id.progress);

        // Prefill from extras
        reservationId = getIntent().getStringExtra("id");
        selectedStationId = getIntent().getStringExtra("stationId");
        selectedStationLabel = getIntent().getStringExtra("stationName"); // nice to show

        String at = getIntent().getStringExtra("reservationAtUtc");
        parseInitialTime(at);

        if (!TextUtils.isEmpty(selectedStationLabel)) {
            edtStationDisplay.setText(selectedStationLabel);
        }

        refreshDateTimeLabels();

        // Events
        btnPickStation.setOnClickListener(v -> fetchAndPickStation());
        edtStationDisplay.setOnClickListener(v -> fetchAndPickStation());
        btnPickDate.setOnClickListener(v -> new DatePickerDialog(this,
                (view, y, m, d) -> { cal.set(y, m, d); refreshDateTimeLabels(); },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show());

        btnPickTime.setOnClickListener(v -> new TimePickerDialog(this,
                (view, h, min) -> { cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min); cal.set(Calendar.SECOND, 0); refreshDateTimeLabels(); },
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show());

        btnSave.setOnClickListener(v -> doUpdate());
        btnCancel.setOnClickListener(v -> finish());
    }

    private void parseInitialTime(String serverIso) {
        if (TextUtils.isEmpty(serverIso)) return;
        try {
            SimpleDateFormat inOffs = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
            SimpleDateFormat inZ = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            inZ.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d;
            try { d = inOffs.parse(serverIso); } catch (Exception e) { d = inZ.parse(serverIso); }
            if (d != null) cal.setTime(d);
        } catch (Exception ignored) {}
    }

    private void refreshDateTimeLabels() {
        SimpleDateFormat d = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());
        SimpleDateFormat t = new SimpleDateFormat("HH:mm", Locale.getDefault());
        txtDate.setText(d.format(cal.getTime()));
        txtTime.setText(t.format(cal.getTime()));
    }

    // ---- Station Picker (User/Public) ----
    private void fetchAndPickStation() {
        final String token = getToken();
        if (TextUtils.isEmpty(token)) { toast("Not authenticated."); return; }
        setLoading(true);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(API_BASE + "/api/public/stations");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Accept", "application/json");
                int code = conn.getResponseCode();

                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream()
                ));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                if (code < 200 || code >= 300) throw new IOException(sb.toString());

                JSONObject root = new JSONObject(sb.toString());
                JSONArray data = root.optJSONArray("data");

                final List<String> labels = new ArrayList<>();
                final List<String> ids = new ArrayList<>();
                if (data != null) {
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject o = data.optJSONObject(i);
                        if (o == null) continue;
                        String id = o.optString("id", "");
                        if (TextUtils.isEmpty(id)) continue;

                        String loc = o.optString("location", "").trim();
                        String type = o.optString("type", "").trim().toUpperCase(Locale.US);
                        String label = loc.isEmpty() ? "(Unnamed)" : loc;
                        if (!TextUtils.isEmpty(type)) label += " • " + type; // AC / DC only

                        ids.add(id);
                        labels.add(label);
                    }
                }

                runOnUiThread(() -> {
                    setLoading(false);
                    if (ids.isEmpty()) { toast("No active stations found."); return; }
                    new AlertDialog.Builder(this)
                            .setTitle("Select station")
                            .setItems(labels.toArray(new String[0]), (d, which) -> {
                                selectedStationId = ids.get(which);
                                selectedStationLabel = labels.get(which);
                                edtStationDisplay.setText(selectedStationLabel);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
            } catch (Exception ex) {
                Log.e(TAG, "stations error", ex);
                runOnUiThread(() -> { setLoading(false); toast("Failed to load stations"); });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    // ---- Save (PUT /api/owner/reservations/{id}) ----
    private void doUpdate() {
        if (TextUtils.isEmpty(reservationId)) { toast("Missing reservation id."); return; }
        if (TextUtils.isEmpty(selectedStationId)) { edtStationDisplay.setError("Pick a station"); return; }

        // Use UTC 'Z' – matches your Postman format
        SimpleDateFormat isoUtcZ = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        isoUtcZ.setTimeZone(TimeZone.getTimeZone("UTC"));
        final String reservationAt = isoUtcZ.format(cal.getTime());

        final String token = getToken();
        if (TextUtils.isEmpty(token)) { toast("Not authenticated."); return; }

        setLoading(true);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(API_BASE + "/api/owner/reservations/" + reservationId.trim());
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PUT");
                conn.setDoOutput(true);
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");

                JSONObject body = new JSONObject();
                body.put("stationId", selectedStationId);
                body.put("reservationAt", reservationAt);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes("UTF-8"));
                }

                int code = conn.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream()
                ));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                Log.d(TAG, "PUT " + code + " " + sb);

                runOnUiThread(() -> {
                    setLoading(false);
                    if (code >= 200 && code < 300) {
                        toast("Reservation updated");
                        finish(); // return to list; onResume() will refresh
                    } else {
                        String msg = tryMsg(sb.toString());
                        toast("Update failed (" + code + "): " + (msg != null ? msg : sb));
                    }
                });
            } catch (Exception ex) {
                Log.e(TAG, "update error", ex);
                runOnUiThread(() -> { setLoading(false); toast("Update error: " + ex.getMessage()); });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    // ---- utils ----
    private void setLoading(boolean b) {
        progress.setVisibility(b ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!b);
        btnCancel.setEnabled(!b);
        btnPickDate.setEnabled(!b);
        btnPickTime.setEnabled(!b);
        btnPickStation.setEnabled(!b);
        edtStationDisplay.setEnabled(!b);
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    private String getToken() {
        String nic = Prefs.getCurrentNic(this);
        if (!TextUtils.isEmpty(nic)) {
            UserDao dao = new UserDao(getApplicationContext());
            String t = dao.getAccessToken(nic);
            if (!TextUtils.isEmpty(t)) return t;
        }
        return getSharedPreferences("auth", MODE_PRIVATE).getString("token", null);
    }

    private @Nullable String tryMsg(String json) {
        try { return new JSONObject(json).optString("message", null); } catch (Exception ignore) { return null; }
    }
}
