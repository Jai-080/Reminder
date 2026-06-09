package com.example.remainder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import java.util.HashMap;
import java.util.Map;

public class PaymentNotificationService extends Service {

    private static final String CHANNEL_ID = "payment_reminder_channel";
    public static final String EXTRA_PAYMENT_NAME = "payment_name";
    public static final String EXTRA_PAYMENT_ID = "payment_id";
    public static final String ACTION_REMOVE = "action_remove_notification";
    public static final String ACTION_STOP_SERVICE = "action_stop_service";

    // ✅ Track all active payment notification IDs and their names
    private static final Map<Integer, String> activePayments = new HashMap<>();
    private static int foregroundId = -1;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;

        String action = intent.getAction();
        int paymentId = intent.getIntExtra(EXTRA_PAYMENT_ID, -1);
        String paymentName = intent.getStringExtra(EXTRA_PAYMENT_NAME);

        if (ACTION_STOP_SERVICE.equals(action)) {
            stopForegroundAndService();
            return START_NOT_STICKY;
        }

        if (ACTION_REMOVE.equals(action)) {
            removePaymentNotification(paymentId);
        } else {
            showPaymentNotification(paymentId, paymentName);
        }

        return START_STICKY;
    }

    private void showPaymentNotification(int paymentId, String paymentName) {
        if (paymentId == -1 || paymentName == null) return;

        createNotificationChannel();
        activePayments.put(paymentId, paymentName);

        Notification notification = createNotification(paymentName);

        // ✅ Always call startForeground to satisfy Android 14+ requirements
        startForeground(paymentId, notification);
        foregroundId = paymentId;
    }

    private void removePaymentNotification(int paymentId) {
        if (paymentId == -1) return;

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(paymentId);

        activePayments.remove(paymentId);

        if (activePayments.isEmpty()) {
            stopForegroundAndService();
        } else if (paymentId == foregroundId) {
            // If we removed the one currently used for foreground, pick another one to maintain foreground status
            int nextId = activePayments.keySet().iterator().next();
            String nextName = activePayments.get(nextId);
            if (nextName != null) {
                startForeground(nextId, createNotification(nextName));
                foregroundId = nextId;
            }
        }
    }

    private Notification createNotification(String paymentName) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("💳 Payment Due Today!")
                .setContentText(paymentName + " is due today. Don't forget to pay!")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSound(null)
                .setVibrate(null)
                .setOngoing(true)
                .build();
    }

    private void stopForegroundAndService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
        activePayments.clear();
        foregroundId = -1;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Payment Reminders",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setSound(null, null);
            channel.enableVibration(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}