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
            if (payments.isEmpty()) return;
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Clear All Payments")
                    .setMessage("Are you sure you want to delete all payments?")
                    .setPositiveButton("Clear All", (dialog, which) -> {
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
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
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

    public void showPaymentCreateDialog(String initialName) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle("Add Payment Reminder");

        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_create_payment, null);
        builder.setView(dialogView);

        final com.google.android.material.textfield.TextInputEditText nameInput = dialogView.findViewById(R.id.dialogPaymentNameInput);
        nameInput.setText(initialName);

        final com.google.android.material.textfield.TextInputEditText amountInput = dialogView.findViewById(R.id.dialogPaymentAmountInput);
        final View customDaysContainer = dialogView.findViewById(R.id.dialogCustomDaysContainer);
        final com.google.android.material.textfield.TextInputEditText customDaysInput = dialogView.findViewById(R.id.dialogCustomDaysInput);
        
        final android.widget.Spinner recurrenceSpinner = dialogView.findViewById(R.id.dialogRecurrenceSpinner);
        android.widget.ArrayAdapter<String> spinnerAdapter = new android.widget.ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"One-Time", "Monthly", "Bi-monthly", "Quarterly", "Half-yearly", "Yearly", "Custom"}
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        recurrenceSpinner.setAdapter(spinnerAdapter);
        recurrenceSpinner.setSelection(1); // Default to Monthly
        customDaysContainer.setVisibility(View.GONE);

        recurrenceSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position == 6) {
                    customDaysContainer.setVisibility(View.VISIBLE);
                } else {
                    customDaysContainer.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

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

        builder.setPositiveButton("Save", null);
        builder.setNegativeButton("Cancel", null);

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            v.setEnabled(false);
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter a payment name", Toast.LENGTH_SHORT).show();
                v.setEnabled(true);
                return;
            }

            Double amount = null;
            String amountStr = amountInput.getText().toString().trim();
            if (!amountStr.isEmpty()) {
                try {
                    amount = Double.parseDouble(amountStr);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
                    v.setEnabled(true);
                    return;
                }
            }

            int spinnerPos = recurrenceSpinner.getSelectedItemPosition();
            RecurrenceType recurrence = RecurrenceType.MONTHLY;
            int customIntervalDays = 28;

            if (spinnerPos == 0) {
                recurrence = RecurrenceType.ONE_TIME;
            } else if (spinnerPos == 1) {
                recurrence = RecurrenceType.MONTHLY;
            } else if (spinnerPos == 2) {
                recurrence = RecurrenceType.BI_MONTHLY;
            } else if (spinnerPos == 3) {
                recurrence = RecurrenceType.QUARTERLY;
            } else if (spinnerPos == 4) {
                recurrence = RecurrenceType.HALF_YEARLY;
            } else if (spinnerPos == 5) {
                recurrence = RecurrenceType.YEARLY;
            } else if (spinnerPos == 6) {
                recurrence = RecurrenceType.CUSTOM_DAYS;
                String customStr = customDaysInput.getText() != null ? customDaysInput.getText().toString().trim() : "";
                if (customStr.isEmpty()) {
                    Toast.makeText(this, "Please enter recurrence interval in days", Toast.LENGTH_SHORT).show();
                    v.setEnabled(true);
                    return;
                }
                try {
                    customIntervalDays = Integer.parseInt(customStr);
                    if (customIntervalDays < 1 || customIntervalDays > 365) {
                        Toast.makeText(this, "Interval must be between 1 and 365 days", Toast.LENGTH_SHORT).show();
                        v.setEnabled(true);
                        return;
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Interval must be a whole number between 1 and 365", Toast.LENGTH_SHORT).show();
                    v.setEnabled(true);
                    return;
                }
            }

            long dueDateMillis = dueCalendar.getTimeInMillis();
            String notificationOffsets = "0";

            int paymentId = dbHelper.insertPayment(
                    name,
                    dueDateMillis,
                    false,
                    amount,
                    recurrence,
                    notificationOffsets,
                    dueCalendar.get(java.util.Calendar.DAY_OF_MONTH),
                    customIntervalDays
            );

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
            if (txtNoPayments != null) {
                txtNoPayments.setVisibility(payments.isEmpty() ? View.VISIBLE : View.GONE);
            }

            Toast.makeText(this, "Payment reminder set for due date!", Toast.LENGTH_SHORT).show();

            MonthlyPayment newPayment = new MonthlyPayment(
                    paymentId,
                    null,
                    name,
                    false,
                    dueDateMillis,
                    "PENDING",
                    amount,
                    recurrence,
                    notificationOffsets,
                    dueCalendar.get(java.util.Calendar.DAY_OF_MONTH),
                    customIntervalDays
            );

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
                                if (txtNoPayments != null) {
                                    txtNoPayments.setVisibility(payments.isEmpty() ? View.VISIBLE : View.GONE);
                                }
                            });
                        }

                        @Override
                        public void onError(String error) {
                            Log.e("MonthlyPayments", "Failed to sync payment: " + error);
                        }
                    }
            );

            dialog.dismiss();
        });
    }

    public void showPaymentEditDialog(MonthlyPayment payment) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle("Edit Payment Reminder");

        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_create_payment, null);
        builder.setView(dialogView);

        final com.google.android.material.textfield.TextInputEditText nameInput = dialogView.findViewById(R.id.dialogPaymentNameInput);
        nameInput.setText(payment.getName());

        final com.google.android.material.textfield.TextInputEditText amountInput = dialogView.findViewById(R.id.dialogPaymentAmountInput);
        if (payment.getAmount() != null) {
            amountInput.setText(String.format(java.util.Locale.US, "%.2f", payment.getAmount()));
        }

        final View customDaysContainer = dialogView.findViewById(R.id.dialogCustomDaysContainer);
        final com.google.android.material.textfield.TextInputEditText customDaysInput = dialogView.findViewById(R.id.dialogCustomDaysInput);
        customDaysInput.setText(String.valueOf(payment.getCustomIntervalDays()));

        final android.widget.Spinner recurrenceSpinner = dialogView.findViewById(R.id.dialogRecurrenceSpinner);
        android.widget.ArrayAdapter<String> spinnerAdapter = new android.widget.ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"One-Time", "Monthly", "Bi-monthly", "Quarterly", "Half-yearly", "Yearly", "Custom"}
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        recurrenceSpinner.setAdapter(spinnerAdapter);

        int selectionPos = 1;
        if (payment.getRecurrence() != null) {
            switch (payment.getRecurrence()) {
                case ONE_TIME: selectionPos = 0; break;
                case MONTHLY: selectionPos = 1; break;
                case BI_MONTHLY: selectionPos = 2; break;
                case QUARTERLY: selectionPos = 3; break;
                case HALF_YEARLY: selectionPos = 4; break;
                case YEARLY: selectionPos = 5; break;
                case CUSTOM_DAYS: selectionPos = 6; break;
            }
        }
        recurrenceSpinner.setSelection(selectionPos);
        customDaysContainer.setVisibility(selectionPos == 6 ? View.VISIBLE : View.GONE);

        recurrenceSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position == 6) {
                    customDaysContainer.setVisibility(View.VISIBLE);
                } else {
                    customDaysContainer.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        final com.google.android.material.button.MaterialButton dateButton = dialogView.findViewById(R.id.dialogDateButton);
        final java.util.Calendar dueCalendar = java.util.Calendar.getInstance();
        dueCalendar.setTimeInMillis(payment.getDueDate());

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
            datePickerDialog.show();
        });

        builder.setPositiveButton("Save", null);
        builder.setNegativeButton("Cancel", null);

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            v.setEnabled(false);
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter a payment name", Toast.LENGTH_SHORT).show();
                v.setEnabled(true);
                return;
            }

            Double amount = null;
            String amountStr = amountInput.getText().toString().trim();
            if (!amountStr.isEmpty()) {
                try {
                    amount = Double.parseDouble(amountStr);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
                    v.setEnabled(true);
                    return;
                }
            }

            int spinnerPos = recurrenceSpinner.getSelectedItemPosition();
            RecurrenceType recurrence = RecurrenceType.MONTHLY;
            int customIntervalDays = 28;

            if (spinnerPos == 0) {
                recurrence = RecurrenceType.ONE_TIME;
            } else if (spinnerPos == 1) {
                recurrence = RecurrenceType.MONTHLY;
            } else if (spinnerPos == 2) {
                recurrence = RecurrenceType.BI_MONTHLY;
            } else if (spinnerPos == 3) {
                recurrence = RecurrenceType.QUARTERLY;
            } else if (spinnerPos == 4) {
                recurrence = RecurrenceType.HALF_YEARLY;
            } else if (spinnerPos == 5) {
                recurrence = RecurrenceType.YEARLY;
            } else if (spinnerPos == 6) {
                recurrence = RecurrenceType.CUSTOM_DAYS;
                String customStr = customDaysInput.getText() != null ? customDaysInput.getText().toString().trim() : "";
                if (customStr.isEmpty()) {
                    Toast.makeText(this, "Please enter recurrence interval in days", Toast.LENGTH_SHORT).show();
                    v.setEnabled(true);
                    return;
                }
                try {
                    customIntervalDays = Integer.parseInt(customStr);
                    if (customIntervalDays < 1 || customIntervalDays > 365) {
                        Toast.makeText(this, "Interval must be between 1 and 365 days", Toast.LENGTH_SHORT).show();
                        v.setEnabled(true);
                        return;
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Interval must be a whole number between 1 and 365", Toast.LENGTH_SHORT).show();
                    v.setEnabled(true);
                    return;
                }
            }

            String oldName = payment.getName();
            long newDueDateMillis = dueCalendar.getTimeInMillis();

            payment.setName(name);
            payment.setAmount(amount);
            payment.setDueDateMillis(newDueDateMillis);
            payment.setOriginalDueDay(dueCalendar.get(java.util.Calendar.DAY_OF_MONTH));
            payment.setRecurrence(recurrence);
            payment.setCustomIntervalDays(recurrence == RecurrenceType.CUSTOM_DAYS ? customIntervalDays : 28);
            payment.setSyncStatus("PENDING");

            dbHelper.updatePayment(payment);

            // Reschedule alarm
            AlarmUtils.cancelPaymentAlarm(this, payment.getId(), oldName);
            AlarmUtils.cancelNotification(this, payment.getId());

            if (newDueDateMillis <= System.currentTimeMillis()) {
                AlarmUtils.showMonthlyPaymentNotification(this, payment.getId(), payment.getName());
            } else {
                AlarmUtils.schedulePaymentAlarm(this, payment.getId(), payment.getName(), newDueDateMillis);
            }

            payments.clear();
            payments.addAll(dbHelper.getAllPayments());
            adapter.notifyDataSetChanged();
            if (txtNoPayments != null) {
                txtNoPayments.setVisibility(payments.isEmpty() ? View.VISIBLE : View.GONE);
            }

            Toast.makeText(this, "Payment updated", Toast.LENGTH_SHORT).show();

            SyncManager.getInstance(MonthlyPaymentsActivity.this).uploadPayment(
                    payment,
                    new SyncManager.SyncCallback<Long>() {
                        @Override
                        public void onSuccess(Long serverId) {
                            Log.d("MonthlyPayments", "Payment update synced to server");
                        }

                        @Override
                        public void onError(String error) {
                            Log.e("MonthlyPayments", "Failed to sync payment update: " + error);
                        }
                    }
            );

            dialog.dismiss();
        });
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
