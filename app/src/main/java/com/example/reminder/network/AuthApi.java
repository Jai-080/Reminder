package com.example.reminder.network;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface AuthApi {
    @POST("api/auth/register")
    Call<AuthResponse> register(@Body RegisterRequest request);

    @POST("api/auth/login")
    Call<AuthResponse> login(@Body LoginRequest request);

    @POST("api/auth/refresh")
    Call<AuthResponse> refresh(@Body RefreshTokenRequest request);

    @POST("api/auth/logout")
    Call<Map<String, String>> logout(@Body RefreshTokenRequest request);

    @GET("api/auth/me")
    Call<Map<String, Object>> getMe();
}
