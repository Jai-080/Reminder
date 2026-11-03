package com.example.reminder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.example.reminder.sync.SyncManager;

import java.util.ArrayList;

public class SnoozeReceiver extends BroadcastReceiver {

    public static final String ACTION_SHOW_OPTIONS = "com.example.reminder.ACTION_SHOW_SNOOZE_OPTIONS";
    public static final String ACTION_RESCHEDULE = "com.example.reminder.ACTION_RESCHEDULE";
    public static final String ACTION_DISMISS = "com.example.reminder.ACTION_DISMISS";

    public static final String EXTRA_REMINDER_ID = "reminder_id";
    public static final String EXTRA_REMINDER_TEXT = "reminder_text";
    public static final String EXTRA_MINUTES = "minutes";

    public static final String CHANNEL_SILENT_ID = "reminder_silent_channel";

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
            int minutes = intent.getIntExtra(EXTRA_MINUTES, 10);
            performReschedule(context, notificationManager, reminderId, reminderText, minutes);
        } else if (ACTION_DISMISS.equals(action)) {
            if (notificationManager != null) {
                notificationManager.cancel(reminderId);
            }
        }
    }

    public static RemoteViews buildExpandedRemoteViews(Context context, int reminderId, String reminderText) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.notification_snooze_expanded);
        views.setTextViewText(R.id.notif_reminder_title, reminderText != null ? reminderText : "Reminder");

        // 10 Min
        Intent i10 = new Intent(context, SnoozeReceiver.class);
        i10.setAction(ACTION_RESCHEDULE);
        i10.putExtra(EXTRA_REMINDER_ID, reminderId);
        i10.putExtra(EXTRA_REMINDER_TEXT, reminderText);
        i10.putExtra(EXTRA_MINUTES, 10);
        PendingIntent pi10 = PendingIntent.getBroadcast(
                context,
                reminderId * 100 + 1,
                i10,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.btn_notif_10_min, pi10);

        // 20 Min
        Intent i20 = new Intent(context, SnoozeReceiver.class);
        i20.setAction(ACTION_RESCHEDULE);
        i20.putExtra(EXTRA_REMINDER_ID, reminderId);
        i20.putExtra(EXTRA_REMINDER_TEXT, reminderText);
        i20.putExtra(EXTRA_MINUTES, 20);
        PendingIntent pi20 = PendingIntent.getBroadcast(
                context,
                reminderId * 100 + 2,
                i20,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.btn_notif_20_min, pi20);

        // 30 Min
        Intent i30 = new Intent(context, SnoozeReceiver.class);
        i30.setAction(ACTION_RESCHEDULE);
        i30.putExtra(EXTRA_REMINDER_ID, reminderId);
        i30.putExtra(EXTRA_REMINDER_TEXT, reminderText);
        i30.putExtra(EXTRA_MINUTES, 30);
        PendingIntent pi30 = PendingIntent.getBroadcast(
                context,
                reminderId * 100 + 3,
                i30,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.btn_notif_30_min, pi30);

        // 1 Hour
        Intent i60 = new Intent(context, SnoozeReceiver.class);
        i60.setAction(ACTION_RESCHEDULE);
        i60.putExtra(EXTRA_REMINDER_ID, reminderId);
        i60.putExtra(EXTRA_REMINDER_TEXT, reminderText);
        i60.putExtra(EXTRA_MINUTES, 60);
        PendingIntent pi60 = PendingIntent.getBroadcast(
                context,
                reminderId * 100 + 4,
                i60,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.btn_notif_1_hour, pi60);

        // Custom Date & Time (opens dialog directly)
        Intent iCustom = new Intent(context, SnoozeOptionsActivity.class);
        iCustom.putExtra("reminder_id", reminderId);
        iCustom.putExtra("reminder_text", reminderText);
        iCustom.putExtra("auto_open_picker", true);
        iCustom.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piCustom = PendingIntent.getActivity(
                context,
                reminderId * 100 + 5,
                iCustom,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.btn_notif_custom, piCustom);

        // Dismiss
        Intent iDismiss = new Intent(context, SnoozeReceiver.class);
        iDismiss.setAction(ACTION_DISMISS);
        iDismiss.putExtra(EXTRA_REMINDER_ID, reminderId);
        PendingIntent piDismiss = PendingIntent.getBroadcast(
                context,
                reminderId * 100 + 6,
                iDismiss,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.btn_notif_dismiss, piDismiss);

        return views;
    }

    private void showNotificationOptions(Context context, NotificationManager notificationManager,
                                         int reminderId, String reminderText) {
        if (notificationManager == null) return;

        // Ensure silent low-importance channel so expanding/updating notification NEVER makes sound or vibrates
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel silentChannel = new NotificationChannel(
                    CHANNEL_SILENT_ID,
                    "Reminder Reschedule Updates",
                    NotificationManager.IMPORTANCE_LOW
            );
            silentChannel.setSound(null, null);
            silentChannel.enableVibration(false);
            silentChannel.setShowBadge(false);
            notificationManager.createNotificationChannel(silentChannel);
        }

        RemoteViews expandedView = buildExpandedRemoteViews(context, reminderId, reminderText);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_SILENT_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomBigContentView(expandedView)
                .setCustomContentView(expandedView)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setDefaults(0)
                .setVibrate(null)
                .setSound(null)
                .setAutoCancel(false)
                .setOngoing(true);

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
                            Log.d(TAG, "Reschedule sync succeeded for ID: " + reminderId);
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
