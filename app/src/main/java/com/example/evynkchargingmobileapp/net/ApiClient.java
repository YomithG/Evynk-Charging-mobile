package com.example.evynkchargingmobileapp.net;

import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;

public class ApiClient {
    private final String baseUrl;
    public ApiClient(String baseUrl) { this.baseUrl = baseUrl; }

    public JSONObject post(String path, JSONObject body, String bearer) throws Exception {
        URL url = new URL(baseUrl + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
        c.setDoOutput(true);
        try (OutputStream os = c.getOutputStream()) {
            os.write(body.toString().getBytes("UTF-8"));
        }
        int code = c.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        String resp = new BufferedReader(new InputStreamReader(is))
                .lines().collect(Collectors.joining("\n"));
        c.disconnect();
        if (code >= 200 && 299 >= code) return new JSONObject(resp);
        throw new IOException("HTTP " + code + ": " + resp);
    }

    public JSONObject get(String path, String bearer) throws Exception {
        URL url = new URL(baseUrl + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("GET");
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
        int code = c.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        String resp = new BufferedReader(new InputStreamReader(is))
                .lines().collect(Collectors.joining("\n"));
        c.disconnect();
        if (code >= 200 && 299 >= code) return new JSONObject(resp);
        throw new IOException("HTTP " + code + ": " + resp);
    }
}
