package com.example.reminder.config;

public final class ServerConfig {
    private ServerConfig() {}

    public static final String BASE_URL = "http://115.99.50.73:50000/";

    public static final String WS_BASE_URL = BASE_URL.replaceFirst("^http", "ws");

    public static String getServerHost() {
        return BASE_URL
                .replaceFirst("^https?://", "")
                .replaceAll("/$", "");
    }
}
