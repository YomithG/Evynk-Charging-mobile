package com.example.evynkchargingmobileapp.data.model;

import com.google.gson.annotations.SerializedName;

public class StationOperatorAuthResult {
    @SerializedName("user") public StationOperatorUser user;
    @SerializedName(value = "token", alternate = {"accessToken", "jwt"}) public String token;

    // Fallback if API returns a bare user object
    @SerializedName(value = "id", alternate = {"_id"}) public String id;
    @SerializedName("email") public String email;
    @SerializedName("role") public String role;
    @SerializedName("name") public String name;

    public StationOperatorUser asUser() {
        if (user != null) return user;
        StationOperatorUser u = new StationOperatorUser();
        u.id = id;
        u.email = email;
        u.role = role;
        u.name = name;
        return u;
    }
}
