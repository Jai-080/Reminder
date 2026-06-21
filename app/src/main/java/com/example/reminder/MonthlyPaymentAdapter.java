package com.example.reminder;

import com.example.reminder.sync.SyncManager;
import android.util.Log;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

public class MonthlyPaymentAdapter extends RecyclerView.Adapter<MonthlyPaymentAdapter.ViewHolder> {

    private final Context context;
    private final ArrayList<MonthlyPayment> payments;
    private final PaymentDatabaseHelper dbHelper;

    public MonthlyPaymentAdapter(Context context, ArrayList<MonthlyPayment> payments, PaymentDatabaseHelper dbHelper) {
        this.context = context;
        this.payments = payments;
        this.dbHelper = dbHelper;
        sortPayments();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_payment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MonthlyPayment payment = payments.get(position);
        
        String amountText = payment.getAmount() == null ? "Amount Unknown" : String.format(Locale.getDefault(), "₹%.2f", payment.getAmount());
        holder.paymentName.setText(payment.getName() + " (" + amountText + ")");

        String recurrenceStr = payment.getRecurrence() != null ? payment.getRecurrence().name() : "MONTHLY";
        String recurrenceDisplay = recurrenceStr.substring(0, 1).toUpperCase() + recurrenceStr.substring(1).toLowerCase();

        String formattedDate = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(payment.getDueDate());
        holder.dueDateView.setText(context.getString(R.string.due_date_format, formattedDate) + " (" + recurrenceDisplay + ")");

        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(payment.isCompleted());

        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && pos < payments.size()) {
                MonthlyPayment currentPayment = payments.get(pos);
                if (isChecked) {
                    // 1. Advance due date
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.setTimeInMillis(currentPayment.getDueDate());
                    if (currentPayment.getRecurrence() == RecurrenceType.QUARTERLY) {
                        cal.add(java.util.Calendar.MONTH, 3);
                    } else if (currentPayment.getRecurrence() == RecurrenceType.YEARLY) {
                        cal.add(java.util.Calendar.YEAR, 1);
                    } else {
                        cal.add(java.util.Calendar.MONTH, 1);
                    }
                    long newDueDate = cal.getTimeInMillis();

                    currentPayment.setDueDateMillis(newDueDate);
                    currentPayment.setCompleted(false);
                    currentPayment.setSyncStatus("PENDING");
                    
                    dbHelper.updatePayment(currentPayment);

                    // 2. Cancel current alarm & notification
                    AlarmUtils.cancelPaymentAlarm(context, currentPayment.getId(), currentPayment.getName());
                    AlarmUtils.cancelNotification(context, currentPayment.getId());

                    // 3. Schedule next upcoming alarm
                    if (newDueDate > System.currentTimeMillis()) {
                        Log.d("PAYMENT SCHEDULER", "Scheduling next cycle payment:\nlocalId=" + currentPayment.getId() + "\nserverId=" + (currentPayment.getServerId() != null ? currentPayment.getServerId() : -1) + "\ndueDate=" + newDueDate + "\nsuccess=true");
                        AlarmUtils.schedulePaymentAlarm(context, currentPayment.getId(), currentPayment.getName(), newDueDate);
                    }

                    // 4. Sync status to server
                    SyncManager.getInstance(context).uploadPayment(
                            currentPayment,
                            new SyncManager.SyncCallback<Long>() {
                                @Override
                                public void onSuccess(Long result) {
                                    Log.d("MonthlyPaymentAdapter", "Payment auto-renew synced to server");
                                }

                                @Override
                                public void onError(String error) {
                                    Log.e("MonthlyPaymentAdapter", "Failed to sync auto-renew: " + error);
                                }
                            }
                    );

                    Toast.makeText(context, "Payment completed, auto-renewed", Toast.LENGTH_SHORT).show();
                } else {
                    // Handled if somehow unchecked manually
                    currentPayment.setCompleted(false);
                    currentPayment.setSyncStatus("PENDING");
                    dbHelper.updatePayment(currentPayment);

                    AlarmUtils.cancelPaymentAlarm(context, currentPayment.getId(), currentPayment.getName());
                    AlarmUtils.cancelNotification(context, currentPayment.getId());

                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                    cal.set(java.util.Calendar.MINUTE, 0);
                    cal.set(java.util.Calendar.SECOND, 0);
                    cal.set(java.util.Calendar.MILLISECOND, 0);
                    long startOfToday = cal.getTimeInMillis();

                    if (currentPayment.getDueDate() > System.currentTimeMillis()) {
                        AlarmUtils.schedulePaymentAlarm(context, currentPayment.getId(), currentPayment.getName(), currentPayment.getDueDate());
                    } else if (currentPayment.getDueDate() >= startOfToday) {
                        AlarmUtils.showMonthlyPaymentNotification(context, currentPayment.getId(), currentPayment.getName());
                    }

                    SyncManager.getInstance(context).uploadPayment(
                            currentPayment,
                            new SyncManager.SyncCallback<Long>() {
                                @Override
                                public void onSuccess(Long result) {}
                                @Override
                                public void onError(String error) {}
                            }
                    );
                    Toast.makeText(context, "Payment marked as pending", Toast.LENGTH_SHORT).show();
                }

                sortPayments();
                notifyDataSetChanged();
            }
        });

        holder.deleteButton.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && pos < payments.size()) {
                MonthlyPayment toDelete = payments.get(pos);
 
                AlarmUtils.cancelPaymentAlarm(context, toDelete.getId(), toDelete.getName());
                AlarmUtils.cancelNotification(context, toDelete.getId());
 
                // Sync deletion to server
                SyncManager.getInstance(context).deletePayment(toDelete.getId(), toDelete.getServerId(), new SyncManager.SyncCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        Log.d("MonthlyPaymentAdapter", "Payment deletion synced to server");
                    }
 
                    @Override
                    public void onError(String error) {
                        Log.e("MonthlyPaymentAdapter", "Failed to sync payment deletion: " + error);
                    }
                });
 
                payments.remove(pos);
                notifyItemRemoved(pos);
                Toast.makeText(context, "Payment deleted", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return payments.size();
    }

    private void sortPayments() {
        payments.sort((p1, p2) -> Boolean.compare(p1.isCompleted(), p2.isCompleted()));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView paymentName, dueDateView;
        CheckBox checkBox;
        ImageButton deleteButton;

        public ViewHolder(View itemView) {
            super(itemView);
            paymentName = itemView.findViewById(R.id.paymentNameTextView);
            dueDateView = itemView.findViewById(R.id.dueDateTextView);
            checkBox = itemView.findViewById(R.id.paymentCheckBox);
            deleteButton = itemView.findViewById(R.id.deletePaymentBtn);
        }
    }
}
