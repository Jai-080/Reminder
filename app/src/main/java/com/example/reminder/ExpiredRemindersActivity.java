package com.example.reminder;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class ExpiredRemindersActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ReminderAdapter adapter;
    private ReminderDatabaseHelper dbHelper;
    private android.widget.TextView txtNoExpiredReminders;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expired_reminders);

        android.view.View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        recyclerView = findViewById(R.id.recyclerViewExpired);
        txtNoExpiredReminders = findViewById(R.id.txtNoExpiredReminders);
        dbHelper = new ReminderDatabaseHelper(this);

        ArrayList<Reminder> expiredReminders = dbHelper.getExpiredReminders();
        if (txtNoExpiredReminders != null) {
            txtNoExpiredReminders.setVisibility(expiredReminders.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
        }

        adapter = new ReminderAdapter(expiredReminders, dbHelper, this, () -> {
            if (txtNoExpiredReminders != null) {
                txtNoExpiredReminders.setVisibility(adapter.getItemCount() == 0 ? android.view.View.VISIBLE : android.view.View.GONE);
            }
            if (adapter.getItemCount() == 0) {
                Toast.makeText(this, "No expired reminders left", Toast.LENGTH_SHORT).show();
                finish(); // Optionally close the activity when all items are deleted
            }
        }, false); // <-- Added this parameter to indicate expired list

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }
}
