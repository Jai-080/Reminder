package com.example.reminder;

import com.example.reminder.sync.SyncManager;

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

        holder.rescheduleButton.setVisibility(View.VISIBLE);
        holder.rescheduleButton.setOnClickListener(v -> {
            final java.util.Calendar calendar = java.util.Calendar.getInstance();
            calendar.setTimeInMillis(reminder.getTime());

            new android.app.DatePickerDialog(context, (view, year, month, dayOfMonth) -> {
                calendar.set(java.util.Calendar.YEAR, year);
                calendar.set(java.util.Calendar.MONTH, month);
                calendar.set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth);

                java.util.Calendar now = java.util.Calendar.getInstance();
                int hour = now.get(java.util.Calendar.HOUR_OF_DAY);
                int minute = now.get(java.util.Calendar.MINUTE);

                new CustomTimePickerDialog(context, R.style.TimePickerTheme, (timeView, hourOfDay, min) -> {
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(java.util.Calendar.MINUTE, min);
                    calendar.set(java.util.Calendar.SECOND, 0);
                    calendar.set(java.util.Calendar.MILLISECOND, 0);

                    long triggerTime = calendar.getTimeInMillis();
                    if (triggerTime <= System.currentTimeMillis()) {
                        android.widget.Toast.makeText(context, "Please choose a future time", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Reschedule alarm
                    AlarmUtils.cancelReminder(context, reminder.getId());

                    // Update local DB status and sync (reusing the snoozeReminder workflow)
                    dbHelper.snoozeReminder(reminder.getId(), triggerTime);

                    AlarmUtils.scheduleReminder(context, reminder.getId(), reminder.getText(), triggerTime);

                    SyncManager.getInstance(context).uploadReminder(
                            reminder.getId(),
                            reminder.getText(),
                            triggerTime,
                            false,
                            triggerTime,
                            reminder.getServerId(),
                            new SyncManager.SyncCallback<Long>() {
                                @Override
                                public void onSuccess(Long result) {
                                    android.util.Log.d("ReminderAdapter", "Reminder reschedule sync succeeded");
                                }

                                @Override
                                public void onError(String error) {
                                    android.util.Log.e("ReminderAdapter", "Reminder reschedule sync failed: " + error);
                                }
                            }
                    );

                    int currentPos = holder.getAdapterPosition();
                    if (currentPos != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                        reminders.remove(currentPos);
                        notifyItemRemoved(currentPos);
                        notifyItemRangeChanged(currentPos, reminders.size());
                    }

                    if (deleteListener != null) {
                        deleteListener.onReminderDeleted();
                    }

                }, hour, minute, true).show();
            }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), calendar.get(java.util.Calendar.DAY_OF_MONTH)).show();
        });

        holder.deleteButton.setOnClickListener(v -> {
            if (!isExpiredList) {
                AlarmUtils.cancelReminder(context, reminder.getId());
            }

            // Sync deletion to server
            SyncManager.getInstance(context).deleteReminder(reminder.getId(), reminder.getServerId(), new SyncManager.SyncCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    android.util.Log.d("ReminderAdapter", "Reminder deletion synced to server");
                }

                @Override
                public void onError(String error) {
                    android.util.Log.e("ReminderAdapter", "Failed to sync reminder deletion: " + error);
                }
            });

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
        ImageView rescheduleButton;
        ImageView deleteButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            reminderText = itemView.findViewById(R.id.reminderText);
            reminderTime = itemView.findViewById(R.id.reminderTime);
            expiredIcon = itemView.findViewById(R.id.expiredIcon);   // ✅ TextView
            rescheduleButton = itemView.findViewById(R.id.rescheduleButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
}
