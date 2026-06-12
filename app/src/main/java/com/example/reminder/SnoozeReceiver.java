package com.example.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SnoozeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int reminderId = intent.getIntExtra("reminder_id", -1);
        String reminderText = intent.getStringExtra("reminder_text");

        Intent snoozeActivityIntent = new Intent(context, SnoozeOptionsActivity.class);
        snoozeActivityIntent.putExtra("reminder_id", reminderId);
        snoozeActivityIntent.putExtra("reminder_text", reminderText);
        snoozeActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // Required to start activity from broadcast
        context.startActivity(snoozeActivityIntent);
    }
}
