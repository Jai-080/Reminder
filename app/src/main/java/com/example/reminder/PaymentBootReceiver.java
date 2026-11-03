package com.example.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class PaymentBootReceiver extends BroadcastReceiver {
    private static final String TAG = "PaymentBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || 
                "android.intent.action.MY_PACKAGE_REPLACED".equals(intent.getAction()))) {
            Log.d(TAG, "Boot completed or package updated, restoring alarms asynchronously...");
            final PendingResult pendingResult = goAsync();
            new Thread(() -> {
                try {
                    AlarmUtils.restoreDuePaymentNotifications(context);
                    AlarmUtils.restorePendingReminders(context);
                    Log.d(TAG, "Reboot restoration of payments and reminders completed successfully.");
                } catch (Exception e) {
                    Log.e(TAG, "Error restoring alarms during boot", e);
                } finally {
                    pendingResult.finish();
                }
            }).start();
        }
    }
}
