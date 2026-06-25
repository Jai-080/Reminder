package com.example.reminder.utils;

import android.content.Context;
import com.example.reminder.auth.TokenManager;
import com.example.reminder.config.ServerConfig;

public class UIUtils {
    public static String sanitizeError(Context context, String message) {
        if (message == null) {
            return null;
        }
        try {
            TokenManager tokenManager = TokenManager.getInstance(context);
            String baseUrl = tokenManager.getBaseUrl();
            if (baseUrl != null && !baseUrl.isEmpty()) {
                message = message.replace(baseUrl, "the server");
                // Strip trailing slash if present
                String cleanUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
                message = message.replace(cleanUrl, "the server");
                String hostOnly = cleanUrl.replace("http://", "").replace("https://", "");
                if (!hostOnly.isEmpty()) {
                    message = message.replace(hostOnly, "the server");
                }
            }
        } catch (Exception ignored) {}

        // Fallbacks
        String fallbackHost = ServerConfig.getServerHost();
        if (!fallbackHost.isEmpty()) {
            message = message.replace(fallbackHost, "the server");
            if (fallbackHost.contains(":")) {
                message = message.replace(fallbackHost.split(":")[0], "the server");
            }
        }
        message = message.replace("localhost:8080", "the server")
                         .replace("10.0.2.2:8080", "the server");
        return message;
    }
}
