package com.example.reminder;

import com.example.reminder.sync.SyncManager;
import android.util.Log;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import java.util.ArrayList;
import java.util.Calendar;

public class MonthlyPaymentsActivity extends AppCompatActivity {

    private PaymentDatabaseHelper dbHelper;
    private MonthlyPaymentAdapter adapter;
    private ArrayList<MonthlyPayment> payments;

    private EditText paymentInput;
    private BroadcastReceiver syncCompletedReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monthly_payments);

        dbHelper = new PaymentDatabaseHelper(this);
        paymentInput = findViewById(R.id.paymentInput);
        Button addButton = findViewById(R.id.addPaymentButton);
        Button clearAllButton = findViewById(R.id.clearAllBtn);
        RecyclerView recyclerView = findViewById(R.id.paymentRecyclerView);

        payments = dbHelper.getAllPayments();
        adapter = new MonthlyPaymentAdapter(this, payments, dbHelper);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        addButton.setOnClickListener(v -> {
            String name = paymentInput.getText().toString().trim();
            showPaymentCreateDialog(name);
        });

        clearAllButton.setOnClickListener(v -> {
            // Cancel all scheduled alarms before clearing
            for (MonthlyPayment payment : payments) {
                AlarmUtils.cancelPaymentAlarm(this, payment.getId(), payment.getName());
                AlarmUtils.cancelNotification(this, payment.getId());
            }
            dbHelper.deleteAllPayments();
            payments.clear();
            adapter.notifyDataSetChanged();

            Toast.makeText(this, "All payments deleted", Toast.LENGTH_SHORT).show();
        });

        syncCompletedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d("MonthlyPayments", "Sync completed broadcast received. Refreshing payments UI.");
                runOnUiThread(() -> {
                    if (payments != null && dbHelper != null && adapter != null) {
                        payments.clear();
                        payments.addAll(dbHelper.getAllPayments());
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        };
        registerReceiver(syncCompletedReceiver, new IntentFilter(SyncManager.ACTION_SYNC_COMPLETED),
                Context.RECEIVER_NOT_EXPORTED);
    }

    private void showPaymentCreateDialog(String initialName) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Add Payment Reminder");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 20);

        android.widget.TextView nameLabel = new android.widget.TextView(this);
        nameLabel.setText("Payment Name");
        nameLabel.setTextSize(14);
        nameLabel.setPadding(0, 10, 0, 5);
        layout.addView(nameLabel);

        final android.widget.EditText nameInput = new android.widget.EditText(this);
        nameInput.setText(initialName);
        nameInput.setHint("e.g. Netflix");
        nameInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        layout.addView(nameInput);

        android.widget.TextView amountLabel = new android.widget.TextView(this);
        amountLabel.setText("Amount (Optional)");
        amountLabel.setTextSize(14);
        amountLabel.setPadding(0, 20, 0, 5);
        layout.addView(amountLabel);

        final android.widget.EditText amountInput = new android.widget.EditText(this);
        amountInput.setHint("e.g. 649");
        amountInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(amountInput);

        android.widget.TextView recurrenceLabel = new android.widget.TextView(this);
        recurrenceLabel.setText("Recurrence");
        recurrenceLabel.setTextSize(14);
        recurrenceLabel.setPadding(0, 20, 0, 5);
        layout.addView(recurrenceLabel);

        final android.widget.Spinner recurrenceSpinner = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> spinnerAdapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Monthly", "Quarterly", "Yearly"});
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        recurrenceSpinner.setAdapter(spinnerAdapter);
        layout.addView(recurrenceSpinner);

        android.widget.TextView dateLabel = new android.widget.TextView(this);
        dateLabel.setText("Due Date");
        dateLabel.setTextSize(14);
        dateLabel.setPadding(0, 20, 0, 5);
        layout.addView(dateLabel);

        final android.widget.Button dateButton = new android.widget.Button(this);
        final java.util.Calendar dueCalendar = java.util.Calendar.getInstance();
        dueCalendar.set(java.util.Calendar.HOUR_OF_DAY, 9);
        dueCalendar.set(java.util.Calendar.MINUTE, 0);
        dueCalendar.set(java.util.Calendar.SECOND, 0);
        dueCalendar.set(java.util.Calendar.MILLISECOND, 0);

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault());
        dateButton.setText(sdf.format(dueCalendar.getTime()));
        
        dateButton.setOnClickListener(v -> {
            android.app.DatePickerDialog datePickerDialog = new android.app.DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        dueCalendar.set(year, month, dayOfMonth, 9, 0, 0);
                        dueCalendar.set(java.util.Calendar.MILLISECOND, 0);
                        dateButton.setText(sdf.format(dueCalendar.getTime()));
                    },
                    dueCalendar.get(java.util.Calendar.YEAR),
                    dueCalendar.get(java.util.Calendar.MONTH),
                    dueCalendar.get(java.util.Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            datePickerDialog.show();
        });
        layout.addView(dateButton);

        builder.setView(layout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter a payment name", Toast.LENGTH_SHORT).show();
                return;
            }

            Double amount = null;
            String amountStr = amountInput.getText().toString().trim();
            if (!amountStr.isEmpty()) {
                try {
                    amount = Double.parseDouble(amountStr);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            int spinnerPos = recurrenceSpinner.getSelectedItemPosition();
            RecurrenceType recurrence = RecurrenceType.MONTHLY;
            if (spinnerPos == 1) {
                recurrence = RecurrenceType.QUARTERLY;
            } else if (spinnerPos == 2) {
                recurrence = RecurrenceType.YEARLY;
            }

            long dueDateMillis = dueCalendar.getTimeInMillis();
            String notificationOffsets = "7,3,1,0";

            int paymentId = dbHelper.insertPayment(name, dueDateMillis, false, amount, recurrence, notificationOffsets);

            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            long startOfToday = cal.getTimeInMillis();

            if (dueDateMillis > System.currentTimeMillis()) {
                Log.d("PAYMENT SCHEDULER", "Scheduling payment:\nlocalId=" + paymentId + "\nserverId=-1\ndueDate=" + dueDateMillis + "\nsuccess=true");
                AlarmUtils.schedulePaymentAlarm(this, paymentId, name, dueDateMillis);
            } else if (dueDateMillis >= startOfToday) {
                Log.d("PAYMENT SCHEDULER", "Triggering immediate payment notification (due today):\nlocalId=" + paymentId + "\nserverId=-1\ndueDate=" + dueDateMillis);
                AlarmUtils.showMonthlyPaymentNotification(this, paymentId, name);
            }

            payments.clear();
            payments.addAll(dbHelper.getAllPayments());
            adapter.notifyDataSetChanged();

            paymentInput.setText("");
            Toast.makeText(this, "Payment reminder set for due date!", Toast.LENGTH_SHORT).show();

            MonthlyPayment newPayment = new MonthlyPayment(paymentId, null, name, false, dueDateMillis, "PENDING", amount, recurrence, notificationOffsets);
            SyncManager.getInstance(MonthlyPaymentsActivity.this).uploadPayment(
                    newPayment,
                    new SyncManager.SyncCallback<Long>() {
                        @Override
                        public void onSuccess(Long serverId) {
                            Log.d("MonthlyPayments", "Payment synced to server, serverId: " + serverId);
                            runOnUiThread(() -> {
                                payments.clear();
                                payments.addAll(dbHelper.getAllPayments());
                                adapter.notifyDataSetChanged();
                            });
                        }

                        @Override
                        public void onError(String error) {
                            Log.e("MonthlyPayments", "Failed to sync payment: " + error);
                        }
                    }
            );
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (payments != null && dbHelper != null && adapter != null) {
            payments.clear();
            payments.addAll(dbHelper.getAllPayments());
            adapter.notifyDataSetChanged();
        }

        com.example.reminder.auth.TokenManager tokenManager = com.example.reminder.auth.TokenManager.getInstance(this);
        if (tokenManager.isLoggedIn()) {
            long lastSync = tokenManager.getLastSyncTimestamp();
            if (System.currentTimeMillis() - lastSync > 300000) {
                SyncManager.getInstance(this).performFullSync(new SyncManager.SyncCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        runOnUiThread(() -> {
                            if (payments != null && dbHelper != null && adapter != null) {
                                payments.clear();
                                payments.addAll(dbHelper.getAllPayments());
                                adapter.notifyDataSetChanged();
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        Log.e("MonthlyPaymentsActivity", "Auto-sync failed: " + error);
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
