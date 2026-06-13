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

        long scheduledTime = intent.getLongExtra("scheduled_time", -1L);
        long actualTime = System.currentTimeMillis();
        android.util.Log.d("Paymentalarmreceiver", "Alarm fired: reminder id=" + paymentId + ", actual fire time=" + actualTime);
        if (scheduledTime != -1L) {
            long delay = actualTime - scheduledTime;
            android.util.Log.d("Paymentalarmreceiver", "Delay: " + delay + " ms");
        }

        // ✅ Directly show persistent notification to bypass foreground service background limits
        AlarmUtils.showMonthlyPaymentNotification(context, paymentId, paymentName);
    }
}
