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
    private EditText etUsername, etPassword;
    private Button btnLogin;
    private TextView tvToRegister;
    private AuthRepository repo;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvToRegister = findViewById(R.id.tvToRegister);

        repo = new AuthRepository(this);

        btnLogin.setOnClickListener(v -> onLogin());
        tvToRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void onLogin() {
        String user = etUsername.getText().toString().trim();
        String pass = etPassword.getText().toString();
        if (user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Enter username and password.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnLogin.setEnabled(false);

        repo.login(user, pass, new AuthRepository.Callback<User>() {
            @Override public void onSuccess(User data) {
                main.post(() -> {
                    Prefs.setCurrentNic(LoginActivity.this, data.nic);
                    Toast.makeText(LoginActivity.this, "Welcome " + data.name, Toast.LENGTH_SHORT).show();
                    finish(); // or startActivity(new Intent(this, SomeDashboardActivity.class));
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
