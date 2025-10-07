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

    private TextView tvSubtitle, tvName, tvNicValue, tvEmail, tvPhone, tvStatus;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        tvSubtitle     = findViewById(R.id.tvSubtitle);
        tvName         = findViewById(R.id.tvName);
        tvNicValue     = findViewById(R.id.tvNicValue);
        tvEmail        = findViewById(R.id.tvEmail);
        tvPhone        = findViewById(R.id.tvPhone);
        tvStatus       = findViewById(R.id.tvStatus);
        Button btnLogout = findViewById(R.id.btnLogout);
        Button btnEdit   = findViewById(R.id.btnEdit);

        btnEdit.setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, EditProfileActivity.class)));

        btnLogout.setOnClickListener(v -> {
            Prefs.clear(this);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        loadUser();
    }

    @Override protected void onResume() {
        super.onResume();
        loadUser(); // refresh after returning from edit
    }

    private void loadUser() {
        String nicKey = Prefs.getCurrentNic(this);
        if (nicKey == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        UserDao dao = new UserDao(this);
        User u = dao.getUser(nicKey);

        String nicDisplay = (u != null && u.nic != null) ? u.nic : "-";
        String name       = (u != null && u.name != null) ? u.name : "-";
        String email      = (u != null && u.email != null) ? u.email : "-";
        String phone      = (u != null && u.phone != null) ? u.phone : "-";
        String status     = (u != null && u.status == 1) ? "Active" : (u != null ? "Inactive" : "-");

        tvSubtitle.setText("Welcome back, " + (name.equals("-") ? "User" : name) + "!");
        tvName.setText(name);
        tvNicValue.setText(nicDisplay);
        tvEmail.setText(email);
        tvPhone.setText(phone);
        tvStatus.setText(status);

    }
}
