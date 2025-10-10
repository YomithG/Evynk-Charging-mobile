package com.example.evynkchargingmobileapp.repo;

import com.example.evynkchargingmobileapp.data.model.StationOperatorAuthResult;

import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface StationOperatorAuthService {
    // Your station operator endpoint:
    // {{baseURL}}/api/Auth/login
    @POST("api/Auth/login")
    Call<StationOperatorAuthResult> login(@Body RequestBody body);
}
