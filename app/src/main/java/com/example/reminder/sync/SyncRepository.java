package com.example.reminder.sync;

import android.content.Context;

import com.example.reminder.network.ApiClient;
import com.example.reminder.network.NoteApi;
import com.example.reminder.network.NoteRequest;
import com.example.reminder.network.NoteResponse;
import com.example.reminder.network.PaymentApi;
import com.example.reminder.network.PaymentRequest;
import com.example.reminder.network.PaymentResponse;
import com.example.reminder.network.ReminderApi;
import com.example.reminder.network.ReminderRequest;
import com.example.reminder.network.ReminderResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;

public class SyncRepository {
    private final Context context;

    public SyncRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    // --- Notes ---
    public void getNotes(Callback<List<NoteResponse>> callback) {
        NoteApi api = ApiClient.getNoteApi(context);
        api.getNotes().enqueue(callback);
    }

    public void createNote(NoteRequest request, Callback<NoteResponse> callback) {
        NoteApi api = ApiClient.getNoteApi(context);
        api.createNote(request).enqueue(callback);
    }

    public void updateNote(Long serverId, NoteRequest request, Callback<NoteResponse> callback) {
        NoteApi api = ApiClient.getNoteApi(context);
        api.updateNote(serverId, request).enqueue(callback);
    }

    public void deleteNote(Long serverId, Callback<Void> callback) {
        NoteApi api = ApiClient.getNoteApi(context);
        api.deleteNote(serverId).enqueue(callback);
    }

    // --- Reminders ---
    public void getReminders(Callback<List<ReminderResponse>> callback) {
        ReminderApi api = ApiClient.getReminderApi(context);
        api.getReminders().enqueue(callback);
    }

    public void createReminder(ReminderRequest request, Callback<ReminderResponse> callback) {
        ReminderApi api = ApiClient.getReminderApi(context);
        api.createReminder(request).enqueue(callback);
    }

    public void updateReminder(Long serverId, ReminderRequest request, Callback<ReminderResponse> callback) {
        ReminderApi api = ApiClient.getReminderApi(context);
        api.updateReminder(serverId, request).enqueue(callback);
    }

    public void deleteReminder(Long serverId, Callback<Void> callback) {
        ReminderApi api = ApiClient.getReminderApi(context);
        api.deleteReminder(serverId).enqueue(callback);
    }

    // --- Payments ---
    public void getPayments(Callback<List<PaymentResponse>> callback) {
        PaymentApi api = ApiClient.getPaymentApi(context);
        api.getPayments().enqueue(callback);
    }

    public void createPayment(PaymentRequest request, Callback<PaymentResponse> callback) {
        PaymentApi api = ApiClient.getPaymentApi(context);
        api.createPayment(request).enqueue(callback);
    }

    public void updatePayment(Long serverId, PaymentRequest request, Callback<PaymentResponse> callback) {
        PaymentApi api = ApiClient.getPaymentApi(context);
        api.updatePayment(serverId, request).enqueue(callback);
    }

    public void deletePayment(Long serverId, Callback<Void> callback) {
        PaymentApi api = ApiClient.getPaymentApi(context);
        api.deletePayment(serverId).enqueue(callback);
    }
}
