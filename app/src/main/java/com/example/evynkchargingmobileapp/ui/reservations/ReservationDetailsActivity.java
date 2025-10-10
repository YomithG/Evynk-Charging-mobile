package com.example.evynkchargingmobileapp.ui.reservations;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.data.db.UserDao;
import com.example.evynkchargingmobileapp.util.Prefs;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Date;

public class ReservationDetailsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "ReservationDetails";

    // Base URL (normalized, no trailing slash) read from strings.xml
    private String apiBase;

    private TextView txtTitle, txtStatus, txtWhen, txtStation;
    private View qrCard;
    private ImageView qrImage;
    private ProgressBar qrProgress;
    private ImageButton btnShare, btnSave, btnOpenMaps;

    private final Handler main = new Handler(Looper.getMainLooper());

    private String reservationId;
    private String stationId;
    private String token;
    private Bitmap qrBitmap;

    // Map bits
    private GoogleMap googleMap;
    private boolean mapReady = false;
    private boolean detailsLoaded = false;
    private boolean stationLookupTried = false;
    private LatLng stationLatLng;
    private String stationAddress;
    private String stationNameForMap = "Charging Station";

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reservation_details);

        // Load and normalize base URL from resources
        apiBase = stripTrailingSlash(getString(R.string.base_url));

        MaterialToolbar top = findViewById(R.id.topAppBar);
        if (top != null) {
            top.setTitle("Reservation");
            setSupportActionBar(top);
            if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            top.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        }

        txtTitle   = findViewById(R.id.txtTitle);
        txtStatus  = findViewById(R.id.txtStatus);
        txtWhen    = findViewById(R.id.txtWhen);
        txtStation = findViewById(R.id.txtStation);

        qrCard     = findViewById(R.id.qrCard);
        qrImage    = findViewById(R.id.qrImage);
        qrProgress = findViewById(R.id.qrProgress);
        btnShare   = findViewById(R.id.btnShare);
        btnSave    = findViewById(R.id.btnSave);
        btnOpenMaps= findViewById(R.id.btnOpenMaps);

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        reservationId = getIntent().getStringExtra("id");
        stationId     = getIntent().getStringExtra("stationId");
        if (reservationId == null || reservationId.trim().isEmpty()) {
            Toast.makeText(this, "Missing reservation id", Toast.LENGTH_LONG).show();
            finish(); return;
        }
        token = resolveToken();
        if (token == null || token.trim().isEmpty()) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_LONG).show();
            finish(); return;
        }

        btnShare.setOnClickListener(v -> shareQr());
        btnSave.setOnClickListener(v -> saveQrToGallery());
        btnOpenMaps.setOnClickListener(v -> openInGoogleMaps());

        fetchDetails();
    }

    private String resolveToken() {
        String fromIntent = getIntent().getStringExtra("token");
        if (!isEmpty(fromIntent)) return fromIntent;
        String nic = Prefs.getCurrentNic(this);
        if (!isEmpty(nic)) {
            String t = new UserDao(getApplicationContext()).getAccessToken(nic);
            if (!isEmpty(t)) return t;
        }
        return getSharedPreferences("auth", MODE_PRIVATE).getString("token", null);
    }

    private void fetchDetails() {
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                URL url = new URL(apiBase + "/api/owner/reservations/" + reservationId);
                c = (HttpURLConnection) url.openConnection();
                c.setRequestProperty("Authorization", "Bearer " + token);
                c.setRequestProperty("Accept", "application/json");
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                int code = c.getResponseCode();
                InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
                String body = readAllText(is);

                if (code >= 200 && code < 300) {
                    JSONObject data = new JSONObject(body).optJSONObject("data");
                    if (data == null) throw new Exception("Malformed response");

                    final String stationName = data.optString("stationName", "(Unknown station)");
                    final String status      = data.optString("status", "");
                    final String whenUtc     = data.optString("reservationAtUtc", "");

                    final String stationIdFromApi = data.optString("stationId", null);
                    if (!isEmpty(stationIdFromApi)) stationId = stationIdFromApi.trim();

                    final String latStr = firstNonEmpty(
                            data.optString("stationLat", null),
                            data.optString("lat", null),
                            getNestedString(data, "location", "lat"),
                            getNestedString(data, "geo", "lat")
                    );
                    final String lngStr = firstNonEmpty(
                            data.optString("stationLng", null),
                            data.optString("lng", null),
                            getNestedString(data, "location", "lng"),
                            getNestedString(data, "geo", "lng")
                    );
                    final String address = firstNonEmpty(
                            data.optString("stationAddress", null),
                            data.optString("address", null),
                            getNestedString(data, "location", "address")
                    );

                    main.post(() -> {
                        stationNameForMap = isEmpty(stationName) ? "Charging Station" : stationName;
                        txtTitle.setText(stationNameForMap);
                        txtStatus.setText("Active".equalsIgnoreCase(status) ? "Approved" : status);
                        txtStation.setText(stationNameForMap);
                        txtWhen.setText(formatWhen(whenUtc));

                        stationLatLng = parseLatLng(latStr, lngStr);
                        stationAddress = address;

                        detailsLoaded = true;
                        maybeResolveStationLocation();

                        boolean approved = "Active".equalsIgnoreCase(status);
                        qrCard.setVisibility(approved ? View.VISIBLE : View.GONE);
                        if (approved) loadQr();
                    });
                } else {
                    Log.e(TAG, "GET details failed " + code + ": " + body);
                    main.post(() -> {
                        Toast.makeText(this, "Failed to load reservation (" + code + ")", Toast.LENGTH_LONG).show();
                        finish();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "details error", e);
                main.post(() -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
    }

    private void maybeResolveStationLocation() {
        if (!detailsLoaded || !mapReady) return;

        if (stationLatLng != null) { setupMapMarker(); return; }

        if (!isEmpty(stationAddress)) {
            LatLng maybe = parseLatLngFromSingle(stationAddress);
            if (maybe != null) { stationLatLng = maybe; setupMapMarker(); return; }
        }

        if (!isEmpty(stationId) && !stationLookupTried) {
            stationLookupTried = true;
            fetchStationById(stationId);
            return;
        }

        setupMapMarker(); // will attempt Geocoder as the last resort
    }

    private void fetchStationById(String stId) {
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                URL url = new URL(apiBase + "/api/public/stations/" + stId);
                c = (HttpURLConnection) url.openConnection();
                if (!isEmpty(token)) c.setRequestProperty("Authorization", "Bearer " + token);
                c.setRequestProperty("Accept", "application/json");
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);

                int code = c.getResponseCode();
                String body = readAllText((code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream());
                if (code == 401 || code == 403) {
                    Log.e(TAG, "Station by ID unauthorized (" + code + "): " + body);
                }

                if (code >= 200 && code < 300) {
                    JSONObject root = new JSONObject(body);
                    JSONObject data = root.optJSONObject("data");
                    if (data == null) {
                        JSONArray arr = root.optJSONArray("data");
                        if (arr != null && arr.length() > 0) data = arr.optJSONObject(0);
                    }
                    if (data != null) {
                        final String latStr = firstNonEmpty(
                                data.optString("lat", null),
                                data.optString("latitude", null),
                                getNestedString(data, "geo", "lat"),
                                getNestedString(data, "location", "lat")
                        );
                        final String lngStr = firstNonEmpty(
                                data.optString("lng", null),
                                data.optString("longitude", null),
                                getNestedString(data, "geo", "lng"),
                                getNestedString(data, "location", "lng")
                        );
                        final String addr = firstNonEmpty(
                                data.optString("address", null),
                                getNestedString(data, "location", "address")
                        );

                        final LatLng parsed = parseLatLng(latStr, lngStr);
                        final String address = addr;

                        main.post(() -> {
                            if (parsed != null) stationLatLng = parsed;
                            if (!isEmpty(address)) stationAddress = address;

                            if (stationLatLng == null && !isEmpty(stationAddress)) {
                                LatLng maybe = parseLatLngFromSingle(stationAddress);
                                if (maybe != null) stationLatLng = maybe;
                            }
                            setupMapMarker();
                        });
                    } else {
                        Log.w(TAG, "Station fetch: no data object");
                        main.post(this::setupMapMarker);
                    }
                } else {
                    Log.e(TAG, "Station GET failed " + code + ": " + body);
                    main.post(this::setupMapMarker);
                }
            } catch (Exception e) {
                Log.e(TAG, "Station fetch error", e);
                main.post(this::setupMapMarker);
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
    }

    private LatLng parseLatLng(String latStr, String lngStr) {
        try {
            if (!isEmpty(latStr) && !isEmpty(lngStr)) {
                double la = Double.parseDouble(latStr.trim());
                double ln = Double.parseDouble(lngStr.trim());
                return new LatLng(la, ln);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static LatLng parseLatLngFromSingle(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        String re = "^\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*$";
        if (trimmed.matches(re)) {
            try {
                String[] parts = trimmed.split(",");
                double la = Double.parseDouble(parts[0].trim());
                double ln = Double.parseDouble(parts[1].trim());
                return new LatLng(la, ln);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String formatWhen(String utc) {
        try {
            SimpleDateFormat inZ = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            SimpleDateFormat inO = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
            inZ.setTimeZone(TimeZone.getTimeZone("UTC"));
            SimpleDateFormat out = new SimpleDateFormat("EEE, dd MMM yyyy • HH:mm", Locale.getDefault());
            try { return out.format(inO.parse(utc)); } catch (Exception ignored) {}
            return out.format(inZ.parse(utc));
        } catch (Exception e) {
            return utc;
        }
    }

    private void loadQr() {
        qrProgress.setVisibility(View.VISIBLE);
        qrImage.setImageDrawable(new ColorDrawable(0x00000000));
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                URL url = new URL(apiBase + "/api/owner/reservations/" + reservationId + "/qr-image");
                c = (HttpURLConnection) url.openConnection();
                c.setRequestProperty("Authorization", "Bearer " + token);
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                int code = c.getResponseCode();
                if (code >= 200 && code < 300) {
                    Bitmap bmp = BitmapFactory.decodeStream(new BufferedInputStream(c.getInputStream()));
                    qrBitmap = bmp;
                    main.post(() -> {
                        qrProgress.setVisibility(View.GONE);
                        if (bmp != null) qrImage.setImageBitmap(bmp);
                        else Toast.makeText(this, "Failed to decode QR", Toast.LENGTH_LONG).show();
                    });
                } else {
                    main.post(() -> {
                        qrProgress.setVisibility(View.GONE);
                        Toast.makeText(this, "QR fetch failed (" + code + ")", Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "qr error", e);
                main.post(() -> {
                    qrProgress.setVisibility(View.GONE);
                    Toast.makeText(this, "QR error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
    }

    private void shareQr() {
        if (qrBitmap == null) { Toast.makeText(this, "QR not loaded yet", Toast.LENGTH_SHORT).show(); return; }
        try {
            File cacheDir = new File(getCacheDir(), "share");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File f = new File(cacheDir, "reservation_qr.png");
            try (FileOutputStream fos = new FileOutputStream(f)) {
                qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("image/png");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, getString(R.string.share_qr)));
        } catch (Exception e) {
            Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveQrToGallery() {
        if (qrBitmap == null) { Toast.makeText(this, "QR not loaded yet", Toast.LENGTH_SHORT).show(); return; }
        try {
            String fileName = "EVynk_QR.png";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/EVynk");
                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new Exception("Failed to create media entry");
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "EVynk");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, fileName);
                try (FileOutputStream fos = new FileOutputStream(f)) {
                    qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(f)));
            }
            Toast.makeText(this, getString(R.string.saved_to_gallery), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static String readAllText(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line; while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    @Override public void onMapReady(GoogleMap map) {
        this.googleMap = map;
        mapReady = true;
        maybeResolveStationLocation();
    }

    private void setupMapMarker() {
        if (!mapReady || googleMap == null) return;

        googleMap.clear();

        if (stationLatLng == null && !isEmpty(stationAddress)) {
            LatLng maybe = parseLatLngFromSingle(stationAddress);
            if (maybe != null) stationLatLng = maybe;
        }

        if (stationLatLng != null) {
            googleMap.addMarker(new MarkerOptions().position(stationLatLng).title(stationNameForMap));
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(stationLatLng, 15f));
            return;
        }

        if (!isEmpty(stationAddress)) {
            new Thread(() -> {
                try {
                    Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                    List<Address> res = geocoder.getFromLocationName(stationAddress, 1);
                    if (res != null && !res.isEmpty()) {
                        Address a = res.get(0);
                        stationLatLng = new LatLng(a.getLatitude(), a.getLongitude());
                        main.post(() -> {
                            if (googleMap != null) {
                                googleMap.addMarker(new MarkerOptions().position(stationLatLng).title(stationNameForMap));
                                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(stationLatLng, 15f));
                            }
                        });
                    } else {
                        main.post(() -> Toast.makeText(this, "Location unavailable for this station.", Toast.LENGTH_LONG).show());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Geocode failed", e);
                    main.post(() -> Toast.makeText(this, "Location unavailable for this station.", Toast.LENGTH_LONG).show());
                }
            }).start();
        } else {
            Toast.makeText(this, "Location unavailable for this station.", Toast.LENGTH_LONG).show();
        }
    }

    private void openInGoogleMaps() {
        if (stationLatLng != null) {
            String uri = "geo:" + stationLatLng.latitude + "," + stationLatLng.longitude +
                    "?q=" + stationLatLng.latitude + "," + stationLatLng.longitude + "(" + Uri.encode(stationNameForMap) + ")";
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
        } else if (!isEmpty(stationAddress)) {
            String uri = "geo:0,0?q=" + Uri.encode(stationAddress);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
        } else {
            Toast.makeText(this, "Location unavailable for this station.", Toast.LENGTH_LONG).show();
        }
    }

    private static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }
    private static String firstNonEmpty(String... vals) {
        if (vals == null) return null;
        for (String v : vals) if (v != null && !v.trim().isEmpty()) return v.trim();
        return null;
    }
    private static String getNestedString(JSONObject obj, String childKey, String innerKey) {
        try {
            JSONObject c = obj.optJSONObject(childKey);
            if (c == null) return null;
            String s = c.optString(innerKey, null);
            return (!isEmpty(s)) ? s.trim() : null;
        } catch (Exception e) { return null; }
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
