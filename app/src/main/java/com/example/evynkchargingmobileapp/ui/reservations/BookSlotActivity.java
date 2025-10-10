package com.example.evynkchargingmobileapp.ui.reservations;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.data.db.UserDao;
import com.example.evynkchargingmobileapp.util.Prefs;
import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.Date;

public class BookSlotActivity extends AppCompatActivity {

    private static final String TAG = "BookSlotActivity";

    // Paths only — base comes from strings.xml
    private static final String CREATE_RESERVATION_PATH = "/api/owner/reservations";
    private static final String LIST_STATIONS_PATH      = "/api/public/stations";

    // UI
    private EditText edtStationDisplay;  // read-only, shows "Location • AC/DC"
    private TextView txtDate, txtTime;
    private Button btnPickDate, btnPickTime, btnPickStation, btnSubmit;
    private ProgressBar progress;

    // Summary UI (live “Booking Summary” card)
    private TextView summaryStation, summaryDate, summaryTime;

    // State
    private String selectedStationId = null;     // used for submission only
    private String selectedStationLabel = null;  // UI text only
    private String apiBase;                      // normalized base URL from strings.xml

    private final Calendar localCal = Calendar.getInstance();
    private final Handler main = new Handler(Looper.getMainLooper());

    private static final Pattern OBJECT_ID = Pattern.compile("^[0-9a-fA-F]{24}$");

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_slot);

        MaterialToolbar top = findViewById(R.id.topAppBar);
        if (top != null) {
            top.setTitle("Book reservation");
            setSupportActionBar(top);
            if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            top.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        }

        // Base URL from resources (values/urls.xml) and normalize (remove trailing '/')
        apiBase = stripTrailingSlash(getString(R.string.base_url));

        // Bind
        edtStationDisplay = findViewById(R.id.edtStationDisplay);
        txtDate           = findViewById(R.id.txtDate);
        txtTime           = findViewById(R.id.txtTime);
        btnPickDate       = findViewById(R.id.btnPickDate);
        btnPickTime       = findViewById(R.id.btnPickTime);
        btnPickStation    = findViewById(R.id.btnPickStation);
        btnSubmit         = findViewById(R.id.btnSubmit);
        progress          = findViewById(R.id.progress);

        // Summary binds
        summaryStation    = findViewById(R.id.summaryStation);
        summaryDate       = findViewById(R.id.summaryDate);
        summaryTime       = findViewById(R.id.summaryTime);

        // Defaults
        roundCalToNext30Min(localCal);
        refreshDateTimeLabels();
        updateSummary();                    // ensure card shows initial values
        setSubmitEnabled(false);            // disabled until station chosen

        // Clicks
        btnPickDate.setOnClickListener(v -> showDatePicker());
        btnPickTime.setOnClickListener(v -> showTimePicker());
        btnPickStation.setOnClickListener(v -> fetchAndShowActiveStations());
        edtStationDisplay.setOnClickListener(v -> fetchAndShowActiveStations()); // tap field to pick
        btnSubmit.setOnClickListener(v -> doSubmit());
    }

    // ---------- Pickers ----------
    private void showDatePicker() {
        new DatePickerDialog(
                this,
                (view, y, m, d) -> {
                    localCal.set(Calendar.YEAR, y);
                    localCal.set(Calendar.MONTH, m);
                    localCal.set(Calendar.DAY_OF_MONTH, d);
                    refreshDateTimeLabels();
                    updateSummary();
                },
                localCal.get(Calendar.YEAR),
                localCal.get(Calendar.MONTH),
                localCal.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void showTimePicker() {
        new TimePickerDialog(
                this,
                (view, h, min) -> {
                    localCal.set(Calendar.HOUR_OF_DAY, h);
                    localCal.set(Calendar.MINUTE, min);
                    localCal.set(Calendar.SECOND, 0);
                    refreshDateTimeLabels();
                    updateSummary();
                },
                localCal.get(Calendar.HOUR_OF_DAY),
                localCal.get(Calendar.MINUTE),
                true
        ).show();
    }

    private void refreshDateTimeLabels() {
        SimpleDateFormat d = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());
        SimpleDateFormat t = new SimpleDateFormat("HH:mm", Locale.getDefault());
        txtDate.setText(d.format(localCal.getTime()));
        txtTime.setText(t.format(localCal.getTime()));
    }

    private static void roundCalToNext30Min(Calendar c) {
        int minutes = c.get(Calendar.MINUTE);
        int add = (minutes % 30 == 0) ? 0 : (30 - (minutes % 30));
        c.add(Calendar.MINUTE, add);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    // ---------- Booking Summary helpers ----------
    private void updateSummary() {
        // Station
        summaryStation.setText((TextUtils.isEmpty(selectedStationLabel) ? "—" : selectedStationLabel));

        // Date & Time
        SimpleDateFormat d = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());
        SimpleDateFormat t = new SimpleDateFormat("HH:mm", Locale.getDefault());
        summaryDate.setText(d.format(localCal.getTime()));
        summaryTime.setText(t.format(localCal.getTime()));
    }

    private void setSubmitEnabled(boolean enabled) {
        btnSubmit.setEnabled(enabled);
        btnSubmit.setAlpha(enabled ? 1f : 0.5f);
    }

    // ---------- Submit ----------
    private void doSubmit() {
        if (TextUtils.isEmpty(selectedStationId)) {
            edtStationDisplay.setError("Please pick a station");
            edtStationDisplay.requestFocus();
            return;
        }
        if (!OBJECT_ID.matcher(selectedStationId).matches()) {
            edtStationDisplay.setError("Invalid station (id format)");
            return;
        }

        // Local time with offset (backend expects this)
        localCal.set(Calendar.SECOND, 0);
        SimpleDateFormat isoOffset = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        isoOffset.setTimeZone(TimeZone.getDefault());
        final String reservationAt = isoOffset.format(localCal.getTime());

        final String token = getToken();
        if (TextUtils.isEmpty(token)) {
            Toast.makeText(this, "Not authenticated (missing token).", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(apiBase + CREATE_RESERVATION_PATH);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);

                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");

                JSONObject body = new JSONObject();
                body.put("stationId", selectedStationId);   // submit ID only
                body.put("reservationAt", reservationAt);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes("UTF-8"));
                    os.flush();
                }

                int code = conn.getResponseCode();
                String resp = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());

                Log.d(TAG, "POST " + CREATE_RESERVATION_PATH + " -> " + code + " | " + resp);

                runOnUiThread(() -> {
                    setLoading(false);
                    if (code >= 200 && code < 300) {
                        Toast.makeText(this, "Reservation booked ✅", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        String message = tryExtractMessage(resp);
                        Toast.makeText(this, "Failed (" + code + "): " + (message != null ? message : safeSlice(resp)), Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception ex) {
                Log.e(TAG, "Network error", ex);
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(this, "Network error: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    // ---------- Station picker ----------
    /**
     * Calls read-only /api/public/stations and shows a chooser.
     * UI label is strictly "Location • AC|DC" (no address, no id).
     */
    private void fetchAndShowActiveStations() {
        final String token = getToken();
        if (TextUtils.isEmpty(token)) {
            Toast.makeText(this, "Not authenticated (missing token).", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(apiBase + LIST_STATIONS_PATH);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                String resp = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());

                if (code < 200 || code >= 300) {
                    String msg = tryExtractMessage(resp);
                    throw new IOException("HTTP " + code + (msg != null ? " - " + msg : ""));
                }

                JSONObject root = new JSONObject(resp);
                JSONArray data = root.optJSONArray("data");

                final List<String> labels = new ArrayList<>();
                final List<String> ids = new ArrayList<>();

                if (data != null) {
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject o = data.optJSONObject(i);
                        if (o == null) continue;

                        String id = o.optString("id", "").trim();
                        if (TextUtils.isEmpty(id)) continue;

                        // Build label = "<Location> • <AC|DC>"
                        String loc  = o.optString("location", "").trim();
                        String type = o.optString("type", "").trim().toUpperCase(Locale.US);

                        String label = (loc.isEmpty() ? "(Unnamed)" : loc);
                        if (!TextUtils.isEmpty(type)) label += " • " + type;

                        // No address, no id in label
                        labels.add(label);
                        ids.add(id);
                    }
                }

                runOnUiThread(() -> {
                    setLoading(false);

                    if (ids.isEmpty()) {
                        Toast.makeText(this, "No active stations found.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("Select a charging station")
                            .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                                selectedStationId = ids.get(which);           // store ID (hidden)
                                selectedStationLabel = labels.get(which);     // store label
                                edtStationDisplay.setText(selectedStationLabel); // show "Location • Type"
                                edtStationDisplay.setError(null);
                                updateSummary();
                                setSubmitEnabled(true);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });

            } catch (Exception ex) {
                Log.e(TAG, "fetch stations error", ex);
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(this, "Failed to load stations: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    // ---------- Utils ----------
    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        boolean enable = !loading;
        btnSubmit.setEnabled(enable && !TextUtils.isEmpty(selectedStationId));
        btnPickDate.setEnabled(enable);
        btnPickTime.setEnabled(enable);
        btnPickStation.setEnabled(enable);
        edtStationDisplay.setEnabled(enable);
        if (!enable) btnSubmit.setAlpha(0.5f);
        else setSubmitEnabled(!TextUtils.isEmpty(selectedStationId));
    }

    private @Nullable String getToken() {
        String fromIntent = getIntent().getStringExtra("token");
        if (!TextUtils.isEmpty(fromIntent)) return fromIntent;

        String nic = Prefs.getCurrentNic(this);
        if (!TextUtils.isEmpty(nic)) {
            UserDao dao = new UserDao(getApplicationContext());
            String t = dao.getAccessToken(nic);
            if (!TextUtils.isEmpty(t)) return t;
        }
        return getSharedPreferences("auth", MODE_PRIVATE).getString("token", null);
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private static String safeSlice(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    private static @Nullable String tryExtractMessage(String json) {
        try {
            JSONObject o = new JSONObject(json);
            String m = o.optString("message", null);
            if (!TextUtils.isEmpty(m)) return m;
        } catch (Exception ignored) {}
        return null;
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
