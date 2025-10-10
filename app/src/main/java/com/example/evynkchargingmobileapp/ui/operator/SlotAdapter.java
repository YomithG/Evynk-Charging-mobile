package com.example.evynkchargingmobileapp.ui.operator;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.ui.operator.StationOperatorSlotsMockActivity.SlotItem;

import java.util.List;

public class SlotAdapter extends RecyclerView.Adapter<SlotAdapter.VH> {

    private final List<SlotItem> data;

    public SlotAdapter(List<SlotItem> data) {
        this.data = data;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_slot, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int i) {
        SlotItem s = data.get(i);
        h.tvName.setText(s.name);
        h.tvConnector.setText(s.connector + " • " + s.power);
        h.tvUpdated.setText(s.updated);

        h.tvStatus.setText(s.status);
        // Simple badge coloring based on status
        int bg;
        int fg;
        switch (s.status.toLowerCase()) {
            case "available":
                bg = 0x2237D67A; // translucent green-ish
                fg = 0xFF37D67A;
                break;
            case "occupied":
                bg = 0x22E0B422; // translucent amber
                fg = 0xFFE0B422;
                break;
            default: // offline/unknown
                bg = 0x222E3743; // translucent gray
                fg = 0xFFA5ADBA;
                break;
        }
        h.tvStatus.setBackgroundColor(bg);
        h.tvStatus.setTextColor(fg);
    }

    @Override public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        CardView card;
        TextView tvName, tvStatus, tvConnector, tvUpdated;
        VH(@NonNull View v) {
            super(v);
            card       = v.findViewById(R.id.card);
            tvName     = v.findViewById(R.id.tvName);
            tvStatus   = v.findViewById(R.id.tvStatus);
            tvConnector= v.findViewById(R.id.tvConnector);
            tvUpdated  = v.findViewById(R.id.tvUpdated);
        }
    }
}
