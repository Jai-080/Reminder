package com.example.reminder.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import androidx.appcompat.app.AppCompatDelegate;
import java.util.concurrent.CopyOnWriteArrayList;

public class ThemeManager {

    public static final int MODE_SYSTEM = 0;
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;

    private static final String PREFS_NAME = "app_theme_preferences";
    private static final String KEY_THEME_MODE = "key_theme_mode";

    public interface ThemeChangeListener {
        void onThemeChanged(int mode, boolean isDarkActive);
    }

    private static final CopyOnWriteArrayList<ThemeChangeListener> listeners = new CopyOnWriteArrayList<>();

    public static void init(Context context) {
        int savedMode = getThemeMode(context);
        applyMode(savedMode);
    }

    public static int getThemeMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_THEME_MODE, MODE_SYSTEM);
    }

    public static void setThemeMode(Context context, int mode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply();
        applyMode(mode);

        boolean isDark = isDarkActive(context);
        for (ThemeChangeListener listener : listeners) {
            listener.onThemeChanged(mode, isDark);
        }
    }

    public static void toggleLightDark(Context context) {
        boolean isCurrentlyDark = isDarkActive(context);
        int newMode = isCurrentlyDark ? MODE_LIGHT : MODE_DARK;
        setThemeMode(context, newMode);
    }

    public static boolean isDarkActive(Context context) {
        int mode = getThemeMode(context);
        if (mode == MODE_DARK) {
            return true;
        } else if (mode == MODE_LIGHT) {
            return false;
        } else {
            int currentNightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
        }
    }

    private static void applyMode(int mode) {
        switch (mode) {
            case MODE_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case MODE_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case MODE_SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    public static void registerListener(ThemeChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void unregisterListener(ThemeChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }
}

