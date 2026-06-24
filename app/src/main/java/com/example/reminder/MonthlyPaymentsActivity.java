package com.example.reminder;

import com.example.reminder.sync.SyncManager;
import android.util.Log;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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
    private android.widget.TextView txtNoPayments;

    private BroadcastReceiver syncCompletedReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monthly_payments);

        dbHelper = new PaymentDatabaseHelper(this);
        Button addButton = findViewById(R.id.addPaymentButton);
        Button clearAllButton = findViewById(R.id.clearAllBtn);
        RecyclerView recyclerView = findViewById(R.id.paymentRecyclerView);
        txtNoPayments = findViewById(R.id.txtNoPayments);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        payments = dbHelper.getAllPayments();
        adapter = new MonthlyPaymentAdapter(this, payments, dbHelper);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        if (txtNoPayments != null) {
            txtNoPayments.setVisibility(payments.isEmpty() ? View.VISIBLE : View.GONE);
        }

        addButton.setOnClickListener(v -> {
            showPaymentCreateDialog("");
        });

        clearAllButton.setOnClickListener(v -> {
            // Cancel all scheduled alarms before clearing
            for (MonthlyPayment payment : payments) {
                AlarmUtils.cancelPaymentAlarm(this, payment.getId(), payment.getName());
                AlarmUtils.cancelNotification(this, payment.getId());
                dbHelper.softDeletePayment(payment.getId());
            }
            payments.clear();
            adapter.notifyDataSetChanged();
            if (txtNoPayments != null) {
                txtNoPayments.setVisibility(View.VISIBLE);
            }

            Toast.makeText(this, "All payments deleted", Toast.LENGTH_SHORT).show();
            ReminderApplication.enqueueSyncWorker(this);
        });

        syncCompletedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d("MonthlyPayments", "SYNC_COMPLETED received");
                System.out.println("SYNC_COMPLETED received");
                runOnUiThread(() -> {
                    if (payments != null && dbHelper != null && adapter != null) {
                        payments.clear();
                        payments.addAll(dbHelper.getAllPayments());
                        adapter.notifyDataSetChanged();
                        if (txtNoPayments != null) {
                            txtNoPayments.setVisibility(payments.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                        Log.d("MonthlyPayments", "UI refresh executed");
                        System.out.println("UI refresh executed");
                        Log.d("MonthlyPayments", "Dataset size after refresh: " + payments.size());
                        System.out.println("Dataset size after refresh: " + payments.size());
                    }
                });
            }
        };
        registerReceiver(syncCompletedReceiver, new IntentFilter(SyncManager.ACTION_SYNC_COMPLETED),
                Context.RECEIVER_NOT_EXPORTED);
    }

    private void showPaymentCreateDialog(String initialName) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle("Add Payment Reminder");

        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_create_payment, null);
        builder.setView(dialogView);

        final com.google.android.material.textfield.TextInputEditText nameInput = dialogView.findViewById(R.id.dialogPaymentNameInput);
        nameInput.setText(initialName);

        final com.google.android.material.textfield.TextInputEditText amountInput = dialogView.findViewById(R.id.dialogPaymentAmountInput);
        
        final android.widget.Spinner recurrenceSpinner = dialogView.findViewById(R.id.dialogRecurrenceSpinner);
        android.widget.ArrayAdapter<String> spinnerAdapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"One-Time", "Monthly", "Quarterly", "Yearly"});
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        recurrenceSpinner.setAdapter(spinnerAdapter);
        recurrenceSpinner.setSelection(1); // Default to Monthly

        final com.google.android.material.button.MaterialButton dateButton = dialogView.findViewById(R.id.dialogDateButton);
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
            if (spinnerPos == 0) {
                recurrence = RecurrenceType.ONE_TIME;
            } else if (spinnerPos == 1) {
                recurrence = RecurrenceType.MONTHLY;
            } else if (spinnerPos == 2) {
                recurrence = RecurrenceType.QUARTERLY;
            } else if (spinnerPos == 3) {
                recurrence = RecurrenceType.YEARLY;
            }

            long dueDateMillis = dueCalendar.getTimeInMillis();
            String notificationOffsets = "0";

            int paymentId = dbHelper.insertPayment(name, dueDateMillis, false, amount, recurrence, notificationOffsets);

            if (dueDateMillis <= System.currentTimeMillis()) {
                Log.d("PAYMENT SCHEDULER", "Triggering immediate payment notification:\nlocalId=" + paymentId + "\nserverId=-1\ndueDate=" + dueDateMillis);
                AlarmUtils.showMonthlyPaymentNotification(this, paymentId, name);
            } else {
                Log.d("PAYMENT SCHEDULER", "Scheduling payment:\nlocalId=" + paymentId + "\nserverId=-1\ndueDate=" + dueDateMillis + "\nsuccess=true");
                AlarmUtils.schedulePaymentAlarm(this, paymentId, name, dueDateMillis);
            }

            payments.clear();
            payments.addAll(dbHelper.getAllPayments());
            adapter.notifyDataSetChanged();

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
            if (txtNoPayments != null) {
                txtNoPayments.setVisibility(payments.isEmpty() ? View.VISIBLE : View.GONE);
            }
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
                                if (txtNoPayments != null) {
                                    txtNoPayments.setVisibility(payments.isEmpty() ? View.VISIBLE : View.GONE);
                                }
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
