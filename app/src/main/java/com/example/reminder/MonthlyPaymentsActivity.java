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

    private BroadcastReceiver syncCompletedReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monthly_payments);

        dbHelper = new PaymentDatabaseHelper(this);
        Button addButton = findViewById(R.id.addPaymentButton);
        Button clearAllButton = findViewById(R.id.clearAllBtn);
        RecyclerView recyclerView = findViewById(R.id.paymentRecyclerView);

        payments = dbHelper.getAllPayments();
        adapter = new MonthlyPaymentAdapter(this, payments, dbHelper);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        addButton.setOnClickListener(v -> {
            showPaymentCreateDialog("");
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
                Log.d("MonthlyPayments", "UI refresh received");
                System.out.println("UI refresh received");
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

        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_create_payment, null);
        builder.setView(dialogView);

        final com.google.android.material.textfield.TextInputEditText nameInput = dialogView.findViewById(R.id.dialogPaymentNameInput);
        nameInput.setText(initialName);

        final com.google.android.material.textfield.TextInputEditText amountInput = dialogView.findViewById(R.id.dialogPaymentAmountInput);
        
        final android.widget.Spinner recurrenceSpinner = dialogView.findViewById(R.id.dialogRecurrenceSpinner);
        android.widget.ArrayAdapter<String> spinnerAdapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Monthly", "Quarterly", "Yearly"});
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        recurrenceSpinner.setAdapter(spinnerAdapter);

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

        final android.widget.CheckBox checkbox7d = dialogView.findViewById(R.id.checkboxNotify7d);
        final android.widget.CheckBox checkbox3d = dialogView.findViewById(R.id.checkboxNotify3d);
        final android.widget.CheckBox checkbox1d = dialogView.findViewById(R.id.checkboxNotify1d);
        final android.widget.CheckBox checkboxDue = dialogView.findViewById(R.id.checkboxNotifyDue);

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

            // Construct notificationOffsets based on checkboxes
            java.util.List<String> offsetsList = new java.util.ArrayList<>();
            if (checkbox7d.isChecked()) offsetsList.add("7");
            if (checkbox3d.isChecked()) offsetsList.add("3");
            if (checkbox1d.isChecked()) offsetsList.add("1");
            if (checkboxDue.isChecked()) offsetsList.add("0");

            String notificationOffsets = "0"; // Default fallback
            if (!offsetsList.isEmpty()) {
                notificationOffsets = android.text.TextUtils.join(",", offsetsList);
            }

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
