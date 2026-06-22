package com.example.reminder;

import com.example.reminder.auth.AuthManager;
import com.example.reminder.auth.LoginActivity;
import com.example.reminder.auth.TokenManager;
import com.example.reminder.sync.SyncManager;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;
    private static final String CHANNEL_ID = "reminder_channel";
    private static final String TAG = "MainActivity";

    private EditText quickNoteInput;
    private QuickNoteAdapter quickNoteAdapter;
    private ArrayList<QuickNote> noteList = new ArrayList<>();
    private QuickNoteDatabaseHelper noteDbHelper;
    private BroadcastReceiver syncCompletedReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check authentication status before rendering UI
        TokenManager tokenManager = TokenManager.getInstance(this);
        if (!tokenManager.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        createNotificationChannel();
        requestExactAlarmPermission();
        requestNotificationPermission();

        // Setup Logout Button
        View btnLogout = findViewById(R.id.btnLogout);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                AuthManager.getInstance(this).logout(new AuthManager.AuthCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(MainActivity.this, "Logged out successfully", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(MainActivity.this, LoginActivity.class));
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        // Success is always triggered as local prefs are wiped regardless
                    }
                });
            });
        }

        // Setup Sync Button
        View btnSync = findViewById(R.id.btnSync);
        if (btnSync != null) {
            btnSync.setOnClickListener(v -> {
                btnSync.setEnabled(false);
                Toast.makeText(this, "Syncing...", Toast.LENGTH_SHORT).show();
                SyncManager.getInstance(this).performFullSync(new SyncManager.SyncCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        runOnUiThread(() -> {
                            btnSync.setEnabled(true);
                            noteList.clear();
                            noteList.addAll(noteDbHelper.getAllNotes());
                            quickNoteAdapter.notifyDataSetChanged();
                            QuickNotesWidgetProvider.updateWidget(getApplicationContext());
                            Toast.makeText(MainActivity.this, "Sync completed!", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            btnSync.setEnabled(true);
                            Toast.makeText(MainActivity.this, "Sync failed: " + error, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            });
        }

        // ✅ Fixed: View instead of Button — XML now uses LinearLayout
        View btnMonthlyPayments = findViewById(R.id.monthlyPaymentsBtn);
        btnMonthlyPayments.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, MonthlyPaymentsActivity.class));
        });

        View btnTimedReminders = findViewById(R.id.timedRemindersBtn);
        btnTimedReminders.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, TimedRemindersActivity.class));
        });

        noteDbHelper = new QuickNoteDatabaseHelper(this);
        setupQuickNotes();

        syncCompletedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "SYNC_COMPLETED received");
                System.out.println("SYNC_COMPLETED received");
                runOnUiThread(() -> {
                    if (noteDbHelper != null && quickNoteAdapter != null && noteList != null) {
                        noteList.clear();
                        noteList.addAll(noteDbHelper.getAllNotes());
                        quickNoteAdapter.notifyDataSetChanged();
                        QuickNotesWidgetProvider.updateWidget(getApplicationContext());
                        Log.d(TAG, "UI refresh executed");
                        System.out.println("UI refresh executed");
                        Log.d(TAG, "Dataset size after refresh: " + noteList.size());
                        System.out.println("Dataset size after refresh: " + noteList.size());
                    }
                });
            }
        };
        registerReceiver(syncCompletedReceiver, new IntentFilter(SyncManager.ACTION_SYNC_COMPLETED),
                Context.RECEIVER_NOT_EXPORTED);
    }

    private void setupQuickNotes() {
        quickNoteInput = findViewById(R.id.editTextQuickNote);
        ImageView addNoteButton = findViewById(R.id.btnAddQuickNote);
        RecyclerView quickNotesRecycler = findViewById(R.id.recyclerQuickNotes);
        TextView btnClearAll = findViewById(R.id.btnClearAllNotes);

        noteList = noteDbHelper.getAllNotes();
        quickNoteAdapter = new QuickNoteAdapter(this, noteList, noteDbHelper);
        quickNotesRecycler.setAdapter(quickNoteAdapter);
        quickNotesRecycler.setLayoutManager(new LinearLayoutManager(this));

        btnClearAll.setOnClickListener(v -> {
            if (noteList.isEmpty()) return;
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Clear All Notes")
                    .setMessage("Are you sure you want to delete all notes?")
                    .setPositiveButton("Clear All", (dialog, which) -> {
                        noteDbHelper.clearAllNotes();
                        noteList.clear();
                        quickNoteAdapter.notifyDataSetChanged();
                        QuickNotesWidgetProvider.updateWidget(getApplicationContext());
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                quickNoteAdapter.onItemMove(fromPosition, toPosition);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // Not implemented
            }
        });
        itemTouchHelper.attachToRecyclerView(quickNotesRecycler);

        addNoteButton.setOnClickListener(v -> {
            String noteText = quickNoteInput.getText().toString().trim();
            if (!TextUtils.isEmpty(noteText)) {
                long id = noteDbHelper.addNote(noteText);
                if (id != -1) {
                    int localId = (int) id;
                    QuickNote note = new QuickNote(localId, null, noteText, false, noteList.size() + 1, "PENDING");
                    noteList.add(note);
                    quickNoteAdapter.notifyItemInserted(noteList.size() - 1);
                    quickNotesRecycler.scrollToPosition(noteList.size() - 1);
                    quickNoteInput.setText("");
                    QuickNotesWidgetProvider.updateWidget(getApplicationContext());

                    // Sync to backend
                    SyncManager.getInstance(MainActivity.this).uploadNote(localId, noteText, false, note.getPosition(), null, new SyncManager.SyncCallback<Long>() {
                        @Override
                        public void onSuccess(Long serverId) {
                            Log.d(TAG, "Note sync succeeded, serverId: " + serverId);
                            note.setServerId(serverId);
                            note.setSyncStatus("SYNCED");
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Note sync failed: " + error);
                            note.setSyncStatus("FAILED");
                            Toast.makeText(MainActivity.this, "Sync error: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    Toast.makeText(this, "Failed to add note", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void createNotificationChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            NotificationChannel existingChannel = manager.getNotificationChannel(CHANNEL_ID);
            if (existingChannel == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Reminders",
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Channel for reminder notifications");
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                channel.enableLights(true);
                channel.enableVibration(true);
                manager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created: " + CHANNEL_ID);
            } else {
                Log.d(TAG, "Notification channel already exists: " + CHANNEL_ID);
            }
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

    @Override
    protected void onResume() {
        super.onResume();
        TokenManager tokenManager = TokenManager.getInstance(this);
        if (tokenManager.isLoggedIn()) {
            // Reload local list first in case details changed offline or on another screen
            noteList.clear();
            noteList.addAll(noteDbHelper.getAllNotes());
            quickNoteAdapter.notifyDataSetChanged();

            // Rate-limit auto-sync to once every 5 minutes (300000ms)
            long lastSync = tokenManager.getLastSyncTimestamp();
            if (System.currentTimeMillis() - lastSync > 300000) {
                Log.d(TAG, "Rate limit passed, starting auto-sync...");
                SyncManager.getInstance(this).performFullSync(new SyncManager.SyncCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        runOnUiThread(() -> {
                            noteList.clear();
                            noteList.addAll(noteDbHelper.getAllNotes());
                            quickNoteAdapter.notifyDataSetChanged();
                            QuickNotesWidgetProvider.updateWidget(getApplicationContext());
                        });
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Auto-sync failed: " + error);
                    }
                });
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (syncCompletedReceiver != null) {
            unregisterReceiver(syncCompletedReceiver);
        }
    }
}
