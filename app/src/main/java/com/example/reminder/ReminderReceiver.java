package com.example.reminder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        long receivedTimestamp = System.currentTimeMillis();
        int reminderId = intent.getIntExtra("reminder_id", -1);
        long serverId = intent.getLongExtra("server_id", -1L);
        String title = intent.getStringExtra("reminder_text");
        String time = intent.getStringExtra("reminder_time"); // optional display
        boolean isPayment = intent.getBooleanExtra("is_payment", false);

        android.util.Log.d("REMINDER_FIRE", "Receiver entered at " + receivedTimestamp + 
                " for local ID: " + reminderId + ", server ID: " + serverId + 
                ", title: " + title + ", isPayment: " + isPayment);

        try {
            if (reminderId == -1 || title == null) {
                android.util.Log.w("ReminderReceiver", "Ignoring invalid intent: reminderId=" + reminderId + ", title=" + title);
                return;
            }

            long scheduledTime = intent.getLongExtra("scheduled_time", -1L);
            android.util.Log.d("ReminderReceiver", "Alarm fired: reminder id=" + reminderId + ", actual fire time=" + receivedTimestamp);
            if (scheduledTime != -1L) {
                long delay = receivedTimestamp - scheduledTime;
                android.util.Log.d("ReminderReceiver", "Delay: " + delay + " ms");
            }

            if (isPayment) {
                // ✅ Monthly payment notification (permanent)
                AlarmUtils.showMonthlyPaymentNotification(context, reminderId, title);
            } else {
                // 1️⃣ Update reminder status to expired
                ReminderDatabaseHelper dbHelper = new ReminderDatabaseHelper(context);
                dbHelper.updateReminderStatus(reminderId, "expired");
                android.util.Log.d("ReminderReceiver", "Reminder marked expired in database for id=" + reminderId);

                // 2️⃣ Broadcast to update UI
                Intent updateIntent = new Intent("com.example.reminder.REMINDER_EXPIRED");
                updateIntent.putExtra("reminder_id", reminderId);
                context.sendBroadcast(updateIntent);

                // 3️⃣ Intent to open snooze activity
                Intent snoozeIntent = new Intent(context, SnoozeOptionsActivity.class);
                snoozeIntent.putExtra("reminder_id", reminderId);
                snoozeIntent.putExtra("reminder_text", title);
                snoozeIntent.putExtra("reminder_time", time);

                PendingIntent snoozePendingIntent = PendingIntent.getActivity(
                        context,
                        reminderId,
                        snoozeIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                // 4️⃣ Intent to open app on tap
                Intent activityIntent = new Intent(context, TimedRemindersActivity.class);
                PendingIntent activityPendingIntent = PendingIntent.getActivity(
                        context,
                        reminderId + 10000,
                        activityIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                // 5️⃣ Notification channel
                String channelId = "reminder_channel";
                NotificationManager notificationManager =
                        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                NotificationChannel channel = new NotificationChannel(
                        channelId,
                        "Reminders",
                        NotificationManager.IMPORTANCE_HIGH
                );
                notificationManager.createNotificationChannel(channel);

                // 6️⃣ Build notification
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(android.R.drawable.ic_popup_reminder)
                        .setContentTitle("Reminder: " + title)
                        .setContentText(time != null ? "Scheduled for " + time : "")
                        .setContentIntent(activityPendingIntent)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .addAction(android.R.drawable.ic_menu_recent_history, "Snooze", snoozePendingIntent);

                android.util.Log.d("ReminderReceiver", "Notification built for reminder id=" + reminderId);

                // 7️⃣ Show notification
                notificationManager.notify(reminderId, builder.build());
                android.util.Log.d("ReminderReceiver", "Notification posted for reminder id=" + reminderId);
            }
        } catch (Exception e) {
            android.util.Log.e("ReminderReceiver", "Receiver failure", e);
        }
    }
}
