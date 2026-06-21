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
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.reminder.sync.SyncWorker;

import java.util.concurrent.TimeUnit;

public class ReminderApplication extends Application {
    private static final String TAG = "ReminderApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        registerConnectivityMonitor();
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
}
