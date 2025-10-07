package com.example.evynkchargingmobileapp.ui.dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.data.db.UserDao;
import com.example.evynkchargingmobileapp.data.model.User;
import com.example.evynkchargingmobileapp.ui.auth.LoginActivity;
import com.example.evynkchargingmobileapp.util.Prefs;

public class DashboardActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        TextView tvWelcome = findViewById(R.id.tvWelcome);
        TextView tvNic = findViewById(R.id.tvNic);
        Button btnLogout = findViewById(R.id.btnLogout);

        String nic = Prefs.getCurrentNic(this);
        if (nic == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        User u = new UserDao(this).getUser(nic);
        tvWelcome.setText("Logged in");
        tvNic.setText("NIC: " + (u != null ? u.nic : nic));

        btnLogout.setOnClickListener(v -> {
            Prefs.clear(this);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }
}
