package com.example.reminder.network;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.reminder.auth.LoginActivity;
import com.example.reminder.auth.TokenManager;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

public class TokenRefreshAuthenticator implements Authenticator {
    private static final String TAG = "TokenAuthenticator";
    private final Context context;
    private final TokenManager tokenManager;

    public TokenRefreshAuthenticator(Context context, TokenManager tokenManager) {
        this.context = context.getApplicationContext();
        this.tokenManager = tokenManager;
    }

    @Override
    public Request authenticate(Route route, Response response) throws IOException {
        // 1. Avoid infinite loops of retries
        if (responseCount(response) >= 3) {
            forceLogout();
            return null;
        }

        synchronized (this) {
            String currentToken = tokenManager.getAccessToken();
            String requestToken = response.request().header("Authorization");

            // Strip "Bearer " prefix if present to compare
            if (requestToken != null && requestToken.startsWith("Bearer ")) {
                requestToken = requestToken.substring(7);
            }

            // If the token was already refreshed by another concurrent request, retry with the new one
            if (currentToken != null && !currentToken.equals(requestToken)) {
                return response.request().newBuilder()
                        .header("Authorization", "Bearer " + currentToken)
                        .build();
            }

            // Try to refresh using refresh token
            String refreshToken = tokenManager.getRefreshToken();
            if (refreshToken == null) {
                forceLogout();
                return null;
            }

            Log.d(TAG, "Access token expired. Attempting token refresh...");
            Log.d("TokenRefreshAuthenticator", "Received 401. Attempting token refresh.");

            // Use the unauthenticated client to prevent interceptor/authenticator loop
            AuthApi authApi = ApiClient.getAuthServiceNoAuth(context);
            retrofit2.Response<AuthResponse> refreshResponse = authApi.refresh(new RefreshTokenRequest(refreshToken)).execute();

            if (refreshResponse.isSuccessful() && refreshResponse.body() != null) {
                AuthResponse newTokens = refreshResponse.body();
                Log.d(TAG, "Token refresh succeeded. Saving new access token.");
                Log.d("TokenRefreshAuthenticator", "Token refresh successful. Retrying request.");
                tokenManager.saveSession(
                        newTokens.getAccessToken(),
                        newTokens.getRefreshToken(),
                        newTokens.getUserId(),
                        newTokens.getUsername()
                );

                return response.request().newBuilder()
                        .header("Authorization", "Bearer " + newTokens.getAccessToken())
                        .build();
            } else {
                Log.e(TAG, "Token refresh failed (HTTP " + refreshResponse.code() + "). Forcing logout.");
                forceLogout();
                return null;
            }
        }
    }

    private int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) {
            result++;
        }
        return result;
    }

    private void forceLogout() {
        tokenManager.clearSession();
        com.example.reminder.sync.WebSocketManager.getInstance(context).disconnect();
        Intent intent = new Intent(context, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
        Log.w(TAG, "Redirected user to LoginActivity due to authentication failure.");
    }
}
