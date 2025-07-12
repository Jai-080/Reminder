package com.example.remainder;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.DatePicker;
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
        clearAllButton = findViewById(R.id.clearAllBtn); // make sure this ID exists in XML
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
                    calendar.set(year, month, dayOfMonth);
                    long dueDateMillis = calendar.getTimeInMillis();
                    dbHelper.insertPayment(paymentName, dueDateMillis, false);

                    // Update the list and notify the adapter
                    payments.clear();
                    payments.addAll(dbHelper.getAllPayments());
                    adapter.notifyDataSetChanged();

                    AlarmUtils.schedulePaymentReminder(this, paymentName, dueDateMillis);
                    paymentInput.setText("");
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }
}
