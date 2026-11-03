package com.example.reminder.utils;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import java.util.concurrent.CopyOnWriteArrayList;

public class ThemeManager {

    public static final int MODE_SYSTEM = 0;
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;

    private static final String PREFS_NAME = "app_theme_preferences";
    private static final String KEY_THEME_MODE = "key_theme_mode";

    private static Bitmap transitionScreenshot = null;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final Runnable cleanupRunnable = () -> {
        if (transitionScreenshot != null && !transitionScreenshot.isRecycled()) {
            transitionScreenshot.recycle();
            transitionScreenshot = null;
        }
    };

    public interface ThemeChangeListener {
        void onThemeChanged(int mode, boolean isDarkActive);
    }

    private static final CopyOnWriteArrayList<ThemeChangeListener> listeners = new CopyOnWriteArrayList<>();

    public static void init(Context context) {
        int savedMode = getThemeMode(context);
        applyMode(savedMode);

        if (context instanceof Application) {
            ((Application) context).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}

                @Override
                public void onActivityStarted(@NonNull Activity activity) {
                    applyTransitionOverlay(activity);
                }

                @Override
                public void onActivityResumed(@NonNull Activity activity) {}

                @Override
                public void onActivityPaused(@NonNull Activity activity) {}

                @Override
                public void onActivityStopped(@NonNull Activity activity) {}

                @Override
                public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

                @Override
                public void onActivityDestroyed(@NonNull Activity activity) {}
            });
        }
    }

    public static int getThemeMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_THEME_MODE, MODE_SYSTEM);
    }

    public static void setThemeMode(Context context, int mode) {
        int currentMode = getThemeMode(context);
        boolean currentDark = isDarkActive(context);

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply();

        // Check if appearance actually changes
        boolean willChange;
        if (mode == MODE_DARK) {
            willChange = !currentDark;
        } else if (mode == MODE_LIGHT) {
            willChange = currentDark;
        } else {
            int currentNightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            boolean systemDark = (currentNightMode == Configuration.UI_MODE_NIGHT_YES);
            willChange = (systemDark != currentDark);
        }

        if (willChange) {
            captureScreenshot(context);
        }

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

    private static Activity scanForActivity(Context context) {
        if (context == null) return null;
        if (context instanceof Activity) return (Activity) context;
        if (context instanceof ContextWrapper) {
            return scanForActivity(((ContextWrapper) context).getBaseContext());
        }
        return null;
    }

    public static void captureScreenshot(Context context) {
        Activity activity = scanForActivity(context);
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        try {
            View decorView = activity.getWindow().getDecorView();
            int w = decorView.getWidth();
            int h = decorView.getHeight();
            if (w <= 0 || h <= 0) return;

            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            decorView.draw(canvas);

            if (transitionScreenshot != null && !transitionScreenshot.isRecycled()) {
                transitionScreenshot.recycle();
            }
            transitionScreenshot = bitmap;

            mainHandler.removeCallbacks(cleanupRunnable);
            mainHandler.postDelayed(cleanupRunnable, 1500);

            try {
                activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            } catch (Exception ignored) {}
        } catch (Throwable t) {
            android.util.Log.e("ThemeManager", "Failed to capture transition screenshot", t);
        }
    }

    public static void applyTransitionOverlay(Activity activity) {
        if (transitionScreenshot == null || transitionScreenshot.isRecycled()) {
            return;
        }

        mainHandler.removeCallbacks(cleanupRunnable);
        final Bitmap bitmap = transitionScreenshot;
        transitionScreenshot = null;

        try {
            final ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
            final ImageView overlay = new ImageView(activity);
            overlay.setImageBitmap(bitmap);
            overlay.setScaleType(ImageView.ScaleType.FIT_XY);
            overlay.setClickable(true);
            overlay.setFocusable(false);

            decorView.addView(overlay, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            overlay.post(() -> {
                overlay.animate()
                        .alpha(0f)
                        .setDuration(550)
                        .setInterpolator(new FastOutSlowInInterpolator())
                        .withEndAction(() -> {
                            try {
                                decorView.removeView(overlay);
                                if (!bitmap.isRecycled()) {
                                    bitmap.recycle();
                                }
                            } catch (Exception ignored) {}
                        })
                        .start();
            });
        } catch (Throwable t) {
            android.util.Log.e("ThemeManager", "Error applying transition overlay", t);
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
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
