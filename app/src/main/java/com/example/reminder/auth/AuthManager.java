package com.example.reminder.auth;

import android.content.Context;
import android.util.Log;

import com.example.reminder.network.ApiClient;
import com.example.reminder.network.AuthApi;
import com.example.reminder.network.AuthResponse;
import com.example.reminder.network.LoginRequest;
import com.example.reminder.network.RefreshTokenRequest;
import com.example.reminder.network.RegisterRequest;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthManager {
    private static final String TAG = "AuthManager";
    private static AuthManager instance;
    private final TokenManager tokenManager;
    private final Context context;

    public interface AuthCallback {
        void onSuccess();
        void onError(String message);
    }

    private AuthManager(Context context) {
        this.context = context.getApplicationContext();
        this.tokenManager = TokenManager.getInstance(context);
    }

    public static synchronized AuthManager getInstance(Context context) {
        if (instance == null) {
            instance = new AuthManager(context);
        }
        return instance;
    }

    public boolean isLoggedIn() {
        return tokenManager.isLoggedIn();
    }

    public void login(String email, String password, String deviceName, String platform, AuthCallback callback) {
        AuthApi authApi = ApiClient.getAuthServiceNoAuth(context);
        LoginRequest request = new LoginRequest(email, password, deviceName, platform);

        authApi.login(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();
                    tokenManager.saveSession(
                            authResponse.getAccessToken(),
                            authResponse.getRefreshToken(),
                            authResponse.getUserId(),
                            authResponse.getUsername()
                    );
                    com.example.reminder.sync.WebSocketManager.getInstance(context).connect();
                    callback.onSuccess();
                } else {
                    String errMsg = "Login failed: " + response.code();
                    try {
                        if (response.errorBody() != null) {
                            errMsg = response.errorBody().string();
                        }
                    } catch (Exception ignored) {}
                    callback.onError(errMsg);
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                Log.e(TAG, "Login network failure", t);
                callback.onError("Network connection failure: " + t.getMessage());
            }
        });
    }

    public void register(String username, String email, String password, String deviceName, String platform, AuthCallback callback) {
        AuthApi authApi = ApiClient.getAuthServiceNoAuth(context);
        RegisterRequest request = new RegisterRequest(username, email, password, deviceName, platform);

        authApi.register(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();
                    tokenManager.saveSession(
                            authResponse.getAccessToken(),
                            authResponse.getRefreshToken(),
                            authResponse.getUserId(),
                            authResponse.getUsername()
                    );
                    com.example.reminder.sync.WebSocketManager.getInstance(context).connect();
                    callback.onSuccess();
                } else {
                    String errMsg = "Registration failed: " + response.code();
                    try {
                        if (response.errorBody() != null) {
                            errMsg = response.errorBody().string();
                        }
                    } catch (Exception ignored) {}
                    callback.onError(errMsg);
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                Log.e(TAG, "Registration network failure", t);
                callback.onError("Network connection failure: " + t.getMessage());
            }
        });
    }

    public void logout(AuthCallback callback) {
        String refreshToken = tokenManager.getRefreshToken();
        if (refreshToken == null) {
            // Already logged out locally
            tokenManager.clearSession();
            com.example.reminder.sync.WebSocketManager.getInstance(context).disconnect();
            callback.onSuccess();
            return;
        }

        AuthApi authApi = ApiClient.getAuthServiceNoAuth(context);
        authApi.logout(new RefreshTokenRequest(refreshToken)).enqueue(new Callback<Map<String, String>>() {
            @Override
            public void onResponse(Call<Map<String, String>> call, Response<Map<String, String>> response) {
                // Regardless of backend response status, wipe credentials locally for security
                tokenManager.clearSession();
                com.example.reminder.sync.WebSocketManager.getInstance(context).disconnect();
                callback.onSuccess();
            }

            @Override
            public void onFailure(Call<Map<String, String>> call, Throwable t) {
                Log.w(TAG, "Logout backend call failed, wiping local session anyway", t);
                tokenManager.clearSession();
                com.example.reminder.sync.WebSocketManager.getInstance(context).disconnect();
                callback.onSuccess();
            }
        });
    }

    private final Object refreshLock = new Object();

    public boolean isTokenExpired(String token) {
        if (token == null || token.isEmpty()) return true;
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return true;
            String payloadEnc = parts[1];
            byte[] bytes = android.util.Base64.decode(payloadEnc, android.util.Base64.DEFAULT);
            String payloadDec = new String(bytes, "UTF-8");
            org.json.JSONObject json = new org.json.JSONObject(payloadDec);
            if (json.has("exp")) {
                long exp = json.getLong("exp");
                long current = System.currentTimeMillis() / 1000;
                // Add a 10 seconds buffer
                return exp <= (current + 10);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking token expiration", e);
        }
        return true;
    }

    public String getValidAccessToken() throws java.io.IOException {
        String token = tokenManager.getAccessToken();
        if (token != null && !isTokenExpired(token)) {
            return token;
        }

        synchronized (refreshLock) {
            // Re-read token after acquiring lock in case another thread refreshed it
            token = tokenManager.getAccessToken();
            if (token != null && !isTokenExpired(token)) {
                Log.d(TAG, "Access token refreshed by another concurrent thread. Reusing.");
                return token;
            }

            String refreshToken = tokenManager.getRefreshToken();
            if (refreshToken == null) {
                throw new java.io.IOException("No refresh token available");
            }

            Log.d(TAG, "Access token expired or missing. Centralized coordinator starting refresh...");
            AuthApi authApi = ApiClient.getAuthServiceNoAuth(context);
            retrofit2.Response<AuthResponse> response = authApi.refresh(new RefreshTokenRequest(refreshToken)).execute();

            if (response.isSuccessful() && response.body() != null) {
                AuthResponse authResponse = response.body();
                tokenManager.saveSession(
                        authResponse.getAccessToken(),
                        authResponse.getRefreshToken(),
                        authResponse.getUserId(),
                        authResponse.getUsername()
                );
                Log.d(TAG, "Centralized refresh request completed successfully.");
                return authResponse.getAccessToken();
            } else {
                String errMsg = "Refresh failed: " + response.code();
                try {
                    if (response.errorBody() != null) {
                        errMsg = response.errorBody().string();
                    }
                } catch (Exception ignored) {}
                Log.e(TAG, "Centralized refresh failed. Error: " + errMsg);
                throw new retrofit2.HttpException(response);
            }
        }
    }
}
