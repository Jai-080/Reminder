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
        intent.putExtra("reminder_id", id); // ✅ Use correct key
        intent.putExtra("reminder_text", text); // ✅ Use correct key

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Exact alarms not permitted");
                return;
            }
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent);
            Log.d(TAG, "Reminder scheduled with AlarmManager");
        } else {
            Log.e(TAG, "AlarmManager is null");
        }
    }

    public static void schedulePaymentReminder(Context context, String paymentName, long timeMillis) {
        Log.d(TAG, "Scheduling payment reminder: name=" + paymentName + ", time=" + timeMillis);

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("reminder_id", (int) timeMillis); // Use timestamp as unique ID or create your own
        intent.putExtra("reminder_text", "Payment due: " + paymentName);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) timeMillis,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Exact alarms not permitted");
                return;
            }
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent);
            Log.d(TAG, "Payment reminder scheduled");
        } else {
            Log.e(TAG, "AlarmManager is null");
        }
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
