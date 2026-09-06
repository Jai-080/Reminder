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

public class MonthlyPaymentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ITEM = 1;

    private final Context context;
    private final ArrayList<MonthlyPayment> payments;
    private final PaymentDatabaseHelper dbHelper;
    private final ArrayList<Object> displayItems = new ArrayList<>();

    public MonthlyPaymentAdapter(Context context, ArrayList<MonthlyPayment> payments, PaymentDatabaseHelper dbHelper) {
        this.context = context;
        this.payments = payments;
        this.dbHelper = dbHelper;
        rebuildDisplayItems();
    }

    private void rebuildDisplayItems() {
        displayItems.clear();
        ArrayList<MonthlyPayment> upcomingList = new ArrayList<>();
        ArrayList<MonthlyPayment> recentlyPaidList = new ArrayList<>();
        ArrayList<MonthlyPayment> dueLaterList = new ArrayList<>();

        java.util.Calendar nowCal = java.util.Calendar.getInstance();
        int currentMonth = nowCal.get(java.util.Calendar.MONTH) + 1;
        int currentYear = nowCal.get(java.util.Calendar.YEAR);

        for (MonthlyPayment p : payments) {
            if (p.isRecentlyPaid(currentMonth, currentYear)) {
                recentlyPaidList.add(p);
            } else if (p.isUpcoming(currentMonth, currentYear)) {
                upcomingList.add(p);
            } else if (p.isDueLater(currentMonth, currentYear)) {
                dueLaterList.add(p);
            }
        }

        // Sort upcoming list by dueDate ascending
        upcomingList.sort((p1, p2) -> Long.compare(p1.getDueDate(), p2.getDueDate()));

        // Sort recently paid list by lastPaidAt descending
        recentlyPaidList.sort((p1, p2) -> {
            Long lpa1 = p1.getLastPaidAt();
            Long lpa2 = p2.getLastPaidAt();
            if (lpa1 == null) return 1;
            if (lpa2 == null) return -1;
            return Long.compare(lpa2, lpa1);
        });

        // Sort due later list by dueDate ascending
        dueLaterList.sort((p1, p2) -> Long.compare(p1.getDueDate(), p2.getDueDate()));

        if (!upcomingList.isEmpty()) {
            displayItems.add("Due This Month");
            displayItems.addAll(upcomingList);
        }
        if (!recentlyPaidList.isEmpty()) {
            displayItems.add("Recently Paid");
            displayItems.addAll(recentlyPaidList);
        }
        if (!dueLaterList.isEmpty()) {
            displayItems.add("Due Later");
            displayItems.addAll(dueLaterList);
        }
    }

    @Override
    public int getItemViewType(int position) {
        rebuildDisplayItems();
        if (displayItems.get(position) instanceof String) {
            return VIEW_TYPE_HEADER;
        }
        return VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_HEADER) {
            TextView tv = new TextView(context);
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 32, 0, 16);
            tv.setLayoutParams(lp);
            tv.setTextSize(14f);
            tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            tv.setLetterSpacing(0.08f);
            try {
                tv.setTextColor(context.getColor(R.color.colorTextMuted));
            } catch (Exception e) {
                tv.setTextColor(0xFF888888);
            }
            return new HeaderViewHolder(tv);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_payment, parent, false);
            return new ViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder rawHolder, int position) {
        if (rawHolder instanceof HeaderViewHolder) {
            HeaderViewHolder holder = (HeaderViewHolder) rawHolder;
            String headerText = (String) displayItems.get(position);
            holder.textView.setText(headerText.toUpperCase());
            return;
        }

        ViewHolder holder = (ViewHolder) rawHolder;
        MonthlyPayment payment = (MonthlyPayment) displayItems.get(position);

        String amountText = payment.getAmount() == null ? "Amount Unknown" : String.format(Locale.getDefault(), "₹%.2f", payment.getAmount());
        holder.paymentName.setText(payment.getName() + " (" + amountText + ")");

        String recurrenceStr = payment.getRecurrence() != null ? payment.getRecurrence().name() : "MONTHLY";
        String recurrenceDisplay = recurrenceStr.replace("_", "-");
        recurrenceDisplay = recurrenceDisplay.substring(0, 1).toUpperCase() + recurrenceDisplay.substring(1).toLowerCase();

        String formattedDate = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(payment.getDueDate());

        holder.checkBox.setOnCheckedChangeListener(null);

        java.util.Calendar nowCal = java.util.Calendar.getInstance();
        int currentMonth = nowCal.get(java.util.Calendar.MONTH) + 1;
        int currentYear = nowCal.get(java.util.Calendar.YEAR);

        boolean isRecentlyPaid = payment.isRecentlyPaid(currentMonth, currentYear);
        boolean isUpcoming = payment.isUpcoming(currentMonth, currentYear);
        boolean isDueLater = payment.isDueLater(currentMonth, currentYear);

        if (isRecentlyPaid) {
            holder.checkBox.setVisibility(View.VISIBLE);
            holder.checkBox.setChecked(true);
            holder.checkBox.setEnabled(false);
            if (payment.getRecurrence() == RecurrenceType.ONE_TIME) {
                holder.dueDateView.setText("✓ Paid  |  Due: " + formattedDate + " (" + recurrenceDisplay + ")");
            } else {
                holder.dueDateView.setText("✓ Paid  |  Next Due: " + formattedDate + " (" + recurrenceDisplay + ")");
            }
            holder.dueDateView.setTextColor(0xFF888888); // gray secondary text
            holder.paymentName.setTextColor(0xFF888888);
        } else if (isDueLater) {
            holder.checkBox.setVisibility(View.GONE);
            holder.checkBox.setChecked(false);
            holder.checkBox.setEnabled(false);
            holder.dueDateView.setText("Due Date: " + formattedDate + " (" + recurrenceDisplay + ")");
            try {
                holder.dueDateView.setTextColor(context.getColor(R.color.colorTextSecondary));
            } catch (Exception e) {
                holder.dueDateView.setTextColor(0xFF666666);
            }
            try {
                holder.paymentName.setTextColor(context.getColor(R.color.colorTextPrimary));
            } catch (Exception e) {
                holder.paymentName.setTextColor(0xFF000000);
            }
        } else { // isUpcoming
            holder.checkBox.setVisibility(View.VISIBLE);
            holder.checkBox.setChecked(false);
            holder.checkBox.setEnabled(true);
            
            long now = System.currentTimeMillis();
            if (payment.getDueDate() < now) {
                holder.dueDateView.setText("Due Date: " + formattedDate + " (" + recurrenceDisplay + ") [Overdue]");
                holder.dueDateView.setTextColor(0xFFFF3333); // red text for overdue
            } else {
                holder.dueDateView.setText("Due Date: " + formattedDate + " (" + recurrenceDisplay + ")");
                try {
                    holder.dueDateView.setTextColor(context.getColor(R.color.colorTextSecondary));
                } catch (Exception e) {
                    holder.dueDateView.setTextColor(0xFF666666);
                }
            }
            try {
                holder.paymentName.setTextColor(context.getColor(R.color.colorTextPrimary));
            } catch (Exception e) {
                holder.paymentName.setTextColor(0xFF000000);
            }
        }

        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && pos < displayItems.size()) {
                MonthlyPayment currentPayment = (MonthlyPayment) displayItems.get(pos);
                if (isChecked) {
                    if (currentPayment.getRecurrence() == RecurrenceType.ONE_TIME) {
                        currentPayment.setLastPaidAt(System.currentTimeMillis());
                        currentPayment.setCompleted(false);
                        currentPayment.setSyncStatus("PENDING");
                        
                        dbHelper.updatePayment(currentPayment);

                        // Cancel current alarm & notification
                        AlarmUtils.cancelPaymentAlarm(context, currentPayment.getId(), currentPayment.getName());
                        AlarmUtils.cancelNotification(context, currentPayment.getId());
                    } else {
                        // Advance due date based on recurrence frequency
                        java.util.Calendar cal = java.util.Calendar.getInstance();
                        cal.setTimeInMillis(currentPayment.getDueDate());
                        if (currentPayment.getRecurrence() == RecurrenceType.BI_MONTHLY) {
                            cal.add(java.util.Calendar.MONTH, 2);
                        } else if (currentPayment.getRecurrence() == RecurrenceType.QUARTERLY) {
                            cal.add(java.util.Calendar.MONTH, 3);
                        } else if (currentPayment.getRecurrence() == RecurrenceType.HALF_YEARLY) {
                            cal.add(java.util.Calendar.MONTH, 6);
                        } else if (currentPayment.getRecurrence() == RecurrenceType.YEARLY) {
                            cal.add(java.util.Calendar.YEAR, 1);
                        } else {
                            cal.add(java.util.Calendar.MONTH, 1);
                        }
                        long newDueDate = cal.getTimeInMillis();

                        currentPayment.setDueDateMillis(newDueDate);
                        currentPayment.setLastPaidAt(System.currentTimeMillis());
                        currentPayment.setCompleted(false);
                        currentPayment.setSyncStatus("PENDING");
                        
                        dbHelper.updatePayment(currentPayment);

                        // Cancel current alarm & notification
                        AlarmUtils.cancelPaymentAlarm(context, currentPayment.getId(), currentPayment.getName());
                        AlarmUtils.cancelNotification(context, currentPayment.getId());

                        // Schedule next upcoming alarm
                        if (newDueDate > System.currentTimeMillis()) {
                            Log.d("PAYMENT SCHEDULER", "Scheduling next cycle payment:\nlocalId=" + currentPayment.getId() + "\nserverId=" + (currentPayment.getServerId() != null ? currentPayment.getServerId() : -1) + "\ndueDate=" + newDueDate + "\nsuccess=true");
                            AlarmUtils.schedulePaymentAlarm(context, currentPayment.getId(), currentPayment.getName(), newDueDate);
                        }
                    }

                    // Sync status to server
                    SyncManager.getInstance(context).uploadPayment(
                            currentPayment,
                            new SyncManager.SyncCallback<Long>() {
                                @Override
                                public void onSuccess(Long result) {
                                    Log.d("MonthlyPaymentAdapter", "Payment completion synced to server");
                                }

                                @Override
                                public void onError(String error) {
                                    Log.e("MonthlyPaymentAdapter", "Failed to sync payment completion: " + error);
                                }
                            }
                    );

                    Toast.makeText(context, "Payment completed", Toast.LENGTH_SHORT).show();
                }

                notifyDataSetChanged();
                if (context instanceof MonthlyPaymentsActivity) {
                    View txtNoPayments = ((MonthlyPaymentsActivity) context).findViewById(R.id.txtNoPayments);
                    if (txtNoPayments != null) {
                        txtNoPayments.setVisibility(payments.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                }
            }
        });

        holder.deleteButton.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && pos < displayItems.size()) {
                MonthlyPayment toDelete = (MonthlyPayment) displayItems.get(pos);
 
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
 
                payments.remove(toDelete);
                notifyDataSetChanged();
                if (context instanceof MonthlyPaymentsActivity) {
                    View txtNoPayments = ((MonthlyPaymentsActivity) context).findViewById(R.id.txtNoPayments);
                    if (txtNoPayments != null) {
                        txtNoPayments.setVisibility(payments.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                }
                Toast.makeText(context, "Payment deleted", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        rebuildDisplayItems();
        return displayItems.size();
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

    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        public HeaderViewHolder(TextView itemView) {
            super(itemView);
            this.textView = itemView;
        }
    }
}
