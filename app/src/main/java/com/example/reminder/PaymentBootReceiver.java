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
            Log.d(TAG, "Boot completed or package updated, restoring due payment notifications...");
            AlarmUtils.restoreDuePaymentNotifications(context);
        }
    }
}
