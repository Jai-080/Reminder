package com.example.remainder;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public class MonthlyPaymentAdapter extends RecyclerView.Adapter<MonthlyPaymentAdapter.ViewHolder> {

    private final Context context;
    private final ArrayList<MonthlyPayment> payments;
    private final PaymentDatabaseHelper dbHelper;

    public MonthlyPaymentAdapter(Context context, ArrayList<MonthlyPayment> payments, PaymentDatabaseHelper dbHelper) {
        this.context = context;
        this.payments = payments;
        this.dbHelper = dbHelper;
        sortPayments(); // Initial sort
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_payment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        MonthlyPayment payment = payments.get(position);
        holder.paymentName.setText(payment.getName());

        String formattedDate = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(payment.getDueDate());
        holder.dueDateView.setText("Due: " + formattedDate);

        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(payment.isCompleted());

        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            payment.setCompleted(isChecked);
            dbHelper.updatePaymentStatus(payment.getId(), isChecked);

            sortPayments();  // Sort updated list (incomplete at top, completed at bottom)
            notifyDataSetChanged(); // Refresh UI
        });

        holder.deleteButton.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && pos < payments.size()) {
                dbHelper.deletePayment(payments.get(pos).getId());
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
        Collections.sort(payments, (p1, p2) -> Boolean.compare(p1.isCompleted(), p2.isCompleted()));
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
