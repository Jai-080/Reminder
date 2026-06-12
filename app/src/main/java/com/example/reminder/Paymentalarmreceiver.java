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

        // ✅ Directly show persistent notification to bypass foreground service background limits
        AlarmUtils.showMonthlyPaymentNotification(context, paymentId, paymentName);
    }
}
