package com.example.remainder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

/**
 * This BroadcastReceiver is triggered by AlarmManager at the payment due date.
 * It shows the notification ONLY when the due date arrives — not immediately.
 */
public class Paymentalarmreceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "payment_reminder_channel";
    private static final String CHANNEL_NAME = "Payment Reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        int paymentId = intent.getIntExtra("payment_id", -1);
        String paymentName = intent.getStringExtra("payment_name");

        if (paymentName == null || paymentName.isEmpty()) return;

        showNotification(context, paymentId, paymentName);
    }

    private void showNotification(Context context, int paymentId, String paymentName) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        // Create notification channel for Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Reminders for monthly payments");
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification) // make sure this drawable exists in your project
                .setContentTitle("💳 Payment Due Today!")
                .setContentText(paymentName + " is due today. Don't forget to pay!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        // Use paymentId as notification ID so each payment shows separately
        manager.notify(paymentId, builder.build());
    }
}