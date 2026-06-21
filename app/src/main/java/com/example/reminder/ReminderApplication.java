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

    @Override
    public void onCreate() {
        super.onCreate();
        registerConnectivityMonitor();

        // Phase 13: Startup Sync and schedule Periodic Sync
        enqueueSyncWorker(this);
        schedulePeriodicSync(this);

        // Phase 11: Connect WebSocket on startup if already authenticated
        if (com.example.reminder.auth.TokenManager.getInstance(this).isLoggedIn()) {
            WebSocketManager.getInstance(this).connect();
        }
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
