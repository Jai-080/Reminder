package com.example.reminder.network;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.reminder.auth.LoginActivity;
import com.example.reminder.auth.TokenManager;
import com.example.reminder.utils.FileLogger;

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
        String requestUrl = response.request().url().toString();
        FileLogger.log("TOKEN_REFRESH", "TokenRefreshAuthenticator.authenticate() called");
        FileLogger.log("TOKEN_REFRESH", "401 received: URL=" + requestUrl + ", method=" + response.request().method());

        int count = responseCount(response);
        FileLogger.log("TOKEN_REFRESH", "responseCount() = " + count);

        // 1. Avoid infinite loops of retries
        if (count >= 3) {
            FileLogger.log("TOKEN_REFRESH", "forceLogout() triggered. Reason for forceLogout(): Response count exceeded 3 retries (" + count + ") for " + requestUrl);
            forceLogout("Response count exceeded 3 retries for " + requestUrl);
            return null;
        }

        // Get current refresh token to compare on failures
        String originalRefreshToken = tokenManager.getRefreshToken();
        if (originalRefreshToken == null) {
            FileLogger.log("TOKEN_REFRESH", "forceLogout() triggered. Reason for forceLogout(): Stored refresh token is null for " + requestUrl);
            forceLogout("No refresh token available for " + requestUrl);
            return null;
        }

        try {
            FileLogger.log("TOKEN_REFRESH", "Refresh started: Requesting valid access token from AuthManager");
            com.example.reminder.auth.AuthManager authManager = com.example.reminder.auth.AuthManager.getInstance(context);
            String validAccessToken = authManager.getValidAccessToken();

            FileLogger.log("TOKEN_REFRESH", "Refresh succeeded: Valid access token acquired");
            FileLogger.log("TOKEN_REFRESH", "Retry original request: " + requestUrl);
            Log.d(TAG, "Obtained valid access token from coordinator. Retrying request.");
            return response.request().newBuilder()
                    .header("Authorization", "Bearer " + validAccessToken)
                    .build();
        } catch (Exception e) {
            FileLogger.log("TOKEN_REFRESH", "Refresh failed: Reason refresh failed: " + e.getClass().getSimpleName() + " - " + e.getMessage(), e);

            // Safety check: Did another concurrent thread refresh successfully?
            String currentRefreshToken = tokenManager.getRefreshToken();
            FileLogger.log("TOKEN_REFRESH", "Safety check: comparing refresh tokens. original="
                    + FileLogger.formatTokenMeta(originalRefreshToken)
                    + ", current=" + FileLogger.formatTokenMeta(currentRefreshToken));

            if (currentRefreshToken != null && !currentRefreshToken.equals(originalRefreshToken)) {
                String newAccessToken = tokenManager.getAccessToken();
                if (newAccessToken != null) {
                    FileLogger.log("TOKEN_REFRESH", "Safety check PASSED: Concurrent thread refreshed tokens. Retry original request with new access token.");
                    Log.d(TAG, "Refresh failed, but stored refresh token changed. Assuming concurrent refresh succeeded. Retrying request with new access token.");
                    return response.request().newBuilder()
                            .header("Authorization", "Bearer " + newAccessToken)
                            .build();
                }
            }

            // If it's a network/timeout exception (not a server HTTP rejection), do not log out
            if (e instanceof IOException && !(e instanceof retrofit2.HttpException)) {
                FileLogger.log("TOKEN_REFRESH", "Refresh failed due to network/timeout exception. NOT logging out. Propagating IOException.");
                Log.w(TAG, "Refresh failed due to network exception. Not logging out.", e);
                throw (IOException) e;
            }

            FileLogger.log("TOKEN_REFRESH", "forceLogout() triggered. Reason for forceLogout(): Refresh genuinely failed: " + e.getMessage());
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
        FileLogger.log("TOKEN_REFRESH", "forceLogout() executed. Reason for forceLogout(): " + reason);
        Log.w(TAG, "forceLogout() triggered. Reason: " + reason);
        tokenManager.clearSession("TokenRefreshAuthenticator.forceLogout (" + reason + ")");
        com.example.reminder.sync.WebSocketManager.getInstance(context).disconnect();
        Intent intent = new Intent(context, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("launch_reason", "forceLogout: " + reason);
        context.startActivity(intent);
        FileLogger.log("TOKEN_REFRESH", "Redirecting to LoginActivity. Reason for redirect: forceLogout (" + reason + ")");
        Log.w(TAG, "Redirected user to LoginActivity due to authentication failure.");
    }
}
