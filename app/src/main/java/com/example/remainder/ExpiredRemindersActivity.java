package com.example.remainder;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expired_reminders);

        recyclerView = findViewById(R.id.recyclerViewExpired);
        dbHelper = new ReminderDatabaseHelper(this);

        ArrayList<Reminder> expiredReminders = dbHelper.getExpiredReminders();
        adapter = new ReminderAdapter(expiredReminders, dbHelper, this, () -> {
            if (adapter.getItemCount() == 0) {
                Toast.makeText(this, "No expired reminders left", Toast.LENGTH_SHORT).show();
                finish(); // Optionally close the activity when all items are deleted
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }
}
