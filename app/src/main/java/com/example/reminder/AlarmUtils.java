package com.example.reminder;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import java.util.Calendar;

public class AlarmUtils {
    private static final String CHANNEL_ID = "reminder_channel";
    private static final String CHANNEL_NAME = "Reminders";
    private static final String CHANNEL_DESC = "Shows notifications for reminders and payments";

    // ✅ Ensure channel is created
    private static void createNotificationChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(CHANNEL_DESC);

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    // ✅ Schedule a timed reminder
    public static void scheduleReminder(Context context, int reminderId, String reminderText, long triggerAtMillis) {
        scheduleAlarm(context, reminderId, reminderText, triggerAtMillis, false);
    }

    // ✅ Centralized method to schedule monthly payment alarm using Paymentalarmreceiver
    public static void schedulePaymentAlarm(Context context, int paymentId, String paymentName, long dueDateMillis) {
        if (dueDateMillis <= System.currentTimeMillis()) {
            return;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, Paymentalarmreceiver.class);
        intent.putExtra("payment_id", paymentId);
        intent.putExtra("payment_name", paymentName);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                paymentId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueDateMillis, pendingIntent);
    }

    // ✅ Centralized method to cancel monthly payment alarm using Paymentalarmreceiver
    public static void cancelPaymentAlarm(Context context, int paymentId, String paymentName) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, Paymentalarmreceiver.class);
        intent.putExtra("payment_id", paymentId);
        intent.putExtra("payment_name", paymentName);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                paymentId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.cancel(pendingIntent);
    }

    // ✅ Schedule a monthly payment reminder (trigger at 9 AM on the due day)
    public static void schedulePaymentReminder(Context context, int paymentId, String paymentName, int dayOfMonth) {
        long triggerAtMillis = getPaymentTriggerMillis(dayOfMonth);
        scheduleAlarm(context, paymentId, paymentName, triggerAtMillis, true);
    }

    // ✅ Generic method to schedule any alarm
    private static void scheduleAlarm(Context context, int id, String text, long triggerAtMillis, boolean isPayment) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("reminder_id", id);
        intent.putExtra("reminder_text", text);
        intent.putExtra("is_payment", isPayment);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
        }
    }

    // ✅ Calculate trigger time for the monthly payment
    private static long getPaymentTriggerMillis(int dayOfMonth) {
        Calendar now = Calendar.getInstance();
        Calendar cal = (Calendar) now.clone();

        cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        cal.set(Calendar.HOUR_OF_DAY, 9); // 9 AM
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // If day has already passed this month, schedule for next month
        if (cal.getTimeInMillis() <= now.getTimeInMillis()) {
            cal.add(Calendar.MONTH, 1);
        }

        return cal.getTimeInMillis();
    }

    // ✅ Show permanent notification for monthly payments
    public static void showMonthlyPaymentNotification(Context context, int paymentId, String paymentName) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            try {
                for (android.service.notification.StatusBarNotification sbn : manager.getActiveNotifications()) {
                    if (sbn.getId() == paymentId) {
                        return; // Already active, skip displaying again
                    }
                }
            } catch (Exception e) {
                // Fallback in case of unexpected errors reading notifications
            }
        }

        createNotificationChannel(context);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Monthly Payment Due")
                .setContentText(paymentName + " is due today")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true) // Permanent notification
                .build();

        if (manager != null) {
            manager.notify(paymentId, notification);
        }
    }

    // ✅ Cancel permanent notification (monthly payment or any notification)
    public static void cancelNotification(Context context, int notificationId) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(notificationId);
        }
    }

    // ✅ Cancel scheduled reminder/payment
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
