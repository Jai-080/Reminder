package com.example.reminder.auth;

import com.example.reminder.config.ServerConfig;
import com.example.reminder.utils.FileLogger;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public class TokenManager {
    private static final String PREF_NAME = "secure_prefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_LAST_SYNC = "last_sync_timestamp";

    private static TokenManager instance;
    private SharedPreferences prefs;

    private TokenManager(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            prefs = EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            Log.d("TokenManager", "Initialized EncryptedSharedPreferences successfully.");
        } catch (Exception e) {
            Log.e("TokenManager", "Error creating EncryptedSharedPreferences, falling back to standard SharedPreferences", e);
            prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }

        String access = prefs.getString(KEY_ACCESS_TOKEN, null);
        String refresh = prefs.getString(KEY_REFRESH_TOKEN, null);
        boolean hasAccess = access != null;
        boolean hasRefresh = refresh != null;
        long userId = prefs.getLong(KEY_USER_ID, -1L);
        String username = prefs.getString(KEY_USERNAME, null);
        long lastSync = prefs.getLong(KEY_LAST_SYNC, 0L);

        FileLogger.log("SESSION", "Session restored / Session load completed: Access Token Exists = " + hasAccess
                + ", Refresh Token Exists = " + hasRefresh
                + ", User ID = " + (userId != -1L ? userId : "null")
                + ", Username = " + username
                + ", Base URL = " + getBaseUrl()
                + ", Last Sync Timestamp = " + lastSync);
    }

    public static synchronized TokenManager getInstance(Context context) {
        if (instance == null) {
            instance = new TokenManager(context.getApplicationContext());
        }
        return instance;
    }

    public synchronized void saveSession(String accessToken, String refreshToken, Long userId, String username) {
        boolean hasAccess = accessToken != null && !accessToken.isEmpty();
        boolean hasRefresh = refreshToken != null && !refreshToken.isEmpty();

        FileLogger.log("TOKEN_MANAGER", "saveSession(): User ID = " + userId
                + ", Username = " + username
                + ", Access Token Exists = " + hasAccess
                + ", Refresh Token Exists = " + hasRefresh
                + ", Base URL = " + getBaseUrl());
        FileLogger.log("SESSION", "Session persisted: saving session to secure storage");

        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putLong(KEY_USER_ID, userId != null ? userId : -1L)
                .putString(KEY_USERNAME, username)
                .apply();

        FileLogger.log("SESSION", "Session save completed");
        Log.d("TokenManager", "Session saved for user: " + username);
    }

    public synchronized void saveAccessToken(String accessToken) {
        boolean hasAccess = accessToken != null && !accessToken.isEmpty();
        FileLogger.log("TOKEN_MANAGER", "saveAccessToken(): Access Token Exists = " + hasAccess + " " + FileLogger.formatTokenMeta(accessToken));
        prefs.edit().putString(KEY_ACCESS_TOKEN, accessToken).apply();
        FileLogger.log("SESSION", "Session persisted: access token saved");
    }

    public synchronized void saveTokens(String accessToken, String refreshToken) {
        boolean hasAccess = accessToken != null && !accessToken.isEmpty();
        boolean hasRefresh = refreshToken != null && !refreshToken.isEmpty();
        FileLogger.log("TOKEN_MANAGER", "saveTokens(): Access Token Exists = " + hasAccess + ", Refresh Token Exists = " + hasRefresh);
        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply();
        FileLogger.log("SESSION", "Session persisted: tokens saved");
    }

    public synchronized void clearSession() {
        clearSession("Unspecified caller");
    }

    public synchronized void clearSession(String reason) {
        FileLogger.log("SESSION", "Session invalidated / Session cleared: Reason = " + reason);
        FileLogger.logClearSession("TOKEN_MANAGER", reason);
        prefs.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_USERNAME)
                .remove(KEY_LAST_SYNC)
                .apply();
        FileLogger.log("TOKEN_MANAGER", "Session cleared: Access Token Exists = false, Refresh Token Exists = false");
        Log.d("TokenManager", "Session cleared.");
    }

    public synchronized void logout() {
        FileLogger.log("TOKEN_MANAGER", "logout() called");
        clearSession("logout() invoked on TokenManager");
    }

    public synchronized String getAccessToken() {
        String token = prefs.getString(KEY_ACCESS_TOKEN, null);
        FileLogger.log("TOKEN_MANAGER", "Access token loaded: Access Token Exists = " + (token != null) + " " + FileLogger.formatTokenMeta(token));
        return token;
    }

    public synchronized String getRefreshToken() {
        String token = prefs.getString(KEY_REFRESH_TOKEN, null);
        FileLogger.log("TOKEN_MANAGER", "Refresh token loaded: Refresh Token Exists = " + (token != null) + " " + FileLogger.formatTokenMeta(token));
        return token;
    }

    public synchronized Long getUserId() {
        long id = prefs.getLong(KEY_USER_ID, -1L);
        return id == -1L ? null : id;
    }

    public synchronized String getUsername() {
        return prefs.getString(KEY_USERNAME, null);
    }

    public synchronized boolean isLoggedIn() {
        String token = prefs.getString(KEY_ACCESS_TOKEN, null);
        boolean loggedIn = token != null;
        FileLogger.log("TOKEN_MANAGER", "TokenManager.isLoggedIn(): Access Token Exists = " + loggedIn);
        return loggedIn;
    }

    public synchronized void setBaseUrl(String baseUrl) {
        if (baseUrl != null && !baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        prefs.edit().putString(KEY_BASE_URL, baseUrl).apply();
        FileLogger.log("TOKEN_MANAGER", "Base URL updated: " + baseUrl);
        Log.d("TokenManager", "Base URL updated: " + baseUrl);
    }

    public synchronized String getBaseUrl() {
        // Return default server address if none configured
        return ServerConfig.BASE_URL;
    }

    public synchronized void setLastSyncTimestamp(long timestamp) {
        FileLogger.log("TOKEN_MANAGER", "Last Sync Timestamp saved: " + timestamp);
        prefs.edit().putLong(KEY_LAST_SYNC, timestamp).apply();
    }

    public synchronized long getLastSyncTimestamp() {
        long timestamp = prefs.getLong(KEY_LAST_SYNC, 0L);
        FileLogger.log("TOKEN_MANAGER", "Last Sync Timestamp loaded: " + timestamp);
        return timestamp;
    }
}
