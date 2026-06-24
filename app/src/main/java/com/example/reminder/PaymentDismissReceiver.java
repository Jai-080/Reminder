package com.example.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class PaymentDismissReceiver extends BroadcastReceiver {
    private static final String TAG = "PaymentDismissReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        int paymentId = intent.getIntExtra("payment_id", -1);
        String paymentName = intent.getStringExtra("payment_name");

        Log.d(TAG, "Notification dismissed for paymentId=" + paymentId + ", paymentName=" + paymentName);

        if (paymentId == -1 || paymentName == null) return;

        try {
            PaymentDatabaseHelper dbHelper = new PaymentDatabaseHelper(context);
            java.util.ArrayList<MonthlyPayment> payments = dbHelper.getAllPayments();
            for (MonthlyPayment p : payments) {
                if (p.getId() == paymentId) {
                    if (!p.isRecentlyPaid()) {
                        Log.d(TAG, "Payment is still unpaid. Recreating notification.");
                        AlarmUtils.showMonthlyPaymentNotification(context, p.getId(), p.getName());
                    } else {
                        Log.d(TAG, "Payment has been marked paid. Not recreating notification.");
                    }
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking payment status on notification dismiss", e);
        }
    }
}
