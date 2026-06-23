package com.example.reminder;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.reminder.sync.SyncManager;
import java.util.ArrayList;
import java.util.Calendar;

public class SnoozeOptionsActivity extends AppCompatActivity {

    private int reminderId;
    private String reminderText;

    private static final String TAG = "SnoozeOptionsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_snooze_options);

        Intent intent = getIntent();
        reminderId = intent.getIntExtra("reminder_id", -1);
        reminderText = intent.getStringExtra("reminder_text");

        if (reminderId == -1 || reminderText == null) {
            Log.e(TAG, "Missing reminder data");
            Toast.makeText(this, "Error: Missing reminder data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        TextView tvReminderText = findViewById(R.id.tv_reminder_text);
        tvReminderText.setText(reminderText);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        View btn10Min = findViewById(R.id.btn_snooze_10_min);
        View btn20Min = findViewById(R.id.btn_snooze_20_min);
        View btn30Min = findViewById(R.id.btn_snooze_30_min);
        View btn1Hour = findViewById(R.id.btn_snooze_1_hour);
        View btnCustom = findViewById(R.id.btn_reschedule_custom);

        btn10Min.setOnClickListener(v -> rescheduleReminder(10));
        btn20Min.setOnClickListener(v -> rescheduleReminder(20));
        btn30Min.setOnClickListener(v -> rescheduleReminder(30));
        btn1Hour.setOnClickListener(v -> rescheduleReminder(60));
        btnCustom.setOnClickListener(v -> showCustomRescheduleDialog());
    }

    private void rescheduleReminder(int minutes) {
        long targetTimeMillis = Calendar.getInstance().getTimeInMillis() + minutes * 60 * 1000L;
        performReschedule(targetTimeMillis, "Rescheduled for " + minutes + " min");
    }

    private void showCustomRescheduleDialog() {
        Calendar now = Calendar.getInstance();
        final Calendar selectedDateTime = Calendar.getInstance();

        new android.app.DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedDateTime.set(Calendar.YEAR, year);
            selectedDateTime.set(Calendar.MONTH, month);
            selectedDateTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            int hour = now.get(Calendar.HOUR_OF_DAY);
            int minute = now.get(Calendar.MINUTE);

            new CustomTimePickerDialog(this, R.style.TimePickerTheme, (timeView, h, m) -> {
                selectedDateTime.set(Calendar.HOUR_OF_DAY, h);
                selectedDateTime.set(Calendar.MINUTE, m);
                selectedDateTime.set(Calendar.SECOND, 0);
                selectedDateTime.set(Calendar.MILLISECOND, 0);

                long triggerTime = selectedDateTime.getTimeInMillis();
                if (triggerTime <= System.currentTimeMillis()) {
                    Toast.makeText(this, "Please choose a future time", Toast.LENGTH_SHORT).show();
                    return;
                }

                performReschedule(triggerTime, "Rescheduled successfully");
            }, hour, minute, true).show();

        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void performReschedule(long targetTimeMillis, String successMessage) {
        // Cancel old notification instantly
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(reminderId);
        }

        // Create intent for the reminder
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("reminder_id", reminderId);
        intent.putExtra("reminder_text", reminderText);
        intent.putExtra("scheduled_time", targetTimeMillis);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                reminderId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Schedule alarm
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    targetTimeMillis,
                    pendingIntent
            );
            Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
        } else {
            Log.e(TAG, "AlarmManager is null");
            Toast.makeText(this, "Failed to reschedule reminder", Toast.LENGTH_SHORT).show();
        }

        // Update local DB status and sync (reusing the existing snoozeReminder workflow)
        ReminderDatabaseHelper dbHelper = new ReminderDatabaseHelper(this);
        dbHelper.snoozeReminder(reminderId, targetTimeMillis);

        ArrayList<Reminder> allReminders = dbHelper.getAllReminders();
        Reminder rescheduled = null;
        for (Reminder r : allReminders) {
            if (r.getId() == reminderId) {
                rescheduled = r;
                break;
            }
        }
        if (rescheduled != null) {
            SyncManager.getInstance(getApplicationContext()).uploadReminder(
                    rescheduled.getId(),
                    rescheduled.getText(),
                    targetTimeMillis,
                    false,
                    targetTimeMillis,
                    rescheduled.getServerId(),
                    new SyncManager.SyncCallback<Long>() {
                        @Override
                        public void onSuccess(Long result) {
                            Log.d(TAG, "Reschedule sync succeeded");
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Reschedule sync failed: " + error);
                        }
                    }
            );
        }

        finish();
    }
}
