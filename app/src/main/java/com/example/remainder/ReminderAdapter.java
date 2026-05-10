package com.example.remainder;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.ArrayList;

public class ReminderAdapter extends RecyclerView.Adapter<ReminderAdapter.ViewHolder> {

    private ArrayList<Reminder> reminders;
    private ReminderDatabaseHelper dbHelper;
    private Context context;
    private OnReminderDeletedListener deleteListener;
    private boolean isExpiredList;

    public interface OnReminderDeletedListener {
        void onReminderDeleted();
    }

    public ReminderAdapter(ArrayList<Reminder> reminders,
                           ReminderDatabaseHelper dbHelper,
                           Context context,
                           OnReminderDeletedListener deleteListener,
                           boolean isExpiredList) {
        this.reminders = reminders;
        this.dbHelper = dbHelper;
        this.context = context;
        this.deleteListener = deleteListener;
        this.isExpiredList = isExpiredList;
    }

    public void setReminders(ArrayList<Reminder> reminders) {
        this.reminders = reminders;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReminderAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.reminder_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReminderAdapter.ViewHolder holder, int position) {
        Reminder reminder = reminders.get(position);

        holder.reminderText.setText(reminder.getText());

        String formattedTime = DateFormat.getDateTimeInstance().format(reminder.getTime());
        holder.reminderTime.setText(formattedTime);

        // ✅ expiredIcon is now a TextView badge
        boolean isExpired = reminder.getTime() <= System.currentTimeMillis();
        holder.expiredIcon.setVisibility(isExpired ? View.VISIBLE : View.GONE);

        holder.deleteButton.setOnClickListener(v -> {
            if (!isExpiredList) {
                AlarmUtils.cancelReminder(context, reminder.getId());
            }

            dbHelper.deleteReminder(reminder.getId());
            reminders.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, reminders.size());

            if (deleteListener != null) {
                deleteListener.onReminderDeleted();
            }
        });
    }

    @Override
    public int getItemCount() {
        return reminders.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView reminderText, reminderTime;
        TextView expiredIcon;   // ✅ was ImageView, now TextView badge
        ImageView deleteButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            reminderText = itemView.findViewById(R.id.reminderText);
            reminderTime = itemView.findViewById(R.id.reminderTime);
            expiredIcon = itemView.findViewById(R.id.expiredIcon);   // ✅ TextView
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
}