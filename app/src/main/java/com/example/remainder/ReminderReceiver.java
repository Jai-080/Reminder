package com.example.remainder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "ReminderReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Received broadcast intent: " + intent);

        if (intent == null) {
            Log.e(TAG, "Intent is null");
            return;
        }

        int reminderId = intent.getIntExtra("reminder_id", -1);
        String reminderText = intent.getStringExtra("reminder_text");

        if (reminderId == -1 || reminderText == null) {
            Log.e(TAG, "Missing reminder ID or text in intent");
            return;
        }

        // ✅ Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Notification permission not granted. Skipping notification.");
                return;
            }
        }

        // ✅ Show the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "reminder_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Reminder")
                .setContentText(reminderText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        manager.notify(reminderId, builder.build());

        Log.d(TAG, "Notification shown for reminder: " + reminderId);

        // ✅ Update database if needed (optional)
        ReminderDatabaseHelper dbHelper = new ReminderDatabaseHelper(context);
        // Optional: You can call any DB update logic here if required

        // ✅ Broadcast UI update to MainActivity
        Intent uiIntent = new Intent("com.example.remainder.REMINDER_EXPIRED");
        uiIntent.putExtra("reminder_id", reminderId);
        context.sendBroadcast(uiIntent);
    }
}
