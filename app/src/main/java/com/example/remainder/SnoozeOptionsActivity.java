package com.example.remainder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Calendar;

public class SnoozeOptionsActivity extends AppCompatActivity {

    private int reminderId;
    private String reminderText;

    private static final String TAG = "SnoozeOptionsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_snooze_options);

        // Get extras from intent
        Intent intent = getIntent();
        reminderId = intent.getIntExtra("reminder_id", -1);
        reminderText = intent.getStringExtra("reminder_text");

        if (reminderId == -1 || reminderText == null) {
            Log.e(TAG, "Missing reminder data");
            Toast.makeText(this, "Error: Missing reminder data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Button btn1Min = findViewById(R.id.btn_snooze_1_min);
        Button btn5Min = findViewById(R.id.btn_snooze_5_min);
        Button btn10Min = findViewById(R.id.btn_snooze_10_min);

        btn1Min.setOnClickListener(v -> snoozeReminder(1));
        btn5Min.setOnClickListener(v -> snoozeReminder(5));
        btn10Min.setOnClickListener(v -> snoozeReminder(10));
    }

    private void snoozeReminder(int minutes) {
        long snoozeTimeMillis = Calendar.getInstance().getTimeInMillis() + minutes * 60 * 1000;

        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("reminder_id", reminderId);
        intent.putExtra("reminder_text", reminderText);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                reminderId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    snoozeTimeMillis,
                    pendingIntent
            );
            Toast.makeText(this, "Reminder snoozed for " + minutes + " minutes", Toast.LENGTH_SHORT).show();
        } else {
            Log.e(TAG, "AlarmManager is null");
            Toast.makeText(this, "Failed to snooze reminder", Toast.LENGTH_SHORT).show();
        }

        finish(); // ✅ Closes the screen after snoozing
    }
}
