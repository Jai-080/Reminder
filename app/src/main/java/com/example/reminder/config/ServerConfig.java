package com.example.reminder.config;

import com.example.reminder.BuildConfig;

public final class ServerConfig {
    private ServerConfig() {}

    public static final String PRODUCTION_BASE_URL = "http://jaireminderapp.duckdns.org:50000/";
    public static final String DEV_EMULATOR_BASE_URL = "http://10.0.2.2:8088/";

    public static final String BASE_URL = BuildConfig.DEBUG ? DEV_EMULATOR_BASE_URL : PRODUCTION_BASE_URL;

    public static final String WS_BASE_URL = BASE_URL.replaceFirst("^http", "ws");

    public static String getServerHost() {
        return BASE_URL
                .replaceFirst("^https?://", "")
                .replaceAll("/$", "");
    }
}
