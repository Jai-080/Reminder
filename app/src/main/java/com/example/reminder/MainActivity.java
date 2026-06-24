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
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;
    private static final String CHANNEL_ID = "reminder_channel";
    private static final String TAG = "MainActivity";

    private EditText quickNoteInput;
    private QuickNoteAdapter quickNoteAdapter;
    private ArrayList<QuickNote> noteList = new ArrayList<>();
    private QuickNoteDatabaseHelper noteDbHelper;
    private BroadcastReceiver syncCompletedReceiver;

    // Database Helpers
    private ReminderDatabaseHelper reminderDbHelper;
    private PaymentDatabaseHelper paymentDbHelper;

    // Dashboard Views
    private TextView txtWelcomeTitle;
    private TextView txtLastSync;
    private TextView txtNotesCount;
    private TextView txtNotesSub;
    private TextView txtRemindersCount;
    private TextView txtRemindersSub;
    private TextView txtPaymentsCount;
    private TextView txtPaymentsSub;

    private android.widget.LinearLayout layoutUpcomingReminders;
    private android.widget.LinearLayout layoutUpcomingPayments;
    private TextView txtNoUpcomingReminders;
    private TextView txtNoUpcomingPayments;
    private TextView txtNextPaymentDetail;


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
                            ArrayList<QuickNote> dbNotes = noteDbHelper.getAllNotes();
                            sortNotes(dbNotes);
                            noteList.addAll(dbNotes);
                            quickNoteAdapter.notifyDataSetChanged();
                            QuickNotesWidgetProvider.updateWidget(getApplicationContext());
                            refreshDashboardStats();
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

        // ✅ Fixed: View instead of Button — XML now uses CardViews
        View btnMonthlyPayments = findViewById(R.id.monthlyPaymentsBtn);
        if (btnMonthlyPayments != null) {
            btnMonthlyPayments.setOnClickListener(v -> {
                startActivity(new Intent(MainActivity.this, MonthlyPaymentsActivity.class));
            });
        }

        View btnTimedReminders = findViewById(R.id.timedRemindersBtn);
        if (btnTimedReminders != null) {
            btnTimedReminders.setOnClickListener(v -> {
                startActivity(new Intent(MainActivity.this, TimedRemindersActivity.class));
            });
        }

        View sectionUpcomingReminders = findViewById(R.id.sectionUpcomingReminders);
        if (sectionUpcomingReminders != null) {
            sectionUpcomingReminders.setOnClickListener(v -> {
                startActivity(new Intent(MainActivity.this, TimedRemindersActivity.class));
            });
        }

        View sectionUpcomingPayments = findViewById(R.id.sectionUpcomingPayments);
        if (sectionUpcomingPayments != null) {
            sectionUpcomingPayments.setOnClickListener(v -> {
                startActivity(new Intent(MainActivity.this, MonthlyPaymentsActivity.class));
            });
        }

        noteDbHelper = new QuickNoteDatabaseHelper(this);
        reminderDbHelper = new ReminderDatabaseHelper(this);
        paymentDbHelper = new PaymentDatabaseHelper(this);

        txtWelcomeTitle = findViewById(R.id.txtWelcomeTitle);
        txtLastSync = findViewById(R.id.txtLastSync);
        txtNotesCount = findViewById(R.id.txtNotesCount);
        txtNotesSub = findViewById(R.id.txtNotesSub);
        txtRemindersCount = findViewById(R.id.txtRemindersCount);
        txtRemindersSub = findViewById(R.id.txtRemindersSub);
        txtPaymentsCount = findViewById(R.id.txtPaymentsCount);
        txtPaymentsSub = findViewById(R.id.txtPaymentsSub);

        layoutUpcomingReminders = findViewById(R.id.layoutUpcomingReminders);
        layoutUpcomingPayments = findViewById(R.id.layoutUpcomingPayments);
        txtNoUpcomingReminders = findViewById(R.id.txtNoUpcomingReminders);
        txtNoUpcomingPayments = findViewById(R.id.txtNoUpcomingPayments);
        txtNextPaymentDetail = findViewById(R.id.txtNextPaymentDetail);


        setupQuickNotes();

        syncCompletedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "SYNC_COMPLETED received");
                System.out.println("SYNC_COMPLETED received");
                runOnUiThread(() -> {
                    if (noteDbHelper != null && quickNoteAdapter != null && noteList != null) {
                        noteList.clear();
                        ArrayList<QuickNote> dbNotes = noteDbHelper.getAllNotes();
                        sortNotes(dbNotes);
                        noteList.addAll(dbNotes);
                        quickNoteAdapter.notifyDataSetChanged();
                        QuickNotesWidgetProvider.updateWidget(getApplicationContext());
                        refreshDashboardStats();
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

    private void sortNotes(List<QuickNote> list) {
        list.sort((n1, n2) -> {
            if (n1.isCompleted() != n2.isCompleted()) {
                return Boolean.compare(n1.isCompleted(), n2.isCompleted());
            }
            return Integer.compare(n1.getPosition(), n2.getPosition());
        });
    }

    private void setupQuickNotes() {
        quickNoteInput = findViewById(R.id.editTextQuickNote);
        ImageView addNoteButton = findViewById(R.id.btnAddQuickNote);
        RecyclerView quickNotesRecycler = findViewById(R.id.recyclerQuickNotes);
        TextView btnClearAll = findViewById(R.id.btnClearAllNotes);

        noteList = noteDbHelper.getAllNotes();
        sortNotes(noteList);
        quickNoteAdapter = new QuickNoteAdapter(this, noteList, noteDbHelper);
        quickNotesRecycler.setAdapter(quickNoteAdapter);
        quickNotesRecycler.setLayoutManager(new LinearLayoutManager(this));

        // Scroll to checklist when Quick Notes summary card is clicked
        View quickNotesCard = findViewById(R.id.quickNotesSummaryCard);
        View notesSectionCard = findViewById(R.id.notesSectionCard);
        androidx.core.widget.NestedScrollView scrollView = findViewById(R.id.scrollView);
        if (quickNotesCard != null && notesSectionCard != null && scrollView != null) {
            quickNotesCard.setOnClickListener(v -> {
                scrollView.post(() -> scrollView.smoothScrollTo(0, notesSectionCard.getTop()));
            });
        }

        btnClearAll.setOnClickListener(v -> {
            if (noteList.isEmpty()) return;
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Clear All Notes")
                    .setMessage("Are you sure you want to delete all notes?")
                    .setPositiveButton("Clear All", (dialog, which) -> {
                        for (QuickNote note : noteList) {
                            noteDbHelper.softDeleteNote(note.getId());
                        }
                        noteList.clear();
                        quickNoteAdapter.notifyDataSetChanged();
                        QuickNotesWidgetProvider.updateWidget(getApplicationContext());
                        refreshDashboardStats();
                        ReminderApplication.enqueueSyncWorker(this);
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
                refreshDashboardStats();
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
                    int insertIndex = 0;
                    while (insertIndex < noteList.size() && !noteList.get(insertIndex).isCompleted()) {
                        insertIndex++;
                    }
                    noteList.add(insertIndex, note);
                    quickNoteAdapter.notifyItemInserted(insertIndex);
                    quickNotesRecycler.scrollToPosition(insertIndex);
                    quickNoteInput.setText("");
                    QuickNotesWidgetProvider.updateWidget(getApplicationContext());
                    refreshDashboardStats();

                    // Sync to backend
                    SyncManager.getInstance(MainActivity.this).uploadNote(localId, noteText, false, note.getPosition(), null, new SyncManager.SyncCallback<Long>() {
                        @Override
                        public void onSuccess(Long serverId) {
                            Log.d(TAG, "Note sync succeeded, serverId: " + serverId);
                            note.setServerId(serverId);
                            note.setSyncStatus("SYNCED");
                            runOnUiThread(() -> refreshDashboardStats());
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
            ArrayList<QuickNote> dbNotes = noteDbHelper.getAllNotes();
            sortNotes(dbNotes);
            noteList.addAll(dbNotes);
            quickNoteAdapter.notifyDataSetChanged();
            refreshDashboardStats();

            // Rate-limit auto-sync to once every 5 minutes (300000ms)
            long lastSync = tokenManager.getLastSyncTimestamp();
            if (System.currentTimeMillis() - lastSync > 300000) {
                Log.d(TAG, "Rate limit passed, starting auto-sync...");
                SyncManager.getInstance(this).performFullSync(new SyncManager.SyncCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        runOnUiThread(() -> {
                            noteList.clear();
                            ArrayList<QuickNote> dbNotes2 = noteDbHelper.getAllNotes();
                            sortNotes(dbNotes2);
                            noteList.addAll(dbNotes2);
                            quickNoteAdapter.notifyDataSetChanged();
                            QuickNotesWidgetProvider.updateWidget(getApplicationContext());
                            refreshDashboardStats();
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

    public void refreshDashboardStats() {
        // 1. Welcome and sync metadata
        TokenManager tokenManager = TokenManager.getInstance(this);
        String username = tokenManager.getUsername();
        if (txtWelcomeTitle != null) {
            txtWelcomeTitle.setText("Welcome back, " + (username != null && !username.isEmpty() ? username : "User") + "!");
        }
        
        long lastSync = tokenManager.getLastSyncTimestamp();
        if (txtLastSync != null) {
            if (lastSync == 0) {
                txtLastSync.setText("Last Synced: Never");
            } else {
                java.text.SimpleDateFormat dateSdf = new java.text.SimpleDateFormat("MMM dd, ", java.util.Locale.getDefault());
                java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
                java.util.Date dateObj = new java.util.Date(lastSync);
                txtLastSync.setText("Last Synced: " + dateSdf.format(dateObj) + timeFormat.format(dateObj));
            }
        }

        // 2. Quick Notes stats
        int totalNotes = noteList.size();
        int incompleteNotes = 0;
        for (QuickNote note : noteList) {
            if (!note.isCompleted()) {
                incompleteNotes++;
            }
        }
        if (txtNotesCount != null) txtNotesCount.setText(String.valueOf(totalNotes));
        if (txtNotesSub != null) txtNotesSub.setText(incompleteNotes + " incomplete");

        // 3. Reminders stats & list
        if (reminderDbHelper != null) {
            ArrayList<Reminder> pendingReminders = new ArrayList<>();
            ArrayList<Reminder> expiredReminders = new ArrayList<>();
            List<Reminder> allReminders = reminderDbHelper.getAllReminders();
            long now = System.currentTimeMillis();
            for (Reminder r : allReminders) {
                if (r.getTime() <= now) {
                    expiredReminders.add(r);
                } else {
                    pendingReminders.add(r);
                }
            }
            if (txtRemindersCount != null) txtRemindersCount.setText(String.valueOf(pendingReminders.size()));
            if (txtRemindersSub != null) txtRemindersSub.setText(pendingReminders.size() + " pending | " + expiredReminders.size() + " expired");

            // Populate upcoming reminders
            if (layoutUpcomingReminders != null) {
                layoutUpcomingReminders.removeAllViews();
                int count = 0;
                java.text.SimpleDateFormat timeSdf = new java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault());
                for (Reminder r : pendingReminders) {
                    if (count >= 3) break;
                    TextView tv = new TextView(this);
                    tv.setText("• " + r.getText() + " (" + timeSdf.format(new java.util.Date(r.getTime())) + ")");
                    tv.setTextColor(getResources().getColor(R.color.colorTextSecondary, getTheme()));
                    tv.setTextSize(14);
                    tv.setPadding(0, 8, 0, 8);
                    layoutUpcomingReminders.addView(tv);
                    count++;
                }
                if (txtNoUpcomingReminders != null) {
                    txtNoUpcomingReminders.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
                }
            }
        }

        // 4. Payments stats & list
        if (paymentDbHelper != null) {
            ArrayList<MonthlyPayment> allPayments = paymentDbHelper.getAllPayments();
            int upcomingCount = 0;
            int overduePayments = 0;
            ArrayList<MonthlyPayment> upcomingPaymentsList = new ArrayList<>();
            long now = System.currentTimeMillis();

            java.util.Calendar cal = java.util.Calendar.getInstance();
            int currentMonth = cal.get(java.util.Calendar.MONTH) + 1; // 1-based
            int currentYear = cal.get(java.util.Calendar.YEAR);

            for (MonthlyPayment p : allPayments) {
                if (p.isUpcoming(currentMonth, currentYear)) {
                    upcomingCount++;
                    upcomingPaymentsList.add(p);
                    if (p.getDueDate() < now) {
                        overduePayments++;
                    }
                }
            }
            if (txtPaymentsCount != null) txtPaymentsCount.setText(String.valueOf(upcomingCount));
            
            java.util.Collections.sort(upcomingPaymentsList, (p1, p2) -> Long.compare(p1.getDueDate(), p2.getDueDate()));
            // Calculate due this calendar month
            int dueThisMonth = 0;
            for (MonthlyPayment p : upcomingPaymentsList) {
                java.util.Calendar dueCal = java.util.Calendar.getInstance();
                dueCal.setTimeInMillis(p.getDueDate());
                if (dueCal.get(java.util.Calendar.MONTH) + 1 == currentMonth && dueCal.get(java.util.Calendar.YEAR) == currentYear) {
                    dueThisMonth++;
                }
            }
            if (txtPaymentsSub != null) txtPaymentsSub.setText(overduePayments + " overdue | " + dueThisMonth + " due");

            if (txtNextPaymentDetail != null) {
                MonthlyPayment nextThisMonth = null;
                for (MonthlyPayment p : upcomingPaymentsList) {
                    java.util.Calendar dueCal = java.util.Calendar.getInstance();
                    dueCal.setTimeInMillis(p.getDueDate());
                    if (dueCal.get(java.util.Calendar.MONTH) + 1 == currentMonth && dueCal.get(java.util.Calendar.YEAR) == currentYear) {
                        nextThisMonth = p;
                        break;
                    }
                }
                if (nextThisMonth == null) {
                    txtNextPaymentDetail.setText("No upcoming payments for the month");
                } else {
                    java.text.SimpleDateFormat dateSdf = new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault());
                    String amtStr = nextThisMonth.getAmount() == null ? "" : String.format(" (₹%.2f)", nextThisMonth.getAmount());
                    txtNextPaymentDetail.setText(nextThisMonth.getName() + amtStr + "\nDue: " + dateSdf.format(new java.util.Date(nextThisMonth.getDueDate())));
                }
            }

            // Populate upcoming payments
            if (layoutUpcomingPayments != null) {
                layoutUpcomingPayments.removeAllViews();
                int count = 0;
                java.text.SimpleDateFormat dateSdf = new java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault());
                for (MonthlyPayment p : upcomingPaymentsList) {
                    java.util.Calendar dueCal = java.util.Calendar.getInstance();
                    dueCal.setTimeInMillis(p.getDueDate());
                    if (dueCal.get(java.util.Calendar.MONTH) + 1 == currentMonth && dueCal.get(java.util.Calendar.YEAR) == currentYear) {
                        if (count >= 3) break;
                        TextView tv = new TextView(this);
                        String amtStr = p.getAmount() == null ? "" : String.format(" (₹%.2f)", p.getAmount());
                        tv.setText("• " + p.getName() + amtStr + " - Due: " + dateSdf.format(new java.util.Date(p.getDueDate())));
                        tv.setTextColor(getResources().getColor(R.color.colorTextSecondary, getTheme()));
                        tv.setTextSize(14);
                        tv.setPadding(0, 8, 0, 8);
                        layoutUpcomingPayments.addView(tv);
                        count++;
                    }
                }
                if (txtNoUpcomingPayments != null) {
                    txtNoUpcomingPayments.setText("No upcoming payments for the month");
                    txtNoUpcomingPayments.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
                }
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
