package com.example.evynkchargingmobileapp.ui.stations;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.data.db.UserDao;
import com.example.evynkchargingmobileapp.util.Prefs;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class FindStationActivity extends AppCompatActivity
        implements OnMapReadyCallback, GoogleMap.OnInfoWindowClickListener {

    private static final String TAG = "FindStationActivity";
    private static final String API_BASE = "http://10.0.2.2:5000";

    private static final int REQ_LOCATION = 1001;
    private static final double NEARBY_RADIUS_KM = 12.0;

    private GoogleMap googleMap;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView statusText;

    private FusedLocationProviderClient fused;
    private boolean locationGranted = false;
    private LatLng myLatLng = null;
    private Marker myMarker = null;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_station);

        MaterialToolbar top = findViewById(R.id.topAppBar);
        if (top != null) {
            top.setTitle("Find Station");
            setSupportActionBar(top);
            top.setNavigationOnClickListener(v -> onBackPressed());
        }
        statusText = findViewById(R.id.statusText);

        fused = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        ensureLocationPermission();
    }

    @Override public void onMapReady(GoogleMap map) {
        googleMap = map;
        googleMap.setOnInfoWindowClickListener(this);

        // Show the system “my location” blue dot & the floating target button
        updateMyLocationLayer();

        // Fetch current location, then plot stations (nearby if we have a fix)
        getMyLocationThen(this::fetchAndPlotStations);
    }

    // ---------- Location ----------
    private void ensureLocationPermission() {
        locationGranted =
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;

        if (!locationGranted) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{ Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION },
                    REQ_LOCATION
            );
        }
    }

    private void updateMyLocationLayer() {
        if (googleMap == null) return;
        try {
            googleMap.setMyLocationEnabled(locationGranted);
            googleMap.getUiSettings().setMyLocationButtonEnabled(locationGranted);
        } catch (SecurityException ignored) { }
    }

    /** Try getCurrentLocation first (fast one-shot), fall back to lastLocation */
    private void getMyLocationThen(Runnable next) {
        if (!locationGranted) { next.run(); return; }

        try {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(loc -> {
                        if (loc != null) {
                            setMyLatLng(loc);
                            next.run();
                        } else {
                            // fallback
                            fused.getLastLocation()
                                    .addOnSuccessListener(last -> {
                                        if (last != null) setMyLatLng(last);
                                        next.run();
                                    })
                                    .addOnFailureListener(e -> { Log.w(TAG, "lastLocation failed: " + e.getMessage()); next.run(); });
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "getCurrentLocation failed: " + e.getMessage());
                        next.run();
                    });
        } catch (SecurityException se) {
            Log.w(TAG, "Location permission missing when fetching location.");
            next.run();
        }
    }

    private void setMyLatLng(Location loc) {
        myLatLng = new LatLng(loc.getLatitude(), loc.getLongitude());

        // Add/refresh our custom marker too (so user sees something even if dot not rendered yet)
        if (googleMap != null) {
            if (myMarker != null) myMarker.remove();
            myMarker = googleMap.addMarker(new MarkerOptions()
                    .position(myLatLng)
                    .title("You are here")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode,
                                                     @NonNull String[] permissions,
                                                     @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            locationGranted = false;
            for (int r : grantResults) if (r == PackageManager.PERMISSION_GRANTED) { locationGranted = true; break; }
            updateMyLocationLayer();
            getMyLocationThen(this::fetchAndPlotStations);
        }
    }

    // ---------- Network & plotting ----------
    private void fetchAndPlotStations() {
        showStatus("Loading stations…");
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                URL url = new URL(API_BASE + "/api/public/stations");
                c = (HttpURLConnection) url.openConnection();
                c.setRequestProperty("Accept", "application/json");

                String token = readToken();
                if (token != null && !token.trim().isEmpty()) {
                    c.setRequestProperty("Authorization", "Bearer " + token);
                }

                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);

                int code = c.getResponseCode();
                InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
                String body = readAllText(is);

                if (code == 401) {
                    Log.e(TAG, "HTTP 401 (unauthorized). Endpoint requires a valid token.");
                    main.post(() -> { hideStatus(); showToast("Unauthorized. Please sign in again."); });
                    return;
                }

                if (code >= 200 && code < 300) {
                    final JSONArray stations = coerceToStationsArray(body);
                    main.post(() -> { plot(stations != null ? stations : new JSONArray()); hideStatus(); });
                } else {
                    Log.e(TAG, "HTTP " + code + ": " + body);
                    main.post(() -> { hideStatus(); showToast("Failed to load stations (" + code + ")"); });
                }
            } catch (Exception e) {
                Log.e(TAG, "fetch error", e);
                main.post(() -> { hideStatus(); showToast("Error: " + e.getMessage()); });
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
    }

    private void plot(JSONArray stations) {
        if (googleMap == null) return;
        googleMap.clear();

        // Re-add our location layer & marker if we cleared the map
        updateMyLocationLayer();
        if (myLatLng != null) {
            myMarker = googleMap.addMarker(new MarkerOptions()
                    .position(myLatLng)
                    .title("You are here")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
        }

        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        int plotted = 0;
        boolean usedNearbyFilter = false;

        if (myLatLng != null) bounds.include(myLatLng);

        for (int i = 0; i < stations.length(); i++) {
            JSONObject s = stations.optJSONObject(i);
            if (s == null) continue;

            String name = s.optString("displayName", s.optString("name", "Charging Station"));
            String type = s.optString("type", "");
            int slots   = s.optInt("availableSlots", -1);

            String latStr = firstNonEmpty(s.optString("lat", null), s.optString("latitude", null));
            String lngStr = firstNonEmpty(s.optString("lng", null), s.optString("longitude", null));
            LatLng pos = parseLatLng(latStr, lngStr);

            if (pos == null) {
                String addr = firstNonEmpty(s.optString("address", null), getNestedString(s, "location", "address"));
                pos = parseLatLngFromSingle(addr);
            }
            if (pos == null) continue;

            if (myLatLng != null) {
                double km = distanceKm(myLatLng, pos);
                if (km > NEARBY_RADIUS_KM) { usedNearbyFilter = true; continue; }
            }

            String snippet = buildSnippet(type, slots);
            googleMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title(name)
                    .snippet(snippet)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            bounds.include(pos);
            plotted++;
        }

        if (plotted > 0 || myLatLng != null) {
            try {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100));
            } catch (IllegalStateException ignored) {
                if (myLatLng != null) googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myLatLng, 13f));
                else googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(7.8731, 80.7718), 7f));
            }
        } else {
            if (myLatLng != null && usedNearbyFilter) {
                showToast("No nearby stations within " + (int) NEARBY_RADIUS_KM + " km. Showing all.");
                myLatLng = null; // disable filter
                plot(stations);
                return;
            } else {
                showToast("No stations to show");
            }
        }

        if (plotted > 0) {
            showToast(myLatLng != null ? ("Found " + plotted + " nearby stations") : ("Loaded " + plotted + " stations"));
        }
    }

    private String buildSnippet(String type, int slots) {
        StringBuilder sb = new StringBuilder();
        if (type != null && !type.trim().isEmpty()) sb.append("Type: ").append(type);
        if (slots >= 0) { if (sb.length() > 0) sb.append(" • "); sb.append("Slots: ").append(slots); }
        return sb.toString();
    }

    @Override public void onInfoWindowClick(Marker marker) {
        LatLng p = marker.getPosition();
        String label = marker.getTitle() != null ? marker.getTitle() : "Charging Station";
        String uri = String.format(Locale.US, "geo:%f,%f?q=%f,%f(%s)", p.latitude, p.longitude, p.latitude, p.longitude, Uri.encode(label));
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
    }

    // --- helpers ---
    private static String readAllText(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line; while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static LatLng parseLatLng(String latStr, String lngStr) {
        try {
            if (latStr != null && lngStr != null && !latStr.trim().isEmpty() && !lngStr.trim().isEmpty()) {
                return new LatLng(Double.parseDouble(latStr.trim()), Double.parseDouble(lngStr.trim()));
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static LatLng parseLatLngFromSingle(String s) {
        if (s == null) return null;
        String t = s.trim();
        String re = "^\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*$";
        if (t.matches(re)) {
            try {
                String[] p = t.split(",");
                return new LatLng(Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim()));
            } catch (Exception ignored) {}
        }
        return null;
    }

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
            return (s != null && !s.trim().isEmpty()) ? s.trim() : null;
        } catch (Exception e) { return null; }
    }

    private static double distanceKm(LatLng a, LatLng b) {
        float[] out = new float[1];
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, out);
        return out[0] / 1000.0;
    }

    private void showToast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private void showStatus(String s) {
        if (statusText == null) return;
        statusText.setText(s);
        statusText.setVisibility(View.GONE); // set VISIBLE if you want an on-screen status label
    }
    private void hideStatus() { if (statusText != null) statusText.setVisibility(View.GONE); }

    private @Nullable String readToken() {
        String nic = Prefs.getCurrentNic(this);
        if (nic != null && !nic.trim().isEmpty()) {
            return new UserDao(getApplicationContext()).getAccessToken(nic);
        }
        return null;
    }

    private @Nullable JSONArray coerceToStationsArray(String body) {
        try {
            String trimmed = body == null ? "" : body.trim();
            if (trimmed.startsWith("[")) return new JSONArray(trimmed);
            JSONObject root = new JSONObject(trimmed);

            JSONArray arr = root.optJSONArray("data");
            if (arr != null) return arr;

            arr = root.optJSONArray("stations");
            if (arr != null) return arr;

            JSONObject dataObj = root.optJSONObject("data");
            if (dataObj != null) {
                JSONArray inner = dataObj.optJSONArray("stations");
                if (inner != null) return inner;
                return new JSONArray().put(dataObj);
            }
            return new JSONArray().put(root);
        } catch (Exception e) {
            Log.e(TAG, "coerceToStationsArray parse error: " + e.getMessage());
            return null;
        }
    }
}
