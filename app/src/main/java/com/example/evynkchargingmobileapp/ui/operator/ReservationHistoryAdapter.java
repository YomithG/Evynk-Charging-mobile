package com.example.evynkchargingmobileapp.ui.operator;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.data.model.Appointment;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ReservationHistoryAdapter extends RecyclerView.Adapter<ReservationHistoryAdapter.Holder> {

    private final List<Appointment> data;
    private final SimpleDateFormat inFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
    private final SimpleDateFormat outFmt = new SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.getDefault());

    public ReservationHistoryAdapter(List<Appointment> data) {
        this.data = data;
        inFmt.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_reservation_history, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Appointment a = data.get(position);

        h.tvOwnerNic.setText("Owner NIC: " + safe(a.getOwnerNic()));
        h.tvReservationAt.setText(" • " + formatUtc(a.getReservationAtUtc()));

        String status = safe(a.getStatus());
        if ("Completed".equalsIgnoreCase(status)) {
            h.tvStatusBadge.setText("Completed");
            h.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_completed);
        } else {
            h.tvStatusBadge.setText("Cancelled");
            h.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_cancelled);
        }

        // If you add stationId to model, set it here:
        // h.tvStationId.setText("Station: " + a.getStationId());
        h.tvStationId.setText(""); // or hide if not used
        h.tvStationId.setVisibility(View.GONE);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView tvOwnerNic, tvStatusBadge, tvReservationAt, tvStationId;
        Holder(View itemView) {
            super(itemView);
            tvOwnerNic = itemView.findViewById(R.id.tvOwnerNic);
            tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
            tvReservationAt = itemView.findViewById(R.id.tvReservationAt);
            tvStationId = itemView.findViewById(R.id.tvStationId);
        }
    }

    private String safe(String s) { return s == null ? "-" : s; }

    private String formatUtc(String isoUtc) {
        if (isoUtc == null || isoUtc.isEmpty()) return "-";
        try {
            Date d = inFmt.parse(isoUtc);
            return d == null ? isoUtc : outFmt.format(d);
        } catch (ParseException e) {
            return isoUtc;
        }
    }
}
