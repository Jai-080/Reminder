package com.example.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class Paymentalarmreceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int paymentId = intent.getIntExtra("payment_id", -1);
        String paymentName = intent.getStringExtra("payment_name");

        if (paymentId == -1 || paymentName == null || paymentName.isEmpty()) return;

        // ✅ Start the foreground service which shows the persistent silent notification
        Intent serviceIntent = new Intent(context, PaymentNotificationService.class);
        serviceIntent.putExtra(PaymentNotificationService.EXTRA_PAYMENT_NAME, paymentName);
        serviceIntent.putExtra(PaymentNotificationService.EXTRA_PAYMENT_ID, paymentId);

        context.startForegroundService(serviceIntent);
    }
}
