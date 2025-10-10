package com.example.evynkchargingmobileapp.data.model;

import com.google.gson.annotations.SerializedName;

public class StationOperatorUser {
    @SerializedName(value = "id", alternate = {"_id"})
    public String id;

    @SerializedName("email")
    public String email;

    // Role from backend (e.g., "StationOperator")
    @SerializedName("role")
    public String role;

    @SerializedName("name")
    public String name;

    @SerializedName("createdAt")
    public String createdAt;

    @SerializedName("updatedAt")
    public String updatedAt;
}
