package com.example.remainder;

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
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.ContextThemeWrapper;
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
    private Button btnSetReminderFull;
    private RecyclerView rvPending, rvExpired;
    private ReminderAdapter pendingAdapter, expiredAdapter;
    private ReminderDatabaseHelper dbHelper;
    private Calendar selectedDateTime = Calendar.getInstance();

    private TextView pendingLabel, expiredLabel;

    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;
    private static final String CHANNEL_ID = "reminder_channel";
    private static final String TAG = "TimedRemindersActivity";

    private List<Reminder> pendingList = new ArrayList<>();
    private List<Reminder> expiredList = new ArrayList<>();

    private BroadcastReceiver reminderExpiredReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timed_reminder);

        createNotificationChannel();
        requestExactAlarmPermission();
        requestNotificationPermission();

        editTextReminder = findViewById(R.id.editTextReminder);
        btnSetReminderFull = findViewById(R.id.btnSetReminderFull);
        rvPending = findViewById(R.id.rv_pending_reminders);
        rvExpired = findViewById(R.id.rv_expired_reminders);
        pendingLabel = findViewById(R.id.pendingLabel);
        expiredLabel = findViewById(R.id.expiredLabel);

        dbHelper = new ReminderDatabaseHelper(this);

        pendingAdapter = new ReminderAdapter((ArrayList<Reminder>) pendingList, dbHelper, this, this::loadReminders);
        expiredAdapter = new ReminderAdapter((ArrayList<Reminder>) expiredList, dbHelper, this, this::loadReminders);

        rvPending.setLayoutManager(new LinearLayoutManager(this));
        rvExpired.setLayoutManager(new LinearLayoutManager(this));
        rvPending.setAdapter(pendingAdapter);
        rvExpired.setAdapter(expiredAdapter);

        loadReminders();

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

                ContextThemeWrapper themedContext = new ContextThemeWrapper(this, android.R.style.Theme_Holo_Dialog_NoActionBar);
                new TimePickerDialog(themedContext, (timeView, h, m) -> {
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

                    Log.d(TAG, "Scheduling reminder: ID=" + newReminder.getId() + ", Text=" + reminderText + ", Time=" + triggerTime);
                    AlarmUtils.scheduleReminder(this, newReminder.getId(), reminderText, triggerTime);

                    editTextReminder.setText("");
                    loadReminders();

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
                if ("com.example.remainder.REMINDER_EXPIRED".equals(intent.getAction())) {
                    Log.d(TAG, "Received REMINDER_EXPIRED broadcast");
                    loadReminders();
                }
            }
        };
        registerReceiver(reminderExpiredReceiver, new IntentFilter("com.example.remainder.REMINDER_EXPIRED"),
                Context.RECEIVER_EXPORTED);
    }

    private void loadReminders() {
        pendingList.clear();
        expiredList.clear();

        List<Reminder> allReminders = dbHelper.getAllReminders();
        long now = System.currentTimeMillis();

        for (Reminder r : allReminders) {
            if (r.getTime() < now) {
                expiredList.add(r);
            } else {
                pendingList.add(r);
            }
        }

        pendingLabel.setVisibility(pendingList.isEmpty() ? View.GONE : View.VISIBLE);
        expiredLabel.setVisibility(expiredList.isEmpty() ? View.GONE : View.VISIBLE);

        pendingAdapter.notifyDataSetChanged();
        expiredAdapter.notifyDataSetChanged();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
    }

    private void requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
            }
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_PERMISSION
                );
            }
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
    protected void onDestroy() {
        super.onDestroy();
        if (reminderExpiredReceiver != null) {
            unregisterReceiver(reminderExpiredReceiver);
        }
    }
}
