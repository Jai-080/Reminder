package com.example.reminder.auth;

import com.example.reminder.config.ServerConfig;
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
    }

    public static synchronized TokenManager getInstance(Context context) {
        if (instance == null) {
            instance = new TokenManager(context.getApplicationContext());
        }
        return instance;
    }

    public synchronized void saveSession(String accessToken, String refreshToken, Long userId, String username) {
        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putLong(KEY_USER_ID, userId != null ? userId : -1L)
                .putString(KEY_USERNAME, username)
                .apply();
        Log.d("TokenManager", "Session saved for user: " + username);
    }

    public synchronized void saveAccessToken(String accessToken) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, accessToken).apply();
    }

    public synchronized void saveTokens(String accessToken, String refreshToken) {
        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply();
    }

    public synchronized void clearSession() {
        prefs.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_USERNAME)
                .remove(KEY_LAST_SYNC)
                .apply();
        Log.d("TokenManager", "Session cleared.");
    }

    public synchronized String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public synchronized String getRefreshToken() {
        return prefs.getString(KEY_REFRESH_TOKEN, null);
    }

    public synchronized Long getUserId() {
        long id = prefs.getLong(KEY_USER_ID, -1L);
        return id == -1L ? null : id;
    }

    public synchronized String getUsername() {
        return prefs.getString(KEY_USERNAME, null);
    }

    public synchronized boolean isLoggedIn() {
        return getAccessToken() != null;
    }

    public synchronized void setBaseUrl(String baseUrl) {
        if (baseUrl != null && !baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        prefs.edit().putString(KEY_BASE_URL, baseUrl).apply();
        Log.d("TokenManager", "Base URL updated: " + baseUrl);
    }

    public synchronized String getBaseUrl() {
        // Return default server address if none configured
        return ServerConfig.BASE_URL;
    }

    public synchronized void setLastSyncTimestamp(long timestamp) {
        prefs.edit().putLong(KEY_LAST_SYNC, timestamp).apply();
    }

    public synchronized long getLastSyncTimestamp() {
        return prefs.getLong(KEY_LAST_SYNC, 0L);
    }
}
