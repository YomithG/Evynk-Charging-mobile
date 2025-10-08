package com.example.evynkchargingmobileapp.ui.reservations;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
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

public class OwnerReservationsActivity extends AppCompatActivity {

    private static final String TAG = "OwnerResvScreen";
    private static final String API_BASE = "http://10.0.2.2:5000";

    private RecyclerView recycler;
    private TextView emptyView;
    private ProgressBar progress;
    private ReservationsAdapter adapter;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_owner_reservations);

        // Toolbar
        MaterialToolbar top = findViewById(R.id.topAppBar);
        if (top != null) {
            top.setTitle("My upcoming reservations");
            setSupportActionBar(top);
            if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            top.setNavigationOnClickListener(v -> onBackPressed());
        }

        // Views
        recycler = findViewById(R.id.recyclerReservations);
        emptyView = findViewById(R.id.emptyView);
        progress = findViewById(R.id.progress);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReservationsAdapter();
        recycler.setAdapter(adapter);

        // Load only UPCOMING
        fetchUpcoming();
    }

    private void fetchUpcoming() {
        String token = getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Not authenticated (missing token).", Toast.LENGTH_SHORT).show();
            showState(true, false);
            return;
        }

        final String url = API_BASE + "/api/owner/reservations/upcoming";
        showState(false, true);

        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream()
                ));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                conn.disconnect();

                if (code >= 200 && code < 300) {
                    List<BookingItem> items = parseList(sb.toString());
                    main.post(() -> {
                        adapter.submit(items);
                        showState(items.isEmpty(), false);
                    });
                } else {
                    Log.e(TAG, "HTTP " + code + " body=" + sb);
                    main.post(() -> {
                        Toast.makeText(this, "Failed to load reservations (" + code + ")", Toast.LENGTH_SHORT).show();
                        showState(true, false);
                    });
                }
            } catch (Exception ex) {
                Log.e(TAG, "Network error", ex);
                main.post(() -> {
                    Toast.makeText(this, "Network error: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                    showState(true, false);
                });
            }
        }).start();
    }

    private void showState(boolean empty, boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        recycler.setVisibility(!loading && !empty ? View.VISIBLE : View.GONE);
        emptyView.setVisibility(!loading && empty ? View.VISIBLE : View.GONE);
    }

    private @Nullable String getToken() {
        // 1) If caller passed it explicitly
        String fromIntent = getIntent().getStringExtra("token");
        if (fromIntent != null && !fromIntent.trim().isEmpty()) return fromIntent;

        // 2) Your canonical store: DAO via current NIC
        String nic = Prefs.getCurrentNic(this);
        if (nic != null && !nic.trim().isEmpty()) {
            UserDao dao = new UserDao(getApplicationContext());
            String t = dao.getAccessToken(nic);
            if (t != null && !t.trim().isEmpty()) return t;
        }

        // 3) Fallback legacy SP
        String t = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null);
        return (t != null && !t.trim().isEmpty()) ? t : null;
    }

    private List<BookingItem> parseList(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray data = root.optJSONArray("data");
        List<BookingItem> list = new ArrayList<>();
        if (data == null) return list;

        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.getJSONObject(i);
            BookingItem b = new BookingItem();
            b.id = o.optString("id");
            b.stationId = o.optString("stationId");
            b.stationName = o.optString("stationName");
            b.ownerNic = o.optString("ownerNic");
            b.reservationAtUtc = o.optString("reservationAtUtc");
            b.status = o.optString("status");
            list.add(b);
        }
        return list;
    }

    // --- model + adapter ---

    static class BookingItem {
        String id, stationId, stationName, ownerNic, reservationAtUtc, status;
    }

    static class ReservationsAdapter extends RecyclerView.Adapter<ReservationsAdapter.VH> {
        private final List<BookingItem> items = new ArrayList<>();
        private final SimpleDateFormat in  = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        private final SimpleDateFormat out = new SimpleDateFormat("EEE, dd MMM • HH:mm", Locale.getDefault());

        void submit(List<BookingItem> list) {
            items.clear();
            if (list != null) items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reservation, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            BookingItem b = items.get(pos);
            h.txtStationName.setText((b.stationName == null || b.stationName.isEmpty()) ? "(Unknown station)" : b.stationName);
            h.txtStatus.setText(b.status);

            String whenStr = b.reservationAtUtc;
            try {
                Date d = in.parse(b.reservationAtUtc);
                if (d != null) whenStr = out.format(d);
            } catch (Exception ignored) {}
            h.txtWhen.setText(whenStr);

            String idShort = (b.id != null && b.id.length() > 8) ? b.id.substring(0, 8) : String.valueOf(b.id);
            h.txtSub.setText("Owner: " + (b.ownerNic == null ? "-" : b.ownerNic) + " • #" + idShort);
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView txtStationName, txtWhen, txtStatus, txtSub;
            VH(@NonNull View itemView) {
                super(itemView);
                txtStationName = itemView.findViewById(R.id.txtStationName);
                txtWhen        = itemView.findViewById(R.id.txtWhen);
                txtStatus      = itemView.findViewById(R.id.txtStatus);
                txtSub         = itemView.findViewById(R.id.txtSub);
            }
        }
    }
}
