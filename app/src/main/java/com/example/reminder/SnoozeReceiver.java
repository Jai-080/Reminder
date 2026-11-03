package com.example.reminder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;

import com.example.reminder.sync.SyncManager;

import java.util.ArrayList;

public class SnoozeReceiver extends BroadcastReceiver {

    public static final String ACTION_SHOW_OPTIONS = "com.example.reminder.ACTION_SHOW_SNOOZE_OPTIONS";
    public static final String ACTION_RESCHEDULE = "com.example.reminder.ACTION_RESCHEDULE";
    public static final String ACTION_DISMISS = "com.example.reminder.ACTION_DISMISS";

    public static final String EXTRA_REMINDER_ID = "reminder_id";
    public static final String EXTRA_REMINDER_TEXT = "reminder_text";
    public static final String EXTRA_MINUTES = "minutes";
    public static final String KEY_TEXT_REPLY = "key_custom_snooze_minutes";

    private static final String TAG = "SnoozeReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        int reminderId = intent.getIntExtra(EXTRA_REMINDER_ID, -1);
        String reminderText = intent.getStringExtra(EXTRA_REMINDER_TEXT);

        Log.d(TAG, "onReceive action=" + action + ", reminderId=" + reminderId + ", text=" + reminderText);

        if (reminderId == -1) {
            return;
        }

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (ACTION_SHOW_OPTIONS.equals(action)) {
            showNotificationOptions(context, notificationManager, reminderId, reminderText);
        } else if (ACTION_RESCHEDULE.equals(action)) {
            int minutes = intent.getIntExtra(EXTRA_MINUTES, -1);

            // Check if input was submitted via RemoteInput (Custom reply in notification shade)
            Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
            if (remoteInput != null) {
                CharSequence input = remoteInput.getCharSequence(KEY_TEXT_REPLY);
                if (input != null) {
                    try {
                        String clean = input.toString().trim().replaceAll("[^0-9]", "");
                        if (!clean.isEmpty()) {
                            minutes = Integer.parseInt(clean);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse custom minutes: " + input, e);
                    }
                }
            }

            if (minutes <= 0) {
                minutes = 10;
            }

            performReschedule(context, notificationManager, reminderId, reminderText, minutes);
        } else if (ACTION_DISMISS.equals(action)) {
            if (notificationManager != null) {
                notificationManager.cancel(reminderId);
            }
        }
    }

    private void showNotificationOptions(Context context, NotificationManager notificationManager,
                                         int reminderId, String reminderText) {
        if (notificationManager == null) return;

        String channelId = "reminder_channel";
        NotificationChannel channel = new NotificationChannel(
                channelId,
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH
        );
        notificationManager.createNotificationChannel(channel);

        // +10 min
        Intent intent10 = new Intent(context, SnoozeReceiver.class);
        intent10.setAction(ACTION_RESCHEDULE);
        intent10.putExtra(EXTRA_REMINDER_ID, reminderId);
        intent10.putExtra(EXTRA_REMINDER_TEXT, reminderText);
        intent10.putExtra(EXTRA_MINUTES, 10);
        PendingIntent pi10 = PendingIntent.getBroadcast(
                context,
                reminderId * 100 + 1,
                intent10,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // +30 min
        Intent intent30 = new Intent(context, SnoozeReceiver.class);
        intent30.setAction(ACTION_RESCHEDULE);
        intent30.putExtra(EXTRA_REMINDER_ID, reminderId);
        intent30.putExtra(EXTRA_REMINDER_TEXT, reminderText);
        intent30.putExtra(EXTRA_MINUTES, 30);
        PendingIntent pi30 = PendingIntent.getBroadcast(
                context,
                reminderId * 100 + 2,
                intent30,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // +1 hour
        Intent intent60 = new Intent(context, SnoozeReceiver.class);
        intent60.setAction(ACTION_RESCHEDULE);
        intent60.putExtra(EXTRA_REMINDER_ID, reminderId);
        intent60.putExtra(EXTRA_REMINDER_TEXT, reminderText);
        intent60.putExtra(EXTRA_MINUTES, 60);
        PendingIntent pi60 = PendingIntent.getBroadcast(
                context,
                reminderId * 100 + 3,
                intent60,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Custom minutes RemoteInput action
        RemoteInput remoteInput = new RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel("Enter minutes (e.g. 15)")
                .build();

        Intent customIntent = new Intent(context, SnoozeReceiver.class);
        customIntent.setAction(ACTION_RESCHEDULE);
        customIntent.putExtra(EXTRA_REMINDER_ID, reminderId);
        customIntent.putExtra(EXTRA_REMINDER_TEXT, reminderText);

        int mutableFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mutableFlags |= PendingIntent.FLAG_MUTABLE;
        } else {
            mutableFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent piCustom = PendingIntent.getBroadcast(
                context,
                reminderId * 100 + 4,
                customIntent,
                mutableFlags
        );

        NotificationCompat.Action customAction = new NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_edit,
                "Custom",
                piCustom)
                .addRemoteInput(remoteInput)
                .build();

        // Build updated notification with all reschedule options
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Reschedule: " + (reminderText != null ? reminderText : "Reminder"))
                .setContentText("Choose a snooze duration:")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(false)
                .setOngoing(true)
                .addAction(0, "+10 min", pi10)
                .addAction(0, "+30 min", pi30)
                .addAction(0, "+1 hour", pi60)
                .addAction(customAction);

        notificationManager.notify(reminderId, builder.build());
    }

    private void performReschedule(Context context, NotificationManager notificationManager,
                                   int reminderId, String reminderText, int minutes) {
        if (notificationManager != null) {
            notificationManager.cancel(reminderId);
        }

        long targetTimeMillis = System.currentTimeMillis() + (minutes * 60 * 1000L);

        // 1. Schedule exact alarm
        AlarmUtils.scheduleReminder(context, reminderId, reminderText, targetTimeMillis);

        // 2. Update local DB status to active/snoozed
        ReminderDatabaseHelper dbHelper = new ReminderDatabaseHelper(context);
        dbHelper.snoozeReminder(reminderId, targetTimeMillis);

        // 3. Sync to server
        ArrayList<Reminder> allReminders = dbHelper.getAllReminders();
        Reminder rescheduled = null;
        for (Reminder r : allReminders) {
            if (r.getId() == reminderId) {
                rescheduled = r;
                break;
            }
        }
        if (rescheduled != null) {
            SyncManager.getInstance(context.getApplicationContext()).uploadReminder(
                    rescheduled.getId(),
                    rescheduled.getText(),
                    rescheduled.getTime(),
                    false,
                    targetTimeMillis,
                    rescheduled.getServerId(),
                    new SyncManager.SyncCallback<Long>() {
                        @Override
                        public void onSuccess(Long result) {
                            Log.d(TAG, "Reschedule synced to server for ID: " + reminderId);
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Reschedule sync failed for ID: " + reminderId + ": " + error);
                        }
                    }
            );
        }

        // 4. Send UI update broadcast so any open screen refreshes
        Intent updateIntent = new Intent("com.example.reminder.REMINDER_EXPIRED");
        updateIntent.putExtra("reminder_id", reminderId);
        context.sendBroadcast(updateIntent);

        // 5. Show toast confirmation without opening app
        String durationStr = minutes >= 60 ? (minutes / 60) + " hr" : minutes + " min";
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(context, "Rescheduled for " + durationStr, Toast.LENGTH_SHORT).show();
        });
    }
}
