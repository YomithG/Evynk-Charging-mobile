package com.example.evynkchargingmobileapp.ui.reservations;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.data.db.UserDao;
import com.example.evynkchargingmobileapp.util.Prefs;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class OwnerReservationsActivity extends AppCompatActivity {

    private static final String TAG = "OwnerResvScreen";
    private static final String API_BASE = "http://10.0.2.2:5000";

    private RecyclerView recycler;
    private TextView emptyView;
    private ProgressBar progress;
    private ReservationsAdapter adapter;   // <- now defined below in this file
    private final Handler main = new Handler(Looper.getMainLooper());

    // Filters
    private MaterialButtonToggleGroup filterGroup;
    private enum Filter { PENDING, APPROVED, CANCELLED }
    private Filter currentFilter = Filter.PENDING;

    // Full list -> client-side filter
    private final List<BookingItem> allItems = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_owner_reservations);

        MaterialToolbar top = findViewById(R.id.topAppBar);
        if (top != null) {
            top.setTitle("My reservations");
            setSupportActionBar(top);
            if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            top.setNavigationOnClickListener(v -> onBackPressed());
        }

        filterGroup = findViewById(R.id.filterGroup);
        filterGroup.check(R.id.btnFilterPending);
        filterGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnFilterPending) currentFilter = Filter.PENDING;
            else if (checkedId == R.id.btnFilterApproved) currentFilter = Filter.APPROVED;
            else if (checkedId == R.id.btnFilterCancelled) currentFilter = Filter.CANCELLED;
            applyFilter();
        });

        recycler = findViewById(R.id.recyclerReservations);
        emptyView = findViewById(R.id.emptyView);
        progress = findViewById(R.id.progress);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReservationsAdapter(new OnReservationActionListener() {
            @Override public void onOpen(BookingItem item)   { openDetails(item); }
            @Override public void onEdit(BookingItem item)   { openEditor(item); }
            @Override public void onDelete(BookingItem item) { confirmDelete(item); }
            @Override public void onQr(BookingItem item)     { showQrDialog(item.id); }
        });
        recycler.setAdapter(adapter);

        fetchUpcoming();
    }

    @Override protected void onResume() {
        super.onResume();
        fetchUpcoming();
    }

    private void openEditor(BookingItem item) {
        Intent i = new Intent(this, EditReservationActivity.class);
        i.putExtra("id", item.id);
        i.putExtra("stationId", item.stationId);
        i.putExtra("stationName", item.stationName);
        i.putExtra("reservationAtUtc", item.reservationAtUtc);
        startActivity(i);
    }

    private void openDetails(BookingItem item) {
        Intent i = new Intent(this, ReservationDetailsActivity.class);
        i.putExtra("id", item.id);
        i.putExtra("stationId", item.stationId); // pass for map lookup
        startActivity(i);
    }

    // ---- Networking ----
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
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
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
                    main.post(() -> {
                        allItems.clear();
                        allItems.addAll(items);
                        applyFilter();
                        showState(allItems.isEmpty(), false);
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
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void applyFilter() {
        List<BookingItem> filtered = new ArrayList<>();
        for (BookingItem b : allItems) {
            String s = b.status == null ? "" : b.status.trim();
            switch (currentFilter) {
                case PENDING:   if (s.equalsIgnoreCase("Pending"))   filtered.add(b); break;
                case APPROVED:  if (s.equalsIgnoreCase("Active"))    filtered.add(b); break; // Active == Approved
                case CANCELLED: if (s.equalsIgnoreCase("Cancelled")) filtered.add(b); break;
            }
        }
        adapter.submit(filtered);
        emptyView.setText(
                currentFilter == Filter.PENDING   ? "No pending reservations." :
                        currentFilter == Filter.APPROVED  ? "No approved reservations." :
                                "No cancelled reservations."
        );
        emptyView.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        recycler.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
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
            if (o.has("id") && !o.optString("id").isEmpty()) {
                BookingItem b = new BookingItem();
                b.id               = o.optString("id", "").trim();
                b.stationId        = o.optString("stationId", "").trim();
                b.stationName      = o.optString("stationName", "");
                b.ownerNic         = o.optString("ownerNic", ""); // not shown in UI list
                b.reservationAtUtc = o.optString("reservationAtUtc", "");
                b.status           = o.optString("status", "");
                list.add(b);
            }
        }
        return list;
    }

    private void confirmDelete(BookingItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Delete reservation?")
                .setMessage("This action cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> doDelete(item.id))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doDelete(String reservationId) {
        if (reservationId == null || reservationId.trim().isEmpty()) {
            Toast.makeText(this, "Cannot delete: reservation has no ID.", Toast.LENGTH_LONG).show();
            return;
        }
        final String token = getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Not authenticated.", Toast.LENGTH_SHORT).show();
            return;
        }

        progress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(API_BASE + "/api/owner/reservations/" + reservationId.trim());
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("DELETE");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    main.post(() -> {
                        progress.setVisibility(View.GONE);
                        Toast.makeText(this, "Reservation deleted 🗑️", Toast.LENGTH_SHORT).show();
                        fetchUpcoming();
                    });
                } else {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                    StringBuilder sb = new StringBuilder(); String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    Log.e(TAG, "Delete failed " + code + " body=" + sb);
                    main.post(() -> {
                        progress.setVisibility(View.GONE);
                        Toast.makeText(this, "Delete failed (" + code + ")", Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception ex) {
                Log.e(TAG, "Delete error", ex);
                main.post(() -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Delete error: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ---- QR dialog (used from Approved list rows) ----
    private void showQrDialog(String reservationId) {
        final String token = getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Not authenticated.", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_qr);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        ImageView qrImage = dialog.findViewById(R.id.qrImage);
        ProgressBar qrProgress = dialog.findViewById(R.id.qrProgress);
        dialog.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());
        dialog.show();

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(API_BASE + "/api/owner/reservations/" + reservationId + "/qr-image");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    InputStream is = new BufferedInputStream(conn.getInputStream());
                    Bitmap bmp = BitmapFactory.decodeStream(is);
                    main.post(() -> {
                        qrProgress.setVisibility(View.GONE);
                        if (bmp != null) qrImage.setImageBitmap(bmp);
                        else {
                            Toast.makeText(this, "Failed to decode QR image.", Toast.LENGTH_LONG).show();
                            dialog.dismiss();
                        }
                    });
                } else {
                    main.post(() -> {
                        qrProgress.setVisibility(View.GONE);
                        Toast.makeText(this, "QR fetch failed (" + code + ")", Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "QR load error", e);
                main.post(() -> {
                    qrProgress.setVisibility(View.GONE);
                    Toast.makeText(this, "QR load error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    // ----- model + adapter -----
    static class BookingItem {
        String id, stationId, stationName, ownerNic, reservationAtUtc, status;
    }

    interface OnReservationActionListener {
        void onOpen(BookingItem item);
        void onEdit(BookingItem item);
        void onDelete(BookingItem item);
        void onQr(BookingItem item);
    }

    static class ReservationsAdapter extends RecyclerView.Adapter<ReservationsAdapter.VH> {
        private final List<BookingItem> items = new ArrayList<>();
        private final SimpleDateFormat inZ    = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        private final SimpleDateFormat inOffs = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        private final SimpleDateFormat out    = new SimpleDateFormat("EEE, dd MMM • HH:mm", Locale.getDefault());
        private final OnReservationActionListener listener;

        ReservationsAdapter(OnReservationActionListener l) {
            this.listener = l;
            inZ.setTimeZone(TimeZone.getTimeZone("UTC"));
        }

        void submit(List<BookingItem> list) { items.clear(); if (list != null) items.addAll(list); notifyDataSetChanged(); }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reservation, parent, false);
            return new VH(v, listener, inOffs, inZ, out);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) { h.bind(items.get(pos)); }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView txtStationName, txtWhen, txtStatus;
            final ImageButton btnEdit, btnDelete, btnQr;
            final OnReservationActionListener callback;
            final SimpleDateFormat inOffs, inZ, outFmt;
            BookingItem current;

            VH(@NonNull View itemView, OnReservationActionListener cb,
               SimpleDateFormat inOffs, SimpleDateFormat inZ, SimpleDateFormat outFmt) {
                super(itemView);
                this.callback = cb;
                this.inOffs = inOffs;
                this.inZ = inZ;
                this.outFmt = outFmt;

                txtStationName = itemView.findViewById(R.id.txtStationName);
                txtWhen        = itemView.findViewById(R.id.txtWhen);
                txtStatus      = itemView.findViewById(R.id.txtStatus);
                btnEdit        = itemView.findViewById(R.id.btnEdit);
                btnDelete      = itemView.findViewById(R.id.btnDelete);
                btnQr          = itemView.findViewById(R.id.btnQr);

                itemView.setOnClickListener(v -> { if (current != null) callback.onOpen(current); });
                btnEdit.setOnClickListener(v -> { if (current != null) callback.onEdit(current); });
                btnDelete.setOnClickListener(v -> { if (current != null) callback.onDelete(current); });
                btnQr.setOnClickListener(v -> { if (current != null) callback.onQr(current); });
            }

            void bind(BookingItem b) {
                this.current = b;

                txtStationName.setText(
                        (b.stationName == null || b.stationName.isEmpty()) ? "(Unknown station)" : b.stationName
                );

                String statusLabel = b.status;
                if ("Active".equalsIgnoreCase(statusLabel)) statusLabel = "Approved";
                txtStatus.setText(statusLabel);

                String whenStr = b.reservationAtUtc;
                try {
                    Date d;
                    try { d = inOffs.parse(b.reservationAtUtc); }
                    catch (Exception e) { d = inZ.parse(b.reservationAtUtc); }
                    if (d != null) whenStr = outFmt.format(d);
                } catch (Exception ignored) {}
                txtWhen.setText(whenStr);

                boolean isPending   = "Pending".equalsIgnoreCase(b.status);
                boolean isApproved  = "Active".equalsIgnoreCase(b.status);
                boolean isCancelled = "Cancelled".equalsIgnoreCase(b.status);

                btnEdit.setVisibility(isPending ? View.VISIBLE : View.GONE);
                btnDelete.setVisibility(isPending ? View.VISIBLE : View.GONE);
                btnQr.setVisibility(isApproved ? View.VISIBLE : View.GONE);

                if (isCancelled) {
                    btnEdit.setVisibility(View.GONE);
                    btnDelete.setVisibility(View.GONE);
                    btnQr.setVisibility(View.GONE);
                }
            }
        }
    }
}
