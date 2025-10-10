package com.example.evynkchargingmobileapp.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.data.model.StationOperatorUser;
import com.example.evynkchargingmobileapp.repo.StationOperatorAuthRepository;
import com.example.evynkchargingmobileapp.ui.operator.StationOperatorHomeActivity;
import com.example.evynkchargingmobileapp.util.StationOperatorPrefs;

public class StationOperatorLoginActivity extends AppCompatActivity {
    private EditText etEmail, etPassword;
    private Button btnLogin;
    private StationOperatorAuthRepository repo;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_stationoperator);

        // If already logged in as station operator, go straight to operator home
        if (StationOperatorPrefs.getId(this) != null) {
            startActivity(new Intent(this, StationOperatorHomeActivity.class));
            finish();
            return;
        }

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);

        repo = new StationOperatorAuthRepository(this);

        btnLogin.setOnClickListener(v -> onLogin());
    }

    private void onLogin() {
        String email = etEmail.getText().toString().trim();
        String pass  = etPassword.getText().toString();

        if (email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Enter email and password.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnLogin.setEnabled(false);

        repo.loginStationOperator(email, pass, new StationOperatorAuthRepository.Result<StationOperatorUser>() {
            @Override public void onSuccess(StationOperatorUser u, String token) {
                main.post(() -> {
                    // Save station-operator session (IMPORTANT)
                    StationOperatorPrefs.set(u.id, u.email, (u.role == null ? "StationOperator" : u.role),
                            token, StationOperatorLoginActivity.this);

                    // Optional: if your response has stationId, save it
                    // StationOperatorPrefs.setStationId(StationOperatorLoginActivity.this, u.stationId);

                    Toast.makeText(StationOperatorLoginActivity.this,
                            "Welcome, Station Operator " + (u.name == null ? "" : u.name),
                            Toast.LENGTH_SHORT).show();

                    // Go to Operator Home (NOT normal HomeActivity)
                    startActivity(new Intent(StationOperatorLoginActivity.this, StationOperatorHomeActivity.class));
                    finish();
                });
            }

            @Override public void onError(String msg) {
                main.post(() -> {
                    btnLogin.setEnabled(true);
                    Toast.makeText(StationOperatorLoginActivity.this,
                            "Login failed: " + msg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
