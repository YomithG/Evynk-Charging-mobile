package com.example.evynkchargingmobileapp.ui.operator;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.evynkchargingmobileapp.R;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
// NOTE correct import:
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StationOperatorScanQrActivity extends AppCompatActivity {

    private PreviewView previewView;
    private TextView tvHint;
    private ImageButton btnClose;

    private ExecutorService cameraExecutor;
    private BarcodeScanner scanner;
    private volatile boolean handled = false;

    private final ActivityResultLauncher<String> cameraPerm =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else {
                    Toast.makeText(this, "Camera permission needed to scan.", Toast.LENGTH_LONG).show();
                    setResult(RESULT_CANCELED);
                    finish();
                }
            });

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_station_operator_scan_qr);

        previewView = findViewById(R.id.previewView);
        tvHint = findViewById(R.id.tvHint);
        btnClose = findViewById(R.id.btnClose);

        btnClose.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        cameraExecutor = Executors.newSingleThreadExecutor();

        BarcodeScannerOptions opts = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        scanner = BarcodeScanning.getClient(opts);

        ensurePermissionAndStart();
    }

    private void ensurePermissionAndStart() {
        boolean granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        if (granted) startCamera();
        else cameraPerm.launch(Manifest.permission.CAMERA);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindUseCases(cameraProvider);
            } catch (Exception e) {
                Toast.makeText(this, "Camera error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll();

        Preview preview = new Preview.Builder().build();
        CameraSelector selector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        analysis.setAnalyzer(cameraExecutor, this::analyze);

        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        Camera camera = cameraProvider.bindToLifecycle(this, selector, preview, analysis);

        tvHint.setVisibility(View.VISIBLE);
    }

    private void analyze(@NonNull ImageProxy imageProxy) {
        if (handled) { imageProxy.close(); return; }
        try {
            if (imageProxy.getImage() == null) { imageProxy.close(); return; }
            InputImage img = InputImage.fromMediaImage(
                    imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

            scanner.process(img)
                    .addOnSuccessListener(barcodes -> {
                        if (handled || barcodes == null || barcodes.isEmpty()) {
                            imageProxy.close();
                            return;
                        }
                        String value = null;
                        for (Barcode b : barcodes) {
                            if (b.getRawValue() != null) { value = b.getRawValue(); break; }
                        }
                        if (value != null) {
                            handled = true;
                            vibrateOnce();
                            Intent result = new Intent();
                            result.putExtra("qr_text", value);
                            setResult(RESULT_OK, result);
                            finish();
                        }
                        imageProxy.close();
                    })
                    .addOnFailureListener(e -> { imageProxy.close(); });
        } catch (Exception e) {
            imageProxy.close();
        }
    }

    private void vibrateOnce() {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(android.os.VibrationEffect.createOneShot(40, 128));
                } else { v.vibrate(40); }
            }
        } catch (Exception ignore) {}
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (scanner != null) scanner.close();
    }
}
