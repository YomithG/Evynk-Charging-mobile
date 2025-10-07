package com.example.evynkchargingmobileapp.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.data.model.User;
import com.example.evynkchargingmobileapp.repo.AuthRepository;
import com.example.evynkchargingmobileapp.util.Prefs;

public class LoginActivity extends AppCompatActivity {
    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvToRegister;
    private AuthRepository repo;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // auto-skip if already logged in
        String nic = Prefs.getCurrentNic(this);
        if (nic != null) {
            finish(); // already logged; replace with startActivity(new Intent(this, DashboardActivity.class));
            return;
        }

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvToRegister = findViewById(R.id.tvToRegister);

        repo = new AuthRepository(this);

        btnLogin.setOnClickListener(v -> onLogin());
        tvToRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void onLogin() {
        String email = etEmail.getText().toString().trim();
        String pass  = etPassword.getText().toString();

        if (email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Enter email and password.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnLogin.setEnabled(false);

        repo.login(email, pass, new AuthRepository.Callback<User>() {
            @Override public void onSuccess(User data) {
                main.post(() -> {
                    Prefs.setCurrentNic(LoginActivity.this, data.nic);
                    Toast.makeText(LoginActivity.this, "Welcome " + (data.name == null ? "" : data.name), Toast.LENGTH_SHORT).show();
                    finish(); // or startActivity(new Intent(this, DashboardActivity.class));
                });
            }

            @Override public void onError(String msg) {
                main.post(() -> {
                    btnLogin.setEnabled(true);
                    Toast.makeText(LoginActivity.this, "Login failed: " + msg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
