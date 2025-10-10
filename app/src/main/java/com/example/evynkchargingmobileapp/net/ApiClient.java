package com.example.evynkchargingmobileapp.net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class ApiClient {
    private final String baseUrl; // e.g. "http://10.0.2.2:5000/"

    public ApiClient(String baseUrl) {
        if (baseUrl == null) baseUrl = "";
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    private String join(String path) {
        if (path == null) path = "";
        while (path.startsWith("/")) path = path.substring(1);
        return this.baseUrl + path;
    }

    private JSONObject doWrite(String method, String path, JSONObject body, String bearer) throws Exception {
        String full = join(path);
        HttpURLConnection c = (HttpURLConnection) new URL(full).openConnection();
        c.setRequestMethod(method);
        c.setRequestProperty("Content-Type", "application/json");
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
        c.setDoOutput(true);

        if (body != null) {
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }

        int code = c.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        String resp = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                .lines().collect(Collectors.joining("\n"));
        c.disconnect();

        if (code >= 200 && code < 300) {
            return resp.isEmpty() ? new JSONObject() : new JSONObject(resp);
        }
        String msg = "HTTP " + code + " @ " + full + ": " + resp;
        try {
            JSONObject err = new JSONObject(resp);
            String m = err.optString("message", msg);
            throw new IOException("HTTP " + code + " @ " + full + ": " + m);
        } catch (Exception ignore) {
            throw new IOException(msg);
        }
    }

    public JSONObject post(String path, JSONObject body, String bearer) throws Exception {
        return doWrite("POST", path, body, bearer);
    }

    public JSONObject put(String path, JSONObject body, String bearer) throws Exception {
        return doWrite("PUT", path, body, bearer);
    }

    // ✅ Add PATCH method to handle reservations complete action
    public JSONObject patch(String path, JSONObject body, String bearer) throws Exception {
        return doWrite("PATCH", path, body, bearer);
    }

    public JSONObject get(String path, String bearer) throws Exception {
        String full = join(path);
        HttpURLConnection c = (HttpURLConnection) new URL(full).openConnection();
        c.setRequestMethod("GET");
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);

        int code = c.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        String resp = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                .lines().collect(Collectors.joining("\n"));
        c.disconnect();

        if (code >= 200 && code < 300) {
            return resp.isEmpty() ? new JSONObject() : new JSONObject(resp);
        }
        throw new IOException("HTTP " + code + " @ " + full + ": " + resp);
    }

    // Helper to retrieve array from API response
    public JSONArray getArray(String path, String bearer) throws Exception {
        String full = join(path);
        HttpURLConnection c = (HttpURLConnection) new URL(full).openConnection();
        c.setRequestMethod("GET");
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);

        int code = c.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        String resp = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                .lines().collect(Collectors.joining("\n"));
        c.disconnect();

        if (code >= 200 && code < 300) {
            return resp.isEmpty() ? new JSONArray() : new JSONArray(resp);
        }
        throw new IOException("HTTP " + code + " @ " + full + ": " + resp);
    }
}
