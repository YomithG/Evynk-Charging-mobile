package com.example.evynkchargingmobileapp.ui.dashboard;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.data.db.UserDao;
import com.example.evynkchargingmobileapp.data.model.User;
import com.example.evynkchargingmobileapp.repo.AuthRepository;
import com.example.evynkchargingmobileapp.ui.auth.LoginActivity;
import com.example.evynkchargingmobileapp.util.Prefs;

public class EditProfileActivity extends AppCompatActivity {

    private EditText etFullName, etPhone;
    private Button btnSave, btnCancel, btnDeactivate;
    private AuthRepository repo;

    private String originalFullName = "";
    private String originalPhone = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        etFullName   = findViewById(R.id.etFullName);
        etPhone      = findViewById(R.id.etPhone);
        btnSave      = findViewById(R.id.btnSave);
        btnCancel    = findViewById(R.id.btnCancel);
        btnDeactivate= findViewById(R.id.btnDeactivate);
        repo = new AuthRepository(this);

        String nicKey = Prefs.getCurrentNic(this);
        if (nicKey == null) { finish(); return; }

        // Prefill from DB and cache originals
        UserDao dao = new UserDao(this);
        User u = dao.getUser(nicKey);
        if (u != null) {
            if (u.name  != null) { originalFullName = u.name; etFullName.setText(u.name); }
            if (u.phone != null) { originalPhone    = u.phone; etPhone.setText(u.phone); }
        }

        btnSave.setOnClickListener(v -> {
            String full = etFullName.getText().toString().trim();
            String ph   = etPhone.getText().toString().trim();

            boolean changedName = !full.equals(originalFullName);
            boolean changedPhone = !ph.equals(originalPhone);

            // If nothing changed: just go back to Dashboard (no 500, no API call)
            if (!changedName && !changedPhone) {
                setResult(RESULT_CANCELED);
                finish();
                return;
            }

            btnSave.setEnabled(false);

            repo.updateProfile(nicKey,
                    changedName ? full : null,
                    changedPhone ? ph : null,
                    new AuthRepository.Callback<User>() {
                        @Override public void onSuccess(User data) {
                            runOnUiThread(() -> {
                                btnSave.setEnabled(true);
                                Toast.makeText(EditProfileActivity.this, "Profile updated", Toast.LENGTH_SHORT).show();
                                setResult(RESULT_OK);
                                finish(); // Dashboard will refresh in onResume
                            });
                        }
                        @Override public void onError(String message) {
                            runOnUiThread(() -> {
                                btnSave.setEnabled(true);
                                Toast.makeText(EditProfileActivity.this, "Update failed: " + message, Toast.LENGTH_LONG).show();
                            });
                        }
                    });
        });

        btnCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        btnDeactivate.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Deactivate account?")
                    .setMessage("This will disable your account. You cannot reactivate it yourself. An admin must reactivate you.\n\nProceed?")
                    .setPositiveButton("Deactivate", (d, which) -> doDeactivate(nicKey))
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void doDeactivate(String nicKey) {
        btnDeactivate.setEnabled(false);
        repo.deactivate(nicKey, new AuthRepository.Callback<Boolean>() {
            @Override public void onSuccess(Boolean data) {
                runOnUiThread(() -> {
                    Toast.makeText(EditProfileActivity.this, "Account deactivated", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(EditProfileActivity.this, LoginActivity.class));
                    finishAffinity();
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    btnDeactivate.setEnabled(true);
                    Toast.makeText(EditProfileActivity.this, "Failed: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
