package com.example.remainder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import java.util.HashSet;
import java.util.Set;

public class PaymentNotificationService extends Service {

    private static final String CHANNEL_ID = "payment_reminder_channel";
    public static final String EXTRA_PAYMENT_NAME = "payment_name";
    public static final String EXTRA_PAYMENT_ID = "payment_id";
    public static final String ACTION_REMOVE = "action_remove_notification";

    // ✅ Track all active payment notification IDs
    private static final Set<Integer> activePaymentIds = new HashSet<>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;

        String action = intent.getAction();
        int paymentId = intent.getIntExtra(EXTRA_PAYMENT_ID, -1);
        String paymentName = intent.getStringExtra(EXTRA_PAYMENT_NAME);

        if (ACTION_REMOVE.equals(action)) {
            // ✅ Remove only the specific payment notification
            removePaymentNotification(paymentId);
        } else {
            // ✅ Show a new payment notification
            showPaymentNotification(paymentId, paymentName);
        }

        return START_STICKY;
    }

    private void showPaymentNotification(int paymentId, String paymentName) {
        if (paymentId == -1 || paymentName == null) return;

        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("💳 Payment Due Today!")
                .setContentText(paymentName + " is due today. Don't forget to pay!")
                .setPriority(NotificationCompat.PRIORITY_LOW)  // ✅ Silent
                .setSound(null)                                 // ✅ No sound
                .setVibrate(null)                               // ✅ No vibration
                .setOngoing(true)                               // ✅ Can't be swiped
                .build();

        activePaymentIds.add(paymentId);

        // ✅ First payment uses startForeground, rest use notifyManager directly
        if (activePaymentIds.size() == 1) {
            startForeground(paymentId, notification);
        } else {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.notify(paymentId, notification);
        }
    }

    private void removePaymentNotification(int paymentId) {
        if (paymentId == -1) return;

        // ✅ Cancel this specific notification
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(paymentId);

        activePaymentIds.remove(paymentId);

        // ✅ If no more active payments, stop the service entirely
        if (activePaymentIds.isEmpty()) {
            stopSelf();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Payment Reminders",
                    NotificationManager.IMPORTANCE_LOW  // ✅ Silent channel
            );
            channel.setSound(null, null);       // ✅ No sound
            channel.enableVibration(false);     // ✅ No vibration
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}