package com.example.reminder.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.reminder.utils.FileLogger;

import java.util.concurrent.CountDownLatch;

public class SyncWorker extends Worker {
    private static final String TAG = "SyncWorker";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        FileLogger.log("SYNC_WORKER", "Worker started");
        FileLogger.log("SYNC_WORKER", "WorkManager execution thread: " + Thread.currentThread().getName());
        Log.d(TAG, "SyncWorker started");
        System.out.println("SyncWorker started");
        Log.d(TAG, "SyncWorker executing background synchronization...");
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] success = {false};

        SyncManager.getInstance(getApplicationContext()).performFullSync(new SyncManager.SyncCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                FileLogger.log("SYNC_WORKER", "Worker completed");
                Log.d(TAG, "SyncWorker synchronization completed successfully.");
                success[0] = true;
                latch.countDown();
            }

            @Override
            public void onError(String error) {
                FileLogger.log("SYNC_WORKER", "Worker failed: " + error);
                Log.e(TAG, "SyncWorker synchronization failed: " + error);
                success[0] = false;
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            FileLogger.log("SYNC_WORKER", "Worker failed: Execution interrupted: " + e.getMessage(), e);
            Log.e(TAG, "SyncWorker execution interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return Result.retry();
        }

        if (success[0]) {
            return Result.success();
        } else {
            Log.d(TAG, "SyncWorker rescheduling work with exponential backoff due to failure.");
            return Result.retry();
        }
    }
}
