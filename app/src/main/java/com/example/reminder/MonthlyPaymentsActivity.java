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

import java.util.ArrayList;
import java.util.Calendar;

public class MonthlyPaymentsActivity extends AppCompatActivity {

    private PaymentDatabaseHelper dbHelper;
    private MonthlyPaymentAdapter adapter;
    private ArrayList<MonthlyPayment> payments;

    private EditText paymentInput;

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
            if (!name.isEmpty()) {
                showDatePickerAndAdd(name);
            } else {
                Toast.makeText(this, "Please enter a payment name", Toast.LENGTH_SHORT).show();
            }
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
    }

    private void showDatePickerAndAdd(String paymentName) {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    // Set time to 9:00 AM on the selected due date
                    Calendar dueCalendar = Calendar.getInstance();
                    dueCalendar.set(year, month, dayOfMonth, 9, 0, 0);
                    dueCalendar.set(Calendar.MILLISECOND, 0);
                    long dueDateMillis = dueCalendar.getTimeInMillis();

                    // Insert into DB
                    int paymentId = dbHelper.insertPayment(paymentName, dueDateMillis, false);

                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                    cal.set(java.util.Calendar.MINUTE, 0);
                    cal.set(java.util.Calendar.SECOND, 0);
                    cal.set(java.util.Calendar.MILLISECOND, 0);
                    long startOfToday = cal.getTimeInMillis();

                    if (dueDateMillis > System.currentTimeMillis()) {
                        Log.d("PAYMENT SCHEDULER", "Scheduling payment:\nlocalId=" + paymentId + "\nserverId=-1\ndueDate=" + dueDateMillis + "\nsuccess=true");
                        AlarmUtils.schedulePaymentAlarm(this, paymentId, paymentName, dueDateMillis);
                    } else if (dueDateMillis >= startOfToday) {
                        Log.d("PAYMENT SCHEDULER", "Triggering immediate payment notification (due today):\nlocalId=" + paymentId + "\nserverId=-1\ndueDate=" + dueDateMillis);
                        AlarmUtils.showMonthlyPaymentNotification(this, paymentId, paymentName);
                    }

                    payments.clear();
                    payments.addAll(dbHelper.getAllPayments());
                    adapter.notifyDataSetChanged();

                    paymentInput.setText("");
                    Toast.makeText(this, "Payment reminder set for due date!", Toast.LENGTH_SHORT).show();

                    // Sync to backend
                    SyncManager.getInstance(MonthlyPaymentsActivity.this).uploadPayment(
                            paymentId,
                            paymentName,
                            dueDateMillis,
                            false,
                            null,
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
                                    Toast.makeText(MonthlyPaymentsActivity.this, "Sync error: " + error, Toast.LENGTH_SHORT).show();
                                }
                            }
                    );
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        // Prevent selecting past dates
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        datePickerDialog.show();
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
}
