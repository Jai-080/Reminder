package com.example.reminder.auth;

import android.content.Context;
import android.util.Log;

import com.example.reminder.network.ApiClient;
import com.example.reminder.network.AuthApi;
import com.example.reminder.network.AuthResponse;
import com.example.reminder.network.LoginRequest;
import com.example.reminder.network.RefreshTokenRequest;
import com.example.reminder.network.RegisterRequest;
import com.example.reminder.utils.FileLogger;

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
        FileLogger.log("AUTHENTICATION", "Login request started: email=" + email + ", device=" + deviceName + ", platform=" + platform);
        AuthApi authApi = ApiClient.getAuthServiceNoAuth(context);
        LoginRequest request = new LoginRequest(email, password, deviceName, platform);

        authApi.login(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                FileLogger.log("AUTHENTICATION", "Login response received: HTTP " + response.code() + ", isSuccessful=" + response.isSuccessful());
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();
                    FileLogger.log("AUTHENTICATION", "Login succeeded: User ID = " + authResponse.getUserId()
                            + ", Username = " + authResponse.getUsername()
                            + ", Access Token Exists = " + (authResponse.getAccessToken() != null)
                            + ", Refresh Token Exists = " + (authResponse.getRefreshToken() != null));
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
                    FileLogger.log("AUTHENTICATION", "Login failed: HTTP " + response.code() + ", error=" + errMsg);
                    callback.onError(errMsg);
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                FileLogger.log("AUTHENTICATION", "Login response received (Network failure): " + t.getMessage(), t);
                FileLogger.log("AUTHENTICATION", "Login failed: Network exception - " + t.getMessage());
                Log.e(TAG, "Login network failure", t);
                callback.onError("Network connection failure: " + t.getMessage());
            }
        });
    }

    public void register(String username, String email, String password, String deviceName, String platform, AuthCallback callback) {
        FileLogger.log("AUTHENTICATION", "Register request started: username=" + username + ", email=" + email);
        AuthApi authApi = ApiClient.getAuthServiceNoAuth(context);
        RegisterRequest request = new RegisterRequest(username, email, password, deviceName, platform);

        authApi.register(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                FileLogger.log("AUTHENTICATION", "Register response received: HTTP " + response.code() + ", isSuccessful=" + response.isSuccessful());
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();
                    FileLogger.log("AUTHENTICATION", "Register succeeded: User ID = " + authResponse.getUserId() + ", Username = " + authResponse.getUsername());
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
                    FileLogger.log("AUTHENTICATION", "Register failed: HTTP " + response.code() + ", error=" + errMsg);
                    callback.onError(errMsg);
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                FileLogger.log("AUTHENTICATION", "Register response received (Network failure): " + t.getMessage(), t);
                FileLogger.log("AUTHENTICATION", "Register failed: Network exception - " + t.getMessage());
                Log.e(TAG, "Registration network failure", t);
                callback.onError("Network connection failure: " + t.getMessage());
            }
        });
    }

    public void logout(AuthCallback callback) {
        String refreshToken = tokenManager.getRefreshToken();
        FileLogger.log("AUTHENTICATION", "Logout started: Refresh Token Exists = " + (refreshToken != null));
        if (refreshToken == null) {
            tokenManager.clearSession("AuthManager.logout (refreshToken is null)");
            com.example.reminder.sync.WebSocketManager.getInstance(context).disconnect();
            FileLogger.log("AUTHENTICATION", "Logout completed");
            callback.onSuccess();
            return;
        }

        AuthApi authApi = ApiClient.getAuthServiceNoAuth(context);
        authApi.logout(new RefreshTokenRequest(refreshToken)).enqueue(new Callback<Map<String, String>>() {
            @Override
            public void onResponse(Call<Map<String, String>> call, Response<Map<String, String>> response) {
                FileLogger.log("AUTHENTICATION", "Logout API response received: HTTP " + response.code());
                tokenManager.clearSession("AuthManager.logout API onResponse (HTTP " + response.code() + ")");
                com.example.reminder.sync.WebSocketManager.getInstance(context).disconnect();
                FileLogger.log("AUTHENTICATION", "Logout completed");
                callback.onSuccess();
            }

            @Override
            public void onFailure(Call<Map<String, String>> call, Throwable t) {
                FileLogger.log("AUTHENTICATION", "Logout API failed with network exception: " + t.getMessage() + ", wiping local session anyway.", t);
                Log.w(TAG, "Logout backend call failed, wiping local session anyway", t);
                tokenManager.clearSession("AuthManager.logout API onFailure (" + t.getMessage() + ")");
                com.example.reminder.sync.WebSocketManager.getInstance(context).disconnect();
                FileLogger.log("AUTHENTICATION", "Logout completed");
                callback.onSuccess();
            }
        });
    }

    private final Object refreshLock = new Object();

    public boolean isTokenExpired(String token) {
        FileLogger.log("TOKEN_REFRESH", "Access token expired check started");
        if (token == null || token.isEmpty()) {
            FileLogger.log("TOKEN_REFRESH", "Access token expired check: token is " + (token == null ? "null" : "empty") + " -> isExpired = true");
            return true;
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                FileLogger.log("TOKEN_REFRESH", "Access token expired check: invalid JWT structure -> isExpired = true");
                return true;
            }
            String payloadEnc = parts[1];
            byte[] bytes = android.util.Base64.decode(payloadEnc, android.util.Base64.DEFAULT);
            String payloadDec = new String(bytes, "UTF-8");
            org.json.JSONObject json = new org.json.JSONObject(payloadDec);
            if (json.has("exp")) {
                long exp = json.getLong("exp");
                long current = System.currentTimeMillis() / 1000;
                // Add a 10 seconds buffer
                boolean expired = exp <= (current + 10);
                FileLogger.log("TOKEN_REFRESH", "Access token expired check: exp=" + exp
                        + ", now=" + current
                        + ", remainingSec=" + (exp - current)
                        + ", buffer=10s, isExpired=" + expired);
                return expired;
            }
        } catch (Exception e) {
            FileLogger.log("TOKEN_REFRESH", "Access token expired check exception: " + e.getMessage(), e);
            Log.e(TAG, "Error checking token expiration", e);
        }
        return true;
    }

    public String getValidAccessToken() throws java.io.IOException {
        FileLogger.log("TOKEN_REFRESH", "getValidTokenOrRefresh() called");
        String token = tokenManager.getAccessToken();
        if (token != null && !isTokenExpired(token)) {
            FileLogger.log("TOKEN_REFRESH", "getValidTokenOrRefresh(): Stored access token is still valid. Returning without refresh.");
            return token;
        }

        FileLogger.log("TOKEN_REFRESH", "Access token expired check: Token is expired or missing. Acquiring refreshLock.");
        synchronized (refreshLock) {
            // Re-read token after acquiring lock in case another thread refreshed it
            token = tokenManager.getAccessToken();
            if (token != null && !isTokenExpired(token)) {
                FileLogger.log("TOKEN_REFRESH", "getValidTokenOrRefresh(): Access token refreshed by concurrent thread. Reusing.");
                Log.d(TAG, "Access token refreshed by another concurrent thread. Reusing.");
                return token;
            }

            String refreshToken = tokenManager.getRefreshToken();
            if (refreshToken == null) {
                FileLogger.log("TOKEN_REFRESH", "Refresh failed: Reason refresh failed: Stored refresh token is NULL! Cannot refresh.");
                throw new java.io.IOException("No refresh token available");
            }

            FileLogger.log("TOKEN_REFRESH", "Refresh started");
            FileLogger.log("TOKEN_REFRESH", "Refresh request sent: Calling /api/auth/refresh");
            Log.d(TAG, "Access token expired or missing. Centralized coordinator starting refresh...");
            AuthApi authApi = ApiClient.getAuthServiceNoAuth(context);
            retrofit2.Response<AuthResponse> response = authApi.refresh(new RefreshTokenRequest(refreshToken)).execute();

            FileLogger.log("TOKEN_REFRESH", "Refresh HTTP response code: " + response.code());

            if (response.isSuccessful() && response.body() != null) {
                AuthResponse authResponse = response.body();
                FileLogger.log("TOKEN_REFRESH", "Refresh succeeded");
                FileLogger.log("TOKEN_REFRESH", "New access token stored");
                FileLogger.log("TOKEN_REFRESH", "New refresh token stored");
                boolean persisted = tokenManager.saveSessionSynchronous(
                        authResponse.getAccessToken(),
                        authResponse.getRefreshToken(),
                        authResponse.getUserId(),
                        authResponse.getUsername()
                );
                if (!persisted) {
                    FileLogger.log("TOKEN_REFRESH", "Refresh failed: Synchronous session persistence (commit) returned false!");
                    Log.e(TAG, "Centralized refresh failed: unable to synchronously commit new session tokens to storage.");
                    throw new java.io.IOException("Failed to synchronously persist refreshed session tokens to disk");
                }
                Log.d(TAG, "Centralized refresh request completed successfully.");
                return authResponse.getAccessToken();
            } else {
                String errMsg = "Refresh failed: " + response.code();
                try {
                    if (response.errorBody() != null) {
                        errMsg = response.errorBody().string();
                    }
                } catch (Exception ignored) {}
                FileLogger.log("TOKEN_REFRESH", "Refresh failed: HTTP " + response.code() + ", Reason refresh failed: " + errMsg);
                Log.e(TAG, "Centralized refresh failed. Error: " + errMsg);
                throw new retrofit2.HttpException(response);
            }
        }
    }
}
