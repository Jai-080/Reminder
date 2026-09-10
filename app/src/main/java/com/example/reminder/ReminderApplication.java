package com.example.reminder;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.reminder.sync.SyncWorker;
import com.example.reminder.sync.WebSocketManager;

import java.util.concurrent.TimeUnit;

public class ReminderApplication extends Application {
    private static final String TAG = "ReminderApplication";

    private static boolean sIsColdStart = true;
    private static int sActiveActivityCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();

        // 1. Uncaught Exception Handler
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                com.example.reminder.utils.FileLogger.log("EXCEPTION", "Uncaught exception on thread: " + thread.getName()
                        + " | Type: " + throwable.getClass().getName()
                        + " | Message: " + throwable.getMessage(), throwable);
            } catch (Throwable ignored) {}
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });

        // 2. Application & Process Lifecycle Logging
        int pid = android.os.Process.myPid();
        com.example.reminder.utils.FileLogger.log("APP", "Application started");
        com.example.reminder.utils.FileLogger.log("APP", "ReminderApplication.onCreate()");
        com.example.reminder.utils.FileLogger.log("APP", "Process ID (PID): " + pid);
        com.example.reminder.utils.FileLogger.log("APP", "Application version: " + BuildConfig.VERSION_NAME
                + " | Build version: " + BuildConfig.VERSION_CODE
                + " | Build type: " + BuildConfig.BUILD_TYPE);

        if (sIsColdStart) {
            com.example.reminder.utils.FileLogger.log("APP", "Cold start detected (PID=" + pid + ")");
            sIsColdStart = false;
        } else {
            com.example.reminder.utils.FileLogger.log("APP", "Application recreated / Process recreated (PID=" + pid + ")");
        }

        // 3. Activity Lifecycle Tracking (Foreground / Background / Warm Start)
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull android.app.Activity activity, android.os.Bundle savedInstanceState) {
                if (savedInstanceState != null) {
                    com.example.reminder.utils.FileLogger.log("APP", activity.getClass().getSimpleName() + " recreated from savedInstanceState");
                }
            }

            @Override
            public void onActivityStarted(@NonNull android.app.Activity activity) {
                sActiveActivityCount++;
                if (sActiveActivityCount == 1) {
                    com.example.reminder.utils.FileLogger.log("APP", "Application moved to foreground" + (!sIsColdStart ? " (Warm start)" : ""));
                }
            }

            @Override
            public void onActivityResumed(@NonNull android.app.Activity activity) {}

            @Override
            public void onActivityPaused(@NonNull android.app.Activity activity) {}

            @Override
            public void onActivityStopped(@NonNull android.app.Activity activity) {
                sActiveActivityCount--;
                if (sActiveActivityCount <= 0) {
                    sActiveActivityCount = 0;
                    com.example.reminder.utils.FileLogger.log("APP", "Application moved to background");
                }
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull android.app.Activity activity, @NonNull android.os.Bundle outState) {}

            @Override
            public void onActivityDestroyed(@NonNull android.app.Activity activity) {}
        });

        // 4. Initial Auth state
        com.example.reminder.utils.FileLogger.log("APP", "TokenManager.isLoggedIn() = " + com.example.reminder.auth.TokenManager.getInstance(this).isLoggedIn());

        registerConnectivityMonitor();

        // Phase 13: Startup Sync and schedule Periodic Sync
        enqueueSyncWorker(this);
        schedulePeriodicSync(this);

        // Phase 11: Connect WebSocket on startup if already authenticated
        if (com.example.reminder.auth.TokenManager.getInstance(this).isLoggedIn()) {
            com.example.reminder.utils.FileLogger.log("APP", "User already logged in at startup. Initiating WebSocket connect().");
            WebSocketManager.getInstance(this).connect();
        }

        // Restore due payment notifications/alarms on app launch/update
        AlarmUtils.restoreDuePaymentNotifications(this);
    }

    private void registerConnectivityMonitor() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        connectivityManager.registerNetworkCallback(networkRequest, new ConnectivityManager.NetworkCallback() {
            private boolean isFirstCallback = true;

            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                com.example.reminder.utils.FileLogger.log("NETWORK", "Connectivity callback triggered: onAvailable");
                com.example.reminder.utils.FileLogger.log("NETWORK", "Internet available");

                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    com.example.reminder.utils.FileLogger.log("NETWORK", "Wi-Fi connected");
                }

                Log.d(TAG, "Network connection available. Enqueuing sync worker.");
                if (isFirstCallback) {
                    // Skip the initial check on application launch to avoid redundant work triggers
                    isFirstCallback = false;
                    return;
                }
                enqueueSyncWorker(ReminderApplication.this);
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                com.example.reminder.utils.FileLogger.log("NETWORK", "Connectivity callback triggered: onLost");
                com.example.reminder.utils.FileLogger.log("NETWORK", "Internet unavailable");
                com.example.reminder.utils.FileLogger.log("NETWORK", "Wi-Fi disconnected");
                Log.d(TAG, "Network connection lost.");
            }
        });
    }

    public static void enqueueSyncWorker(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        10, // 10 seconds initial backoff
                        TimeUnit.SECONDS
                )
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                "background_sync",
                ExistingWorkPolicy.KEEP,
                request
        );
        Log.d(TAG, "SyncWorker enqueued");
        System.out.println("SyncWorker enqueued");
        Log.d(TAG, "Enqueued unique SyncWorker with network constraints.");
    }

    public static void schedulePeriodicSync(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest periodicRequest = new PeriodicWorkRequest.Builder(
                SyncWorker.class,
                15, TimeUnit.MINUTES
        )
                .setConstraints(constraints)
                .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        10,
                        TimeUnit.SECONDS
                )
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "periodic_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
        );
        Log.d(TAG, "Enqueued periodic sync worker (15 minutes interval) with network constraints.");
    }
}
