package com.example.evynkchargingmobileapp.ui.operator;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;

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

public class AppointmentsAdapter extends RecyclerView.Adapter<AppointmentsAdapter.AppointmentViewHolder> {

    // Updated callback: ask for confirmation before doing API call
    public interface OnActionListener {
        void onAskConfirm(@NonNull Appointment a, int position, boolean isComplete);
    }

    private final List<Appointment> appointments;
    private final OnActionListener listener;

    private final SimpleDateFormat inFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
    private final SimpleDateFormat outFmt = new SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.getDefault());

    public AppointmentsAdapter(List<Appointment> appointments, OnActionListener listener) {
        this.appointments = appointments;
        this.listener = listener;
        inFmt.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @NonNull
    @Override
    public AppointmentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_appointment, parent, false);
        return new AppointmentViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull AppointmentViewHolder holder, int position) {
        Appointment a = appointments.get(position);
        holder.tvOwnerNic.setText("Owner NIC: " + safe(a.getOwnerNic()));
        holder.tvStatus.setText("Status: " + safe(a.getStatus()));
        holder.tvReservationAt.setText("Reserved at: " + formatUtc(a.getReservationAtUtc()));

        holder.btnComplete.setOnClickListener(v -> {
            if (listener != null) listener.onAskConfirm(a, holder.getBindingAdapterPosition(), true);
        });

        holder.btnCancel.setOnClickListener(v -> {
            if (listener != null) listener.onAskConfirm(a, holder.getBindingAdapterPosition(), false);
        });
    }

    @Override
    public int getItemCount() {
        return appointments.size();
    }

    public void removeAt(int position) {
        if (position >= 0 && position < appointments.size()) {
            appointments.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, appointments.size() - position);
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

    static class AppointmentViewHolder extends RecyclerView.ViewHolder {
        TextView tvOwnerNic, tvStatus, tvReservationAt;
        MaterialButton btnComplete, btnCancel;

        AppointmentViewHolder(View itemView) {
            super(itemView);
            tvOwnerNic = itemView.findViewById(R.id.tvOwnerNic);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvReservationAt = itemView.findViewById(R.id.tvReservationAt);
            btnComplete = itemView.findViewById(R.id.btnComplete);
            btnCancel = itemView.findViewById(R.id.btnCancel);
        }
    }
}
