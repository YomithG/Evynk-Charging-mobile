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

public class RegisterActivity extends AppCompatActivity {
    private EditText etNic, etFullName, etEmail, etPhone, etPass;
    private Button btnRegister;
    private TextView tvToLogin;
    private AuthRepository repo;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etNic      = findViewById(R.id.etNic);
        etFullName = findViewById(R.id.etFullName);
        etEmail    = findViewById(R.id.etEmail);
        etPhone    = findViewById(R.id.etPhone);
        etPass     = findViewById(R.id.etPass);
        btnRegister= findViewById(R.id.btnRegister);
        tvToLogin  = findViewById(R.id.tvToLogin);

        repo = new AuthRepository(this);

        btnRegister.setOnClickListener(v -> onRegister());
        tvToLogin.setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));
    }

    private void onRegister() {
        String nic  = etNic.getText().toString().trim();
        String name = etFullName.getText().toString().trim();
        String email= etEmail.getText().toString().trim();
        String phone= etPhone.getText().toString().trim();
        String pass = etPass.getText().toString();

        if (nic.isEmpty() || name.isEmpty() || email.isEmpty() || pass.length() < 6) {
            Toast.makeText(this, "NIC, Name, Email and 6+ char password are required.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnRegister.setEnabled(false);

        User u = new User();
        u.nic = nic; u.name = name; u.email = email; u.phone = phone; u.status = 1;

        repo.register(u, pass, new AuthRepository.Callback<User>() {
            @Override public void onSuccess(User data) {
                main.post(() -> {
                    Prefs.setCurrentNic(RegisterActivity.this, data.nic);
                    Toast.makeText(RegisterActivity.this, "Registered!", Toast.LENGTH_SHORT).show();
                    finish(); // or startActivity(new Intent(this, NextActivity.class));
                });
            }
            @Override public void onError(String msg) {
                main.post(() -> {
                    btnRegister.setEnabled(true);
                    Toast.makeText(RegisterActivity.this, "Register failed: " + msg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
