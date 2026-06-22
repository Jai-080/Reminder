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
        long serverId = -1;
        try {
            ReminderDatabaseHelper dbHelper = new ReminderDatabaseHelper(context);
            android.database.sqlite.SQLiteDatabase db = dbHelper.getReadableDatabase();
            android.database.Cursor cursor = db.query("reminders", new String[]{"server_id"}, "id=?", new String[]{String.valueOf(reminderId)}, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int colIdx = cursor.getColumnIndex("server_id");
                    if (colIdx != -1 && !cursor.isNull(colIdx)) {
                        serverId = cursor.getLong(colIdx);
                    }
                }
                cursor.close();
            }
            db.close();
        } catch (Exception e) {
            android.util.Log.e("AlarmUtils", "Failed to query serverId for logging", e);
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        boolean canExact = alarmManager != null && alarmManager.canScheduleExactAlarms();
        android.util.Log.d("AlarmUtils", "scheduleReminder: localId=" + reminderId + ", serverId=" + serverId + ", triggerTime=" + triggerAtMillis + ", currentTime=" + System.currentTimeMillis() + ", request code=" + reminderId + ", PendingIntent flags=" + (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE) + ", canScheduleExactAlarms=" + canExact);

        scheduleAlarm(context, reminderId, reminderText, triggerAtMillis, false);
    }

    // Centralized method to schedule monthly payment alarm using Paymentalarmreceiver
    public static void schedulePaymentAlarm(Context context, int paymentId, String paymentName, long dueDateMillis) {
        String offsetsStr = "0";
        try {
            PaymentDatabaseHelper dbHelper = new PaymentDatabaseHelper(context);
            android.database.sqlite.SQLiteDatabase db = dbHelper.getReadableDatabase();
            android.database.Cursor cursor = db.query("monthly_payments", new String[]{"notification_offsets", "completed"}, "id=?", new String[]{String.valueOf(paymentId)}, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int completedIdx = cursor.getColumnIndex("completed");
                    if (completedIdx != -1 && cursor.getInt(completedIdx) == 1) {
                        cursor.close();
                        db.close();
                        return; // do not schedule completed payments
                    }
                    int colIdx = cursor.getColumnIndex("notification_offsets");
                    if (colIdx != -1 && !cursor.isNull(colIdx)) {
                        offsetsStr = cursor.getString(colIdx);
                    }
                }
                cursor.close();
            }
            db.close();
        } catch (Exception e) {
            android.util.Log.e("AlarmUtils", "Failed to query notification_offsets", e);
        }

        String[] parts = offsetsStr.split(",");
        long now = System.currentTimeMillis();
        long nextAlertTime = -1;
        int nextOffsetDays = -1;

        for (String part : parts) {
            try {
                int days = Integer.parseInt(part.trim());
                long alertTime = dueDateMillis - ((long) days * 24 * 60 * 60 * 1000);
                if (alertTime > now) {
                    if (nextAlertTime == -1 || alertTime < nextAlertTime) {
                        nextAlertTime = alertTime;
                        nextOffsetDays = days;
                    }
                }
            } catch (NumberFormatException ignored) {}
        }

        // If no future alert offset exists, we don't schedule anything.
        if (nextAlertTime == -1) {
            android.util.Log.d("AlarmUtils", "No future alerts to schedule for payment id=" + paymentId);
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, Paymentalarmreceiver.class);
        intent.putExtra("payment_id", paymentId);
        intent.putExtra("payment_name", paymentName);
        intent.putExtra("scheduled_time", dueDateMillis);
        intent.putExtra("offset_days", nextOffsetDays);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                paymentId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        android.util.Log.d("AlarmUtils", "Alarm scheduled: payment id=" + paymentId + ", offset=" + nextOffsetDays + "d, scheduled trigger time=" + nextAlertTime);
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlertTime, pendingIntent);
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

    // Generic method to schedule any alarm
    private static void scheduleAlarm(Context context, int id, String text, long triggerAtMillis, boolean isPayment) {
        long serverId = -1;
        try {
            ReminderDatabaseHelper dbHelper = new ReminderDatabaseHelper(context);
            android.database.sqlite.SQLiteDatabase db = dbHelper.getReadableDatabase();
            android.database.Cursor cursor = db.query("reminders", new String[]{"server_id"}, "id=?", new String[]{String.valueOf(id)}, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int colIdx = cursor.getColumnIndex("server_id");
                    if (colIdx != -1 && !cursor.isNull(colIdx)) {
                        serverId = cursor.getLong(colIdx);
                    }
                }
                cursor.close();
            }
            db.close();
        } catch (Exception e) {
            // Ignore or log
        }

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("reminder_id", id);
        intent.putExtra("server_id", serverId);
        intent.putExtra("reminder_text", text);
        intent.putExtra("is_payment", isPayment);
        intent.putExtra("scheduled_time", triggerAtMillis);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            android.util.Log.d("AlarmUtils", "scheduleAlarm: request code=" + id + ", triggerTime=" + triggerAtMillis + ", PendingIntent flags=" + (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            android.util.Log.d("AlarmUtils", "Alarm scheduled: reminder id=" + id + ", scheduled trigger time=" + triggerAtMillis);
            
            // Standardize to unconditionally call setExactAndAllowWhileIdle to match the payment flow
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
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
        showMonthlyPaymentNotification(context, paymentId, paymentName, 0);
    }

    public static void showMonthlyPaymentNotification(Context context, int paymentId, String paymentName, int offsetDays) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel(context);

        String title = offsetDays == 0 ? "Payment Due Today" : "Payment Due Soon";
        String contentText = offsetDays == 0 ? 
                paymentName + " is due today" : 
                paymentName + " is due in " + offsetDays + " days";

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(contentText)
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
        android.util.Log.d("REMINDER_CANCEL", "Cancelling reminder " + reminderId + " at " + System.currentTimeMillis());

        Intent intent = new Intent(context, ReminderReceiver.class);

        // Use FLAG_NO_CREATE so we do NOT create or modify an existing PendingIntent.
        // FLAG_UPDATE_CURRENT was previously corrupting the PendingIntent by stripping its extras.
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null && pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
        } else {
            android.util.Log.d("REMINDER_CANCEL", "No pending alarm found for reminder " + reminderId + ", skip cancel.");
        }
    }
}
