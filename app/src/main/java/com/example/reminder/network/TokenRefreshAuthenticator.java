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
            forceLogout("Response count exceeded 3 retries");
            return null;
        }

        // Get the current refresh token in TokenManager to compare on failures
        String originalRefreshToken = tokenManager.getRefreshToken();
        if (originalRefreshToken == null) {
            forceLogout("No refresh token available");
            return null;
        }

        try {
            // Centralized coordinator gets or refreshes the access token
            com.example.reminder.auth.AuthManager authManager = com.example.reminder.auth.AuthManager.getInstance(context);
            String validAccessToken = authManager.getValidAccessToken();

            Log.d(TAG, "Obtained valid access token from coordinator. Retrying request.");
            return response.request().newBuilder()
                    .header("Authorization", "Bearer " + validAccessToken)
                    .build();
        } catch (Exception e) {
            // Refresh failed. Let's perform the Safety Check.
            String currentRefreshToken = tokenManager.getRefreshToken();
            
            if (currentRefreshToken != null && !currentRefreshToken.equals(originalRefreshToken)) {
                // Another thread has refreshed successfully in the meantime!
                // Do not log out. Just load the latest access token and retry.
                String newAccessToken = tokenManager.getAccessToken();
                if (newAccessToken != null) {
                    Log.d(TAG, "Refresh failed, but stored refresh token changed. Assuming concurrent refresh succeeded. Retrying request with new access token.");
                    return response.request().newBuilder()
                            .header("Authorization", "Bearer " + newAccessToken)
                            .build();
                }
            }

            // If it's a network/timeout exception (not a server rejection), do not log out
            if (e instanceof IOException && !(e instanceof retrofit2.HttpException)) {
                Log.w(TAG, "Refresh failed due to network exception. Not logging out.", e);
                throw (IOException) e;
            }

            Log.e(TAG, "Refresh genuinely failed. Forcing logout.", e);
            forceLogout("Refresh genuinely failed: " + e.getMessage());
            return null;
        }
    }

    private int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) {
            result++;
        }
        return result;
    }

    private void forceLogout(String reason) {
        Log.w(TAG, "forceLogout() triggered. Reason: " + reason);
        tokenManager.clearSession();
        com.example.reminder.sync.WebSocketManager.getInstance(context).disconnect();
        Intent intent = new Intent(context, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("launch_reason", "auth_failure");
        context.startActivity(intent);
        Log.w(TAG, "Redirected user to LoginActivity due to authentication failure.");
    }
}
