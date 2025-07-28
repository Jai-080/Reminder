package com.example.remainder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class AlarmUtils {

    private static final String TAG = "AlarmUtils";

    public static void scheduleReminder(Context context, int id, String text, long timeMillis) {
        Log.d(TAG, "Scheduling reminder: id=" + id + ", text=" + text + ", time=" + timeMillis);

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("reminder_id", id);
        intent.putExtra("reminder_text", text);
        intent.putExtra("is_snooze", false);
        intent.putExtra("is_payment", false); // ✅ It's a timed reminder, not a payment

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        scheduleAlarm(context, timeMillis, pendingIntent);
    }


    public static void scheduleSnoozedReminder(Context context, int originalId, String text, long delayMillis) {
        long snoozeTime = System.currentTimeMillis() + delayMillis;
        int snoozeRequestCode = (int) (originalId + snoozeTime); // Ensure unique request code

        Log.d(TAG, "Scheduling snoozed reminder: originalId=" + originalId + ", delay=" + delayMillis);

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("reminder_id", originalId);
        intent.putExtra("reminder_text", text);
        intent.putExtra("is_snooze", true); // Mark that this is a snoozed instance
        intent.putExtra("is_payment", false); // 🟢 Important: Mark as NOT a payment reminder

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                snoozeRequestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        scheduleAlarm(context, snoozeTime, pendingIntent);
    }


    private static void scheduleAlarm(Context context, long timeMillis, PendingIntent pendingIntent) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Exact alarms not permitted");
                return;
            }
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent);
            Log.d(TAG, "Alarm scheduled for: " + timeMillis);
        } else {
            Log.e(TAG, "AlarmManager is null");
        }
    }

    public static void schedulePaymentReminder(Context context, String paymentName, long timeMillis) {
        Log.d(TAG, "Scheduling payment reminder: name=" + paymentName + ", time=" + timeMillis);

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("reminder_id", (int) timeMillis);
        intent.putExtra("reminder_text", "Payment due: " + paymentName);
        intent.putExtra("is_snooze", false);
        intent.putExtra("is_payment", true); // Mark as payment reminder

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) timeMillis,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        scheduleAlarm(context, timeMillis, pendingIntent);
    }

    public static void cancelReminder(Context context, int id) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
            Log.d(TAG, "Canceled reminder with id: " + id);
        }
    }
}
