package com.example.evynkchargingmobileapp.ui.reservations;

import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.data.db.UserDao;
import com.example.evynkchargingmobileapp.util.Prefs;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StationMapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "StationMap";
    private static final String API_BASE = "http://10.0.2.2:5000";

    private GoogleMap map;
    private FloatingActionButton fabOpenExternal;

    private final List<MarkerData> markers = new ArrayList<>();

    static class MarkerData {
        String title;
        String address;
        Double lat;
        Double lng;
        String stationId;
    }

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_station_map);

        MaterialToolbar top = findViewById(R.id.topAppBar);
        if (top != null) {
            top.setTitle("Charging stations map");
            setSupportActionBar(top);
            if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            top.setNavigationOnClickListener(v -> onBackPressed());
        }

        fabOpenExternal = findViewById(R.id.fabOpenExternal);
        fabOpenExternal.setOnClickListener(v -> openCameraCenterInExternalMaps());

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);
    }

    @Override public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.getUiSettings().setZoomControlsEnabled(true);
        map.getUiSettings().setMapToolbarEnabled(false);
        fetchStationsAndShow();
    }

    private String resolveToken() {
        String fromIntent = getIntent().getStringExtra("token");
        if (fromIntent != null && !fromIntent.trim().isEmpty()) return fromIntent;
        String nic = Prefs.getCurrentNic(this);
        if (nic != null && !nic.trim().isEmpty()) {
            String t = new UserDao(getApplicationContext()).getAccessToken(nic);
            if (t != null && !t.trim().isEmpty()) return t;
        }
        return getSharedPreferences("auth", MODE_PRIVATE).getString("token", null);
    }

    private void fetchStationsAndShow() {
        final String token = resolveToken(); // optional for /api/public/stations, but OK if your API accepts it
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(API_BASE + "/api/public/stations");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                if (token != null) conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream()
                ));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                if (code >= 200 && code < 300) {
                    parseAndPlot(sb.toString());
                } else {
                    Log.e(TAG, "Stations load failed " + code + " " + sb);
                    runOnUiThread(() -> Toast.makeText(this, "Failed to load stations ("+code+")", Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                Log.e(TAG, "stations error", e);
                runOnUiThread(() -> Toast.makeText(this, "Error: "+e.getMessage(), Toast.LENGTH_LONG).show());
            } finally { if (conn != null) conn.disconnect(); }
        }).start();
    }

    private void parseAndPlot(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray data = root.optJSONArray("data");
            if (data == null) data = new JSONArray();

            markers.clear();
            for (int i = 0; i < data.length(); i++) {
                JSONObject o = data.optJSONObject(i);
                if (o == null) continue;

                MarkerData md = new MarkerData();
                md.stationId = o.optString("id", "");
                // Your backend fields (adjust if needed)
                String location = o.optString("location", "Station");
                String type = o.optString("type", "");
                md.title = type.isEmpty() ? location : location + " • " + type;

                // If your API already has coordinates, use them:
                // md.lat = o.has("lat") ? o.optDouble("lat") : null;
                // md.lng = o.has("lng") ? o.optDouble("lng") : null;

                // We’ll store the textual address either way
                md.address = location;

                markers.add(md);
            }

            // geocode any items missing lat/lng
            geocodeMissingAndShow();
        } catch (Exception e) {
            Log.e(TAG, "parse error", e);
            runOnUiThread(() -> Toast.makeText(this, "Parse error: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private void geocodeMissingAndShow() {
        new Thread(() -> {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            for (MarkerData m : markers) {
                if (m.lat != null && m.lng != null) continue;
                try {
                    // Try geocoding the address string
                    List<Address> res = geocoder.getFromLocationName(m.address, 1);
                    if (res != null && !res.isEmpty()) {
                        Address a = res.get(0);
                        m.lat = a.getLatitude();
                        m.lng = a.getLongitude();
                    }
                } catch (Exception ignored) {}
            }
            runOnUiThread(this::plotMarkers);
        }).start();
    }

    private void plotMarkers() {
        if (map == null) return;
        map.clear();

        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        int count = 0;

        for (MarkerData m : markers) {
            if (m.lat == null || m.lng == null) continue; // skip ones that couldn’t be geocoded

            LatLng pos = new LatLng(m.lat, m.lng);
            map.addMarker(new MarkerOptions()
                    .position(pos)
                    .title(m.title)
                    .snippet(m.address)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            bounds.include(pos);
            count++;
        }

        if (count > 0) {
            try {
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100));
            } catch (Exception e) {
                // fallback
                MarkerData first = markers.get(0);
                if (first.lat != null && first.lng != null) {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(first.lat, first.lng), 12f));
                }
            }
        } else {
            Toast.makeText(this, "No mappable stations (geocoding failed).", Toast.LENGTH_LONG).show();
        }

        // Click -> open Google Maps with this address
        map.setOnInfoWindowClickListener(marker -> {
            String query = Uri.encode(marker.getSnippet() != null ? marker.getSnippet() : marker.getTitle());
            Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + query);
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            startActivity(mapIntent);
        });
    }

    private void openCameraCenterInExternalMaps() {
        if (map == null) return;
        LatLng target = map.getCameraPosition().target;
        String uri = "geo:" + target.latitude + "," + target.longitude + "?q=" + target.latitude + "," + target.longitude + "(Charging+stations)";
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        intent.setPackage("com.google.android.apps.maps");
        startActivity(intent);
    }
}
