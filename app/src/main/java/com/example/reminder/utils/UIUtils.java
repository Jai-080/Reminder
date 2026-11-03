package com.example.reminder.utils;

import android.content.Context;
import com.example.reminder.auth.TokenManager;
import com.example.reminder.config.ServerConfig;

public class UIUtils {

    /**
     * Translates technical backend/network exceptions and raw error traces into
     * clear, user-friendly messages while stripping internal URLs and hostnames.
     */
    public static String sanitizeError(Context context, String message) {
        if (message == null || message.trim().isEmpty()) {
            return "An unexpected error occurred. Please try again.";
        }

        String lower = message.toLowerCase();

        // 1. Network connectivity and resolution errors
        if (lower.contains("connectexception") || lower.contains("failed to connect")
                || lower.contains("unable to resolve host") || lower.contains("unknownhostexception")
                || lower.contains("no address associated") || lower.contains("network is unreachable")
                || lower.contains("enonet")) {
            return "Unable to connect to the server. Please check your internet connection.";
        }

        // 2. Timeout errors
        if (lower.contains("sockettimeoutexception") || lower.contains("timed out") || lower.contains("timeout")) {
            return "Connection timed out. Please try again.";
        }

        // 3. SSL / Security handshake failures
        if (lower.contains("sslhandshakeexception") || lower.contains("certpathvalidatorexception")
                || lower.contains("sslpeerunverifiedexception")) {
            return "Secure connection to server failed. Please verify your network security settings.";
        }

        // 4. Authentication / Authorization errors
        if (lower.contains("401") || lower.contains("unauthorized")) {
            return "Session expired or invalid credentials. Please sign in again.";
        }
        if (lower.contains("403") || lower.contains("forbidden")) {
            return "Access denied. You do not have permission to perform this action.";
        }

        // 5. Server-side failures (5xx)
        if (lower.contains("500") || lower.contains("502") || lower.contains("503") || lower.contains("504")
                || lower.contains("internal server error") || lower.contains("bad gateway")) {
            return "Server is temporarily unavailable. Please try again later.";
        }

        // 6. JSON / Deserialization parsing failures
        if (lower.contains("jsonsyntaxexception") || lower.contains("malformedjsonexception")
                || lower.contains("jsonparseexception")) {
            return "Received an invalid response format from the server.";
        }

        // 7. Generic raw unhandled Java exceptions
        if (message.contains("java.lang.") || message.contains("java.net.") || message.contains("Exception:")) {
            return "An unexpected error occurred. Please try again.";
        }

        // 8. Sanitize raw URLs/IPs from standard business messages
        try {
            if (context != null) {
                TokenManager tokenManager = TokenManager.getInstance(context);
                String baseUrl = tokenManager.getBaseUrl();
                if (baseUrl != null && !baseUrl.isEmpty()) {
                    message = message.replace(baseUrl, "the server");
                    String cleanUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
                    message = message.replace(cleanUrl, "the server");
                    String hostOnly = cleanUrl.replace("http://", "").replace("https://", "");
                    if (!hostOnly.isEmpty()) {
                        message = message.replace(hostOnly, "the server");
                    }
                }
            }
        } catch (Exception ignored) {}

        // Fallbacks for known development hosts
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
