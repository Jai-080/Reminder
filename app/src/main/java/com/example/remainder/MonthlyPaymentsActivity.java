package com.example.remainder;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
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
    private Button addButton, clearAllButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monthly_payments);

        dbHelper = new PaymentDatabaseHelper(this);
        paymentInput = findViewById(R.id.paymentInput);
        addButton = findViewById(R.id.addPaymentButton);
        clearAllButton = findViewById(R.id.clearAllBtn);
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
                cancelScheduledAlarm(payment.getId(), payment.getName());
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

                    // ✅ FIX: Schedule a future alarm at due date instead of showing notification instantly
                    schedulePaymentAlarm(paymentId, paymentName, dueDateMillis);

                    payments.clear();
                    payments.addAll(dbHelper.getAllPayments());
                    adapter.notifyDataSetChanged();

                    paymentInput.setText("");
                    Toast.makeText(this, "Payment reminder set for due date!", Toast.LENGTH_SHORT).show();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        // Prevent selecting past dates
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        datePickerDialog.show();
    }

    /**
     * Schedules an AlarmManager alarm that fires at the due date.
     * When it fires, it triggers PaymentAlarmReceiver which shows the notification.
     */
    private void schedulePaymentAlarm(int paymentId, String paymentName, long dueDateMillis) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(this, Paymentalarmreceiver.class);
        intent.putExtra("payment_id", paymentId);
        intent.putExtra("payment_name", paymentName);

        // Use paymentId as request code so each payment has its own unique PendingIntent
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                paymentId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Use setExactAndAllowWhileIdle for reliable delivery even in Doze mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueDateMillis, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, dueDateMillis, pendingIntent);
        }
    }

    /**
     * Cancels a previously scheduled alarm for a payment.
     */
    private void cancelScheduledAlarm(int paymentId, String paymentName) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(this, Paymentalarmreceiver.class);
        intent.putExtra("payment_id", paymentId);
        intent.putExtra("payment_name", paymentName);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                paymentId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.cancel(pendingIntent);
    }
}