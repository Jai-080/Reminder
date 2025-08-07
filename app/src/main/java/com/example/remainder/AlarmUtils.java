package com.example.remainder;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class AlarmUtils {
    private static final String CHANNEL_ID = "reminder_channel";

    // ✅ Schedule a timed monthly payment reminder
    public static void schedulePaymentReminder(Context context, int paymentId, String paymentName, long triggerAtMillis) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("reminderId", paymentId);
        intent.putExtra("reminderText", paymentName);
        intent.putExtra("isMonthlyPayment", true); // Used to differentiate in receiver

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                paymentId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
        }
    }

    // ✅ Show permanent notification for monthly payments
    public static void showMonthlyPaymentNotification(Context context, int paymentId, String paymentName) {
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Monthly Payment Due")
                .setContentText(paymentName + " is due today")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true) // Permanent notification
                .build();

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(paymentId, notification);
        }
    }

    // ✅ Cancel permanent notification (monthly payment)
    public static void cancelNotification(Context context, int notificationId) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(notificationId);
        }
    }

    // ✅ Cancel scheduled timed reminder (used when user deletes a timed reminder)
    public static void cancelReminder(Context context, int reminderId) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }
}
