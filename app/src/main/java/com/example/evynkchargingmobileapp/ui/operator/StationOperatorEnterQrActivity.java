package com.example.evynkchargingmobileapp.ui.operator;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.evynkchargingmobileapp.R;
import com.google.android.material.button.MaterialButton;

public class StationOperatorEnterQrActivity extends AppCompatActivity {

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_station_operator_enter_qr);

        EditText etQr = findViewById(R.id.etQr);
        MaterialButton btnOk = findViewById(R.id.btnOk);
        MaterialButton btnCancel = findViewById(R.id.btnCancel);

        btnOk.setOnClickListener(v -> {
            String text = etQr.getText().toString().trim();
            if (TextUtils.isEmpty(text)) {
                Toast.makeText(this, "Enter QR payload (text/JSON).", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent result = new Intent();
            result.putExtra("qr_text", text);
            setResult(RESULT_OK, result);
            finish();
        });

        btnCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }
}
