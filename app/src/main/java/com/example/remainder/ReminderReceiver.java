package com.example.remainder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "ReminderReceiver";
    private static final String CHANNEL_ID = "reminder_channel";
    private static final String SNOOZE_ACTION = "SNOOZE_ACTION";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Received broadcast intent: " + intent);

        if (intent == null) {
            Log.e(TAG, "Intent is null");
            return;
        }

        String action = intent.getAction();
        int reminderId = intent.getIntExtra("reminder_id", -1);
        String reminderText = intent.getStringExtra("reminder_text");
        boolean isSnooze = intent.getBooleanExtra("is_snooze", false);
        boolean isPayment = intent.getBooleanExtra("is_payment", false);

        if (reminderId == -1 || reminderText == null) {
            Log.e(TAG, "Missing reminder ID or text in intent");
            return;
        }

        // Handle snooze action: launch SnoozeOptionsActivity and cancel the original notification
        if (isSnooze || SNOOZE_ACTION.equals(action)) {
            Log.d(TAG, "Launching SnoozeOptionsActivity for ID " + reminderId);

            // Cancel the original notification
            NotificationManagerCompat.from(context).cancel(reminderId);

            // Launch the snooze options screen
            Intent snoozeOptionsIntent = new Intent(context, SnoozeOptionsActivity.class);
            snoozeOptionsIntent.putExtra("reminder_id", reminderId);
            snoozeOptionsIntent.putExtra("reminder_text", reminderText);
            snoozeOptionsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // Required when launching from BroadcastReceiver
            context.startActivity(snoozeOptionsIntent);
            return;
        }

        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Notification permission not granted. Skipping notification.");
            return;
        }

        createNotificationChannel(context);

        // Intent to open the correct activity when tapping the notification
        Intent mainIntent = new Intent(context,
                isPayment ? MonthlyPaymentsActivity.class : TimedRemindersActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        mainIntent.putExtra("reminder_id", reminderId);
        mainIntent.putExtra("reminder_text", reminderText);

        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                context,
                reminderId + 20000,
                mainIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Intent for snooze action button
        Intent snoozeIntent = new Intent(context, ReminderReceiver.class);
        snoozeIntent.setAction(SNOOZE_ACTION);
        snoozeIntent.putExtra("reminder_id", reminderId);
        snoozeIntent.putExtra("reminder_text", reminderText);
        snoozeIntent.putExtra("is_snooze", true);

        PendingIntent snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId + 1000,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Reminder")
                .setContentText(reminderText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(contentPendingIntent)
                .setAutoCancel(true)
                .addAction(android.R.drawable.ic_menu_recent_history, "Snooze", snoozePendingIntent);

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        manager.notify(reminderId, builder.build());

        Log.d(TAG, "Notification shown for reminder: " + reminderId);

        // Optional broadcast for UI updates
        if (!isPayment) {
            Intent uiIntent = new Intent("com.example.remainder.REMINDER_EXPIRED");
            uiIntent.putExtra("reminder_id", reminderId);
            context.sendBroadcast(uiIntent);
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Reminders";
            String description = "Channel for reminder notifications";
            int importance = NotificationManager.IMPORTANCE_HIGH;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();

            channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes);

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}
