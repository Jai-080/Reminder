package com.example.reminder;

import com.example.reminder.sync.SyncManager;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TimedRemindersActivity extends AppCompatActivity {

    private EditText editTextReminder;
    private ReminderAdapter pendingAdapter, expiredAdapter;
    private ReminderDatabaseHelper dbHelper;
    private final Calendar selectedDateTime = Calendar.getInstance();

    private TextView pendingLabel, expiredLabel;
    private TextView txtNoPendingReminders, txtNoExpiredReminders;

    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;
    private static final String CHANNEL_ID = "reminder_channel";
    private static final String TAG = "TimedRemindersActivity";

    private final ArrayList<Reminder> pendingReminders = new ArrayList<>();
    private final ArrayList<Reminder> expiredReminders = new ArrayList<>();

    private BroadcastReceiver reminderExpiredReceiver;
    private BroadcastReceiver syncCompletedReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timed_reminder);

        createNotificationChannel();
        requestExactAlarmPermission();
        requestNotificationPermission();

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        editTextReminder = findViewById(R.id.editTextReminder);
        Button btnSetReminderFull = findViewById(R.id.btnSetReminderFull);
        RecyclerView rvPending = findViewById(R.id.rv_pending_reminders);
        RecyclerView rvExpired = findViewById(R.id.rv_expired_reminders);
        pendingLabel = findViewById(R.id.pendingLabel);
        expiredLabel = findViewById(R.id.expiredLabel);
        txtNoPendingReminders = findViewById(R.id.txtNoPendingReminders);
        txtNoExpiredReminders = findViewById(R.id.txtNoExpiredReminders);

        dbHelper = new ReminderDatabaseHelper(this);

        pendingAdapter = new ReminderAdapter(pendingReminders, dbHelper, this, this::loadReminders, false);
        expiredAdapter = new ReminderAdapter(expiredReminders, dbHelper, this, this::loadReminders, true);

        rvPending.setLayoutManager(new LinearLayoutManager(this));
        rvExpired.setLayoutManager(new LinearLayoutManager(this));
        rvPending.setAdapter(pendingAdapter);
        rvExpired.setAdapter(expiredAdapter);

        loadReminders();

        Button clearAllBtn = findViewById(R.id.clearAllBtn);
        clearAllBtn.setOnClickListener(v -> {
            List<Reminder> allReminders = dbHelper.getAllReminders();
            for (Reminder r : allReminders) {
                AlarmUtils.cancelReminder(this, r.getId());
                dbHelper.softDeleteReminder(r.getId());
            }
            loadReminders();
            Toast.makeText(this, "All reminders cleared", Toast.LENGTH_SHORT).show();
            ReminderApplication.enqueueSyncWorker(this);
        });

        btnSetReminderFull.setOnClickListener(v -> {
            hideKeyboard();
            String reminderText = editTextReminder.getText().toString().trim();

            if (reminderText.isEmpty()) {
                Toast.makeText(this, "Please enter a reminder message", Toast.LENGTH_SHORT).show();
                return;
            }

            Calendar now = Calendar.getInstance();
            new DatePickerDialog(this, (DatePicker view, int year, int month, int dayOfMonth) -> {
                selectedDateTime.set(Calendar.YEAR, year);
                selectedDateTime.set(Calendar.MONTH, month);
                selectedDateTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                int hour = now.get(Calendar.HOUR_OF_DAY);
                int minute = now.get(Calendar.MINUTE);

                // ✅ Modern spinner style instead of old analog clock
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

                    dbHelper.addReminder(reminderText, triggerTime);
                    List<Reminder> updatedReminders = dbHelper.getAllReminders();
                    Reminder newReminder = updatedReminders.get(updatedReminders.size() - 1);

                    Log.d("REMINDER SCHEDULER", "Scheduling reminder:\nlocalId=" + newReminder.getId() + "\nserverId=-1\ntime=" + triggerTime + "\nsuccess=true");
                    AlarmUtils.scheduleReminder(this, newReminder.getId(), reminderText, triggerTime);

                    editTextReminder.setText("");
                    loadReminders();

                    // Sync to backend
                    SyncManager.getInstance(TimedRemindersActivity.this).uploadReminder(
                            newReminder.getId(),
                            reminderText,
                            triggerTime,
                            false,
                            0L,
                            null,
                            new SyncManager.SyncCallback<Long>() {
                                @Override
                                public void onSuccess(Long serverId) {
                                    Log.d(TAG, "Reminder synced to server, serverId: " + serverId);
                                }

                                @Override
                                public void onError(String error) {
                                    Log.e(TAG, "Failed to sync reminder: " + error);
                                    Toast.makeText(TimedRemindersActivity.this, "Sync error: " + com.example.reminder.utils.UIUtils.sanitizeError(TimedRemindersActivity.this, error), Toast.LENGTH_SHORT).show();
                                }
                            }
                    );

                    long diff = triggerTime - System.currentTimeMillis();
                    long hours = TimeUnit.MILLISECONDS.toHours(diff);
                    long minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60;
                    Toast.makeText(this, "Reminder set in " + hours + "h " + minutes + "m", Toast.LENGTH_LONG).show();

                }, hour, minute, true).show();

            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
        });

        reminderExpiredReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.example.reminder.REMINDER_EXPIRED".equals(intent.getAction())) {
                    Log.d(TAG, "Received REMINDER_EXPIRED broadcast");
                    loadReminders();
                }
            }
        };
        registerReceiver(reminderExpiredReceiver, new IntentFilter("com.example.reminder.REMINDER_EXPIRED"),
                Context.RECEIVER_NOT_EXPORTED);

        syncCompletedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "SYNC_COMPLETED received");
                System.out.println("SYNC_COMPLETED received");
                runOnUiThread(() -> {
                    loadReminders();
                    Log.d(TAG, "UI refresh executed");
                    System.out.println("UI refresh executed");
                    int datasetSize = pendingReminders.size() + expiredReminders.size();
                    Log.d(TAG, "Dataset size after refresh: " + datasetSize);
                    System.out.println("Dataset size after refresh: " + datasetSize);
                });
            }
        };
        registerReceiver(syncCompletedReceiver, new IntentFilter(SyncManager.ACTION_SYNC_COMPLETED),
                Context.RECEIVER_NOT_EXPORTED);
    }

    private void loadReminders() {
        pendingReminders.clear();
        expiredReminders.clear();

        List<Reminder> allReminders = dbHelper.getAllReminders();
        long now = System.currentTimeMillis();

        for (Reminder r : allReminders) {
            if (r.getTime() <= now) {
                if (!r.isExpired()) {
                    dbHelper.markAsExpired(r.getId());
                    r.setExpired(true);
                }
                expiredReminders.add(r);
            } else {
                if (r.isExpired()) {
                    dbHelper.markAsPending(r.getId());
                    r.setExpired(false);
                }
                pendingReminders.add(r);
            }
        }

        pendingLabel.setVisibility(pendingReminders.isEmpty() ? View.GONE : View.VISIBLE);
        expiredLabel.setVisibility(expiredReminders.isEmpty() ? View.GONE : View.VISIBLE);
        txtNoPendingReminders.setVisibility(pendingReminders.isEmpty() ? View.VISIBLE : View.GONE);
        txtNoExpiredReminders.setVisibility(expiredReminders.isEmpty() ? View.VISIBLE : View.GONE);

        pendingAdapter.notifyDataSetChanged();
        expiredAdapter.notifyDataSetChanged();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Channel for reminder notifications");

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
            Log.d(TAG, "Notification channel created: " + CHANNEL_ID);
        }
    }

    private void requestExactAlarmPermission() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            startActivity(intent);
        }
    }

    private void requestNotificationPermission() {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATION_PERMISSION
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            String message = (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    ? "Notifications enabled" : "Notifications are disabled";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadReminders();
        
        com.example.reminder.auth.TokenManager tokenManager = com.example.reminder.auth.TokenManager.getInstance(this);
        if (tokenManager.isLoggedIn()) {
            long lastSync = tokenManager.getLastSyncTimestamp();
            if (System.currentTimeMillis() - lastSync > 300000) {
                SyncManager.getInstance(this).performFullSync(new SyncManager.SyncCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        runOnUiThread(() -> loadReminders());
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Auto-sync failed in TimedRemindersActivity: " + error);
                    }
                });
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (reminderExpiredReceiver != null) {
            unregisterReceiver(reminderExpiredReceiver);
        }
        if (syncCompletedReceiver != null) {
            unregisterReceiver(syncCompletedReceiver);
        }
    }
}
