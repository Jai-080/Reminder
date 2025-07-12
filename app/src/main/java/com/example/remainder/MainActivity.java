package com.example.remainder;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;
    private static final String CHANNEL_ID = "reminder_channel";
    private static final String TAG = "MainActivity";

    private EditText quickNoteInput;
    private ImageView addNoteButton;
    private RecyclerView quickNotesRecycler;
    private QuickNoteAdapter quickNoteAdapter;
    private ArrayList<QuickNote> noteList = new ArrayList<>();
    private QuickNoteDatabaseHelper noteDbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createNotificationChannel();
        requestExactAlarmPermission();
        requestNotificationPermission();

        Button btnMonthlyPayments = findViewById(R.id.monthlyPaymentsBtn);
        btnMonthlyPayments.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MonthlyPaymentsActivity.class);
            startActivity(intent);
        });

        Button btnTimedReminders = findViewById(R.id.timedRemindersBtn);
        btnTimedReminders.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, TimedRemindersActivity.class);
            startActivity(intent);
        });

        noteDbHelper = new QuickNoteDatabaseHelper(this);
        setupQuickNotes();
    }

    private void setupQuickNotes() {
        quickNoteInput = findViewById(R.id.editTextQuickNote);
        addNoteButton = findViewById(R.id.btnAddQuickNote);
        quickNotesRecycler = findViewById(R.id.recyclerQuickNotes);

        noteList = noteDbHelper.getAllNotes();
        quickNoteAdapter = new QuickNoteAdapter(this, noteList, noteDbHelper);
        quickNotesRecycler.setAdapter(quickNoteAdapter);
        quickNotesRecycler.setLayoutManager(new LinearLayoutManager(this));

        addNoteButton.setOnClickListener(v -> {
            String noteText = quickNoteInput.getText().toString().trim();
            if (!TextUtils.isEmpty(noteText)) {
                long id = noteDbHelper.addNote(noteText);
                if (id != -1) {
                    QuickNote note = new QuickNote((int) id, noteText, false);
                    noteList.add(0, note);
                    quickNoteAdapter.notifyItemInserted(0);
                    quickNotesRecycler.scrollToPosition(0);
                    quickNoteInput.setText("");
                } else {
                    Toast.makeText(this, "Failed to add note", Toast.LENGTH_SHORT).show();
                }
            }
        });
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
}
