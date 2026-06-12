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
        holder.paymentName.setText(payment.getName());

        String formattedDate = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(payment.getDueDate());
        holder.dueDateView.setText(context.getString(R.string.due_date_format, formattedDate));

        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(payment.isCompleted());

        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && pos < payments.size()) {
                MonthlyPayment currentPayment = payments.get(pos);
                currentPayment.setCompleted(isChecked);
                dbHelper.updatePaymentStatus(currentPayment.getId(), isChecked);

                // Sync status toggle to server
                SyncManager.getInstance(context).uploadPayment(
                        currentPayment.getId(),
                        currentPayment.getName(),
                        currentPayment.getDueDate(),
                        isChecked,
                        currentPayment.getServerId(),
                        new SyncManager.SyncCallback<Long>() {
                            @Override
                            public void onSuccess(Long result) {
                                Log.d("MonthlyPaymentAdapter", "Payment completion toggle synced to server");
                            }

                            @Override
                            public void onError(String error) {
                                Log.e("MonthlyPaymentAdapter", "Failed to sync payment toggle: " + error);
                            }
                        }
                );

                if (isChecked) {
                    cancelScheduledAlarm(currentPayment.getId(), currentPayment.getName());
                    // ✅ Tell service to remove only THIS payment's notification
                    removePaymentNotification(currentPayment.getId());
                    Toast.makeText(context, "Payment marked as completed", Toast.LENGTH_SHORT).show();
                } else {
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

                cancelScheduledAlarm(toDelete.getId(), toDelete.getName());
                // ✅ Tell service to remove only THIS payment's notification
                removePaymentNotification(toDelete.getId());

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

    /**
     * ✅ Sends ACTION_REMOVE to the service so it cancels only THIS payment's notification.
     * Other payment notifications are not affected.
     */
    private void removePaymentNotification(int paymentId) {
        Intent serviceIntent = new Intent(context, PaymentNotificationService.class);
        serviceIntent.setAction(PaymentNotificationService.ACTION_REMOVE);
        serviceIntent.putExtra(PaymentNotificationService.EXTRA_PAYMENT_ID, paymentId);

        // ✅ Use startService since we are just sending a command.
        context.startService(serviceIntent);
    }

    /**
     * ✅ FIX: Cancel the AlarmManager alarm using the same paymentId request code used when scheduling.
     */
    private void cancelScheduledAlarm(int paymentId, String paymentName) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, Paymentalarmreceiver.class);
        intent.putExtra("payment_id", paymentId);
        intent.putExtra("payment_name", paymentName);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                paymentId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.cancel(pendingIntent);
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
