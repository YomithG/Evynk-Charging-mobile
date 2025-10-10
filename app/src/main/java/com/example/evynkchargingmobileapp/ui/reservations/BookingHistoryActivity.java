package com.example.evynkchargingmobileapp.ui.reservations;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.data.db.UserDao;
import com.example.evynkchargingmobileapp.util.Prefs;
import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class BookingHistoryActivity extends AppCompatActivity {

    private static final String TAG = "BookingHistory";

    // Path only — base comes from strings.xml
    private static final String HISTORY_PATH = "/api/owner/reservations/upcoming";

    private RecyclerView recycler;
    private TextView emptyView;
    private ProgressBar progress;
    private HistoryAdapter adapter;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_history);

        MaterialToolbar top = findViewById(R.id.topAppBar);
        setSupportActionBar(top);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        top.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        recycler = findViewById(R.id.recyclerHistory);
        emptyView = findViewById(R.id.emptyView);
        progress = findViewById(R.id.progress);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter();
        recycler.setAdapter(adapter);

        fetchHistory();
    }

    @Override protected void onResume() {
        super.onResume();
        fetchHistory();
    }

    private void fetchHistory() {
        final String token = getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Not authenticated (missing token).", Toast.LENGTH_SHORT).show();
            showState(true, false);
            return;
        }

        // Base URL from resources (values/urls.xml)
        final String baseFromRes = getString(R.string.base_url);
        final String API_BASE = stripTrailingSlash(baseFromRes); // normalize

        showState(false, true);
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(API_BASE + HISTORY_PATH); // safe join because HISTORY_PATH starts with '/'
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream()
                ));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                if (code >= 200 && code < 300) {
                    List<BookingItem> items = parseList(sb.toString());
                    runOnUiThread(() -> {
                        adapter.submit(items);
                        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                        recycler.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
                        progress.setVisibility(View.GONE);
                    });
                } else {
                    Log.e(TAG, "HTTP " + code + " body=" + sb);
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Failed to load history (" + code + ")", Toast.LENGTH_SHORT).show();
                        showState(true, false);
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Network error", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    showState(true, false);
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private void showState(boolean empty, boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        recycler.setVisibility(!loading && !empty ? View.VISIBLE : View.GONE);
        emptyView.setVisibility(!loading && empty ? View.VISIBLE : View.GONE);
    }

    private @Nullable String getToken() {
        String fromIntent = getIntent().getStringExtra("token");
        if (fromIntent != null && !fromIntent.trim().isEmpty()) return fromIntent;

        String nic = Prefs.getCurrentNic(this);
        if (nic != null && !nic.trim().isEmpty()) {
            UserDao dao = new UserDao(getApplicationContext());
            String t = dao.getAccessToken(nic);
            if (t != null && !t.trim().isEmpty()) return t;
        }
        return getSharedPreferences("auth", MODE_PRIVATE).getString("token", null);
    }

    private List<BookingItem> parseList(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray data = root.optJSONArray("data");
        List<BookingItem> list = new ArrayList<>();
        if (data == null) return list;

        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.getJSONObject(i);

            // Accept either a dedicated /completed endpoint OR a mixed list.
            String status = o.optString("status", "");
            if (!"Completed".equalsIgnoreCase(status)) continue;

            BookingItem b = new BookingItem();
            b.id               = o.optString("id", "").trim();
            b.stationId        = o.optString("stationId", "").trim();
            b.stationName      = o.optString("stationName", "");
            b.ownerNic         = o.optString("ownerNic", "");
            b.reservationAtUtc = o.optString("reservationAtUtc", "");
            b.status           = status;
            if (!b.id.isEmpty()) list.add(b);
        }
        return list;
    }

    // --- Model + Adapter (read-only rows) ---
    static class BookingItem {
        String id, stationId, stationName, ownerNic, reservationAtUtc, status;
    }

    static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {
        private final List<BookingItem> items = new ArrayList<>();
        private final SimpleDateFormat inZ    = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        private final SimpleDateFormat inOffs = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        private final SimpleDateFormat out    = new SimpleDateFormat("EEE, dd MMM • HH:mm", Locale.getDefault());

        HistoryAdapter() { inZ.setTimeZone(TimeZone.getTimeZone("UTC")); }

        void submit(List<BookingItem> list) { items.clear(); if (list != null) items.addAll(list); notifyDataSetChanged(); }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reservation, parent, false);
            return new VH(v, inOffs, inZ, out);
        }

        @Override public void onBindViewHolder(@NonNull VH holder, int position) { holder.bind(items.get(position)); }
        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView txtStationName, txtWhen, txtStatus;
            final SimpleDateFormat inOffs, inZ, outFmt;

            VH(@NonNull View itemView, SimpleDateFormat inOffs, SimpleDateFormat inZ, SimpleDateFormat outFmt) {
                super(itemView);
                this.inOffs = inOffs; this.inZ = inZ; this.outFmt = outFmt;
                txtStationName = itemView.findViewById(R.id.txtStationName);
                txtWhen        = itemView.findViewById(R.id.txtWhen);
                txtStatus      = itemView.findViewById(R.id.txtStatus);

                // Hide row actions for history (read-only)
                View btnEdit = itemView.findViewById(R.id.btnEdit);
                View btnDelete = itemView.findViewById(R.id.btnDelete);
                View btnQr = itemView.findViewById(R.id.btnQr);
                if (btnEdit != null) btnEdit.setVisibility(View.GONE);
                if (btnDelete != null) btnDelete.setVisibility(View.GONE);
                if (btnQr != null) btnQr.setVisibility(View.GONE);
            }

            void bind(BookingItem b) {
                txtStationName.setText(
                        (b.stationName == null || b.stationName.isEmpty()) ? "(Unknown station)" : b.stationName
                );

                txtStatus.setText("Completed");

                String whenStr = b.reservationAtUtc;
                try {
                    Date d;
                    try { d = inOffs.parse(b.reservationAtUtc); }
                    catch (Exception e) { d = inZ.parse(b.reservationAtUtc); }
                    if (d != null) whenStr = outFmt.format(d);
                } catch (Exception ignored) {}
                txtWhen.setText(whenStr);
            }
        }
    }
}
