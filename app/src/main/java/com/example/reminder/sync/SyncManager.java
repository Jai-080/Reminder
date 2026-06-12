package com.example.reminder.sync;

import android.content.Context;
import android.util.Log;

import com.example.reminder.PaymentDatabaseHelper;
import com.example.reminder.QuickNoteDatabaseHelper;
import com.example.reminder.ReminderDatabaseHelper;
import com.example.reminder.QuickNote;
import com.example.reminder.Reminder;
import com.example.reminder.MonthlyPayment;
import com.example.reminder.auth.TokenManager;
import com.example.reminder.network.NoteRequest;
import com.example.reminder.network.NoteResponse;
import com.example.reminder.network.PaymentRequest;
import com.example.reminder.network.PaymentResponse;
import com.example.reminder.network.ReminderRequest;
import com.example.reminder.network.ReminderResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SyncManager {
    private static final String TAG = "SyncManager";
    private static SyncManager instance;

    private final Context context;
    private final SyncRepository repository;
    private final TokenManager tokenManager;

    private final QuickNoteDatabaseHelper noteDb;
    private final ReminderDatabaseHelper reminderDb;
    private final PaymentDatabaseHelper paymentDb;

    public interface SyncCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    private SyncManager(Context context) {
        this.context = context.getApplicationContext();
        this.repository = new SyncRepository(context);
        this.tokenManager = TokenManager.getInstance(context);

        this.noteDb = new QuickNoteDatabaseHelper(context);
        this.reminderDb = new ReminderDatabaseHelper(context);
        this.paymentDb = new PaymentDatabaseHelper(context);
    }

    public static synchronized SyncManager getInstance(Context context) {
        if (instance == null) {
            instance = new SyncManager(context);
        }
        return instance;
    }

    /**
     * Executes the initial synchronization by downloading Notes, Reminders, and Payments,
     * and writing them into the local databases without scheduling alarms or deleting local data.
     */
    public void performInitialSync(SyncCallback<Void> callback) {
        Log.d(TAG, "Starting initial synchronization...");

        // 1. Sync Notes
        repository.getNotes(new Callback<List<NoteResponse>>() {
            @Override
            public void onResponse(Call<List<NoteResponse>> call, Response<List<NoteResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<NoteResponse> serverNotes = response.body();
                    for (NoteResponse note : serverNotes) {
                        noteDb.insertOrUpdateSyncedNote(
                                note.getId(),
                                note.getText(),
                                note.getIsCompleted(),
                                note.getPosition() != null ? note.getPosition() : 0,
                                System.currentTimeMillis()
                        );
                    }
                    Log.d(TAG, "Notes synced: " + serverNotes.size() + " items.");
                    
                    // Proceed to Sync Reminders
                    syncReminders(callback);
                } else {
                    callback.onError("Failed to download notes: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<NoteResponse>> call, Throwable t) {
                callback.onError("Notes sync network failure: " + t.getMessage());
            }
        });
    }

    private void syncReminders(SyncCallback<Void> callback) {
        repository.getReminders(new Callback<List<ReminderResponse>>() {
            @Override
            public void onResponse(Call<List<ReminderResponse>> call, Response<List<ReminderResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<ReminderResponse> serverReminders = response.body();
                    for (ReminderResponse reminder : serverReminders) {
                        reminderDb.insertOrUpdateSyncedReminder(
                                reminder.getId(),
                                reminder.getText(),
                                reminder.getReminderTime(),
                                reminder.getIsExpired() != null ? reminder.getIsExpired() : false,
                                reminder.getSnoozedTime() != null ? reminder.getSnoozedTime() : 0L,
                                System.currentTimeMillis()
                        );
                    }
                    Log.d(TAG, "Reminders synced: " + serverReminders.size() + " items.");

                    // Proceed to Sync Payments
                    syncPayments(callback);
                } else {
                    callback.onError("Failed to download reminders: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<ReminderResponse>> call, Throwable t) {
                callback.onError("Reminders sync network failure: " + t.getMessage());
            }
        });
    }

    private void syncPayments(SyncCallback<Void> callback) {
        repository.getPayments(new Callback<List<PaymentResponse>>() {
            @Override
            public void onResponse(Call<List<PaymentResponse>> call, Response<List<PaymentResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<PaymentResponse> serverPayments = response.body();
                    for (PaymentResponse payment : serverPayments) {
                        paymentDb.insertOrUpdateSyncedPayment(
                                payment.getId(),
                                payment.getName(),
                                payment.getDueDate(),
                                payment.getCompleted() != null ? payment.getCompleted() : false,
                                System.currentTimeMillis()
                        );
                    }
                    Log.d(TAG, "Payments synced: " + serverPayments.size() + " items.");

                    // Save sync success status and timestamp
                    tokenManager.setLastSyncTimestamp(System.currentTimeMillis());
                    callback.onSuccess(null);
                } else {
                    callback.onError("Failed to download payments: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<PaymentResponse>> call, Throwable t) {
                callback.onError("Payments sync network failure: " + t.getMessage());
            }
        });
    }

    // ==========================================
    // CRUD OPERATIONS SYNCHRONIZATION
    // ==========================================

    // --- Notes CRUD ---
    public void uploadNote(int localId, String text, boolean completed, int position, Long serverId, SyncCallback<Long> callback) {
        NoteRequest request = new NoteRequest(text, completed, position);

        if (serverId != null && serverId > 0) {
            // Update (PUT)
            repository.updateNote(serverId, request, new Callback<NoteResponse>() {
                @Override
                public void onResponse(Call<NoteResponse> call, Response<NoteResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        noteDb.updateSyncStatus(localId, serverId, "SYNCED");
                        callback.onSuccess(serverId);
                    } else {
                        noteDb.updateSyncStatus(localId, serverId, "FAILED");
                        callback.onError("Server update failed: HTTP " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<NoteResponse> call, Throwable t) {
                    noteDb.updateSyncStatus(localId, serverId, "FAILED");
                    callback.onError("Network failure: " + t.getMessage());
                }
            });
        } else {
            // Create (POST)
            repository.createNote(request, new Callback<NoteResponse>() {
                @Override
                public void onResponse(Call<NoteResponse> call, Response<NoteResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        long newServerId = response.body().getId();
                        noteDb.updateSyncStatus(localId, newServerId, "SYNCED");
                        callback.onSuccess(newServerId);
                    } else {
                        noteDb.updateSyncStatus(localId, null, "FAILED");
                        callback.onError("Server upload failed: HTTP " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<NoteResponse> call, Throwable t) {
                    noteDb.updateSyncStatus(localId, null, "FAILED");
                    callback.onError("Network failure: " + t.getMessage());
                }
            });
        }
    }

    public void deleteNote(int localId, Long serverId, SyncCallback<Void> callback) {
        noteDb.deleteNote(localId); // Wipe locally first

        if (serverId == null || serverId <= 0) {
            // Local-only note, no server call needed
            callback.onSuccess(null);
            return;
        }

        repository.deleteNote(serverId, new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful() || response.code() == 404) {
                    callback.onSuccess(null);
                } else {
                    callback.onError("Server delete failed: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                // Ignore sync failures on deletion as local record is already deleted
                callback.onSuccess(null);
            }
        });
    }

    // --- Reminders CRUD ---
    public void uploadReminder(int localId, String text, long time, boolean expired, long snoozedTime, Long serverId, SyncCallback<Long> callback) {
        ReminderRequest request = new ReminderRequest(text, time, expired, snoozedTime);

        if (serverId != null && serverId > 0) {
            // Update (PUT)
            repository.updateReminder(serverId, request, new Callback<ReminderResponse>() {
                @Override
                public void onResponse(Call<ReminderResponse> call, Response<ReminderResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        reminderDb.updateSyncStatus(localId, serverId, "SYNCED");
                        callback.onSuccess(serverId);
                    } else {
                        reminderDb.updateSyncStatus(localId, serverId, "FAILED");
                        callback.onError("Server update failed: HTTP " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<ReminderResponse> call, Throwable t) {
                    reminderDb.updateSyncStatus(localId, serverId, "FAILED");
                    callback.onError("Network failure: " + t.getMessage());
                }
            });
        } else {
            // Create (POST)
            repository.createReminder(request, new Callback<ReminderResponse>() {
                @Override
                public void onResponse(Call<ReminderResponse> call, Response<ReminderResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        long newServerId = response.body().getId();
                        reminderDb.updateSyncStatus(localId, newServerId, "SYNCED");
                        callback.onSuccess(newServerId);
                    } else {
                        reminderDb.updateSyncStatus(localId, null, "FAILED");
                        callback.onError("Server upload failed: HTTP " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<ReminderResponse> call, Throwable t) {
                    reminderDb.updateSyncStatus(localId, null, "FAILED");
                    callback.onError("Network failure: " + t.getMessage());
                }
            });
        }
    }

    public void deleteReminder(int localId, Long serverId, SyncCallback<Void> callback) {
        reminderDb.deleteReminder(localId); // Wipe locally first

        if (serverId == null || serverId <= 0) {
            callback.onSuccess(null);
            return;
        }

        repository.deleteReminder(serverId, new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful() || response.code() == 404) {
                    callback.onSuccess(null);
                } else {
                    callback.onError("Server delete failed: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onSuccess(null);
            }
        });
    }

    // --- Payments CRUD ---
    public void uploadPayment(int localId, String name, long dueDate, boolean completed, Long serverId, SyncCallback<Long> callback) {
        PaymentRequest request = new PaymentRequest(name, dueDate, completed);

        if (serverId != null && serverId > 0) {
            // Update (PUT)
            repository.updatePayment(serverId, request, new Callback<PaymentResponse>() {
                @Override
                public void onResponse(Call<PaymentResponse> call, Response<PaymentResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        paymentDb.updateSyncStatus(localId, serverId, "SYNCED");
                        callback.onSuccess(serverId);
                    } else {
                        paymentDb.updateSyncStatus(localId, serverId, "FAILED");
                        callback.onError("Server update failed: HTTP " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<PaymentResponse> call, Throwable t) {
                    paymentDb.updateSyncStatus(localId, serverId, "FAILED");
                    callback.onError("Network failure: " + t.getMessage());
                }
            });
        } else {
            // Create (POST)
            repository.createPayment(request, new Callback<PaymentResponse>() {
                @Override
                public void onResponse(Call<PaymentResponse> call, Response<PaymentResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        long newServerId = response.body().getId();
                        paymentDb.updateSyncStatus(localId, newServerId, "SYNCED");
                        callback.onSuccess(newServerId);
                    } else {
                        paymentDb.updateSyncStatus(localId, null, "FAILED");
                        callback.onError("Server upload failed: HTTP " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<PaymentResponse> call, Throwable t) {
                    paymentDb.updateSyncStatus(localId, null, "FAILED");
                    callback.onError("Network failure: " + t.getMessage());
                }
            });
        }
    }

    public void deletePayment(int localId, Long serverId, SyncCallback<Void> callback) {
        paymentDb.deletePayment(localId); // Wipe locally first

        if (serverId == null || serverId <= 0) {
            callback.onSuccess(null);
            return;
        }

        repository.deletePayment(serverId, new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful() || response.code() == 404) {
                    callback.onSuccess(null);
                } else {
                    callback.onError("Server delete failed: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onSuccess(null);
            }
        });
    }

    private long parseInstant(String instantStr) {
        if (instantStr == null || instantStr.isEmpty()) {
            return System.currentTimeMillis();
        }
        try {
            return java.time.Instant.parse(instantStr).toEpochMilli();
        } catch (Exception e) {
            try {
                return Long.parseLong(instantStr);
            } catch (Exception ex) {
                return System.currentTimeMillis();
            }
        }
    }

    public void performFullSync(SyncCallback<Void> callback) {
        if (!tokenManager.isLoggedIn()) {
            if (callback != null) callback.onError("Not authenticated.");
            return;
        }
        Log.d(TAG, "Starting bidirectional sync...");

        syncNotes(new SyncCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                syncRemindersBidirectional(new SyncCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        syncPaymentsBidirectional(new SyncCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                tokenManager.setLastSyncTimestamp(System.currentTimeMillis());
                                if (callback != null) callback.onSuccess(null);
                            }

                            @Override
                            public void onError(String error) {
                                if (callback != null) callback.onError("Payment sync failed: " + error);
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        if (callback != null) callback.onError("Reminder sync failed: " + error);
                    }
                });
            }

            @Override
            public void onError(String error) {
                if (callback != null) callback.onError("Notes sync failed: " + error);
            }
        });
    }

    private void syncNotes(SyncCallback<Void> callback) {
        repository.getNotes(new Callback<List<NoteResponse>>() {
            @Override
            public void onResponse(Call<List<NoteResponse>> call, Response<List<NoteResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<NoteResponse> serverNotes = response.body();
                    java.util.Set<Long> serverIds = new java.util.HashSet<>();
                    for (NoteResponse note : serverNotes) {
                        if (note.getId() != null) {
                            serverIds.add(note.getId());
                        }
                    }

                    // Prune local SYNCED records missing on server
                    List<QuickNote> localNotes = noteDb.getAllNotes();
                    for (QuickNote local : localNotes) {
                        if (local.getServerId() != null && "SYNCED".equals(local.getSyncStatus())) {
                            if (!serverIds.contains(local.getServerId())) {
                                noteDb.deleteNote(local.getId());
                            }
                        }
                    }

                    // Upsert server notes
                    for (NoteResponse serverNote : serverNotes) {
                        QuickNote localNote = null;
                        for (QuickNote n : localNotes) {
                            if (n.getServerId() != null && n.getServerId().equals(serverNote.getId())) {
                                localNote = n;
                                break;
                            }
                        }

                        long serverMillis = parseInstant(serverNote.getUpdatedAt());
                        if (localNote != null) {
                            long localMillis = noteDb.getNoteUpdatedAt(localNote.getId());
                            if (serverMillis >= localMillis) {
                                noteDb.insertOrUpdateSyncedNote(
                                        serverNote.getId(),
                                        serverNote.getText(),
                                        serverNote.getIsCompleted() != null && serverNote.getIsCompleted(),
                                        serverNote.getPosition() != null ? serverNote.getPosition() : 0,
                                        serverMillis
                                );
                            }
                        } else {
                            noteDb.insertOrUpdateSyncedNote(
                                    serverNote.getId(),
                                    serverNote.getText(),
                                    serverNote.getIsCompleted() != null && serverNote.getIsCompleted(),
                                    serverNote.getPosition() != null ? serverNote.getPosition() : 0,
                                    serverMillis
                            );
                        }
                    }

                    // Push pending changes to server
                    List<QuickNote> pendingNotes = new java.util.ArrayList<>();
                    for (QuickNote local : noteDb.getAllNotes()) {
                        if ("PENDING".equals(local.getSyncStatus())) {
                            pendingNotes.add(local);
                        }
                    }
                    uploadPendingNotes(pendingNotes, 0, () -> callback.onSuccess(null));
                } else {
                    callback.onError("HTTP error " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<NoteResponse>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    private void uploadPendingNotes(List<QuickNote> pending, int index, Runnable onFinished) {
        if (index >= pending.size()) {
            onFinished.run();
            return;
        }
        QuickNote note = pending.get(index);
        uploadNote(note.getId(), note.getText(), note.isCompleted(), note.getPosition(), note.getServerId(), new SyncCallback<Long>() {
            @Override
            public void onSuccess(Long result) {
                uploadPendingNotes(pending, index + 1, onFinished);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to upload pending note: " + error);
                uploadPendingNotes(pending, index + 1, onFinished);
            }
        });
    }

    private void syncRemindersBidirectional(SyncCallback<Void> callback) {
        repository.getReminders(new Callback<List<ReminderResponse>>() {
            @Override
            public void onResponse(Call<List<ReminderResponse>> call, Response<List<ReminderResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<ReminderResponse> serverReminders = response.body();
                    java.util.Set<Long> serverIds = new java.util.HashSet<>();
                    for (ReminderResponse reminder : serverReminders) {
                        if (reminder.getId() != null) {
                            serverIds.add(reminder.getId());
                        }
                    }

                    // Prune local SYNCED records missing on server
                    List<Reminder> localReminders = reminderDb.getAllReminders();
                    for (Reminder local : localReminders) {
                        if (local.getServerId() != null && "SYNCED".equals(local.getSyncStatus())) {
                            if (!serverIds.contains(local.getServerId())) {
                                reminderDb.deleteReminder(local.getId());
                            }
                        }
                    }

                    // Upsert server reminders
                    for (ReminderResponse serverReminder : serverReminders) {
                        Reminder localReminder = null;
                        for (Reminder r : localReminders) {
                            if (r.getServerId() != null && r.getServerId().equals(serverReminder.getId())) {
                                localReminder = r;
                                break;
                            }
                        }

                        long serverMillis = parseInstant(serverReminder.getUpdatedAt());
                        boolean isExpired = serverReminder.getIsExpired() != null && serverReminder.getIsExpired();
                        long snoozedTime = serverReminder.getSnoozedTime() != null ? serverReminder.getSnoozedTime() : 0L;

                        if (localReminder != null) {
                            long localMillis = reminderDb.getReminderUpdatedAt(localReminder.getId());
                            if (serverMillis >= localMillis) {
                                reminderDb.insertOrUpdateSyncedReminder(
                                        serverReminder.getId(),
                                        serverReminder.getText(),
                                        serverReminder.getReminderTime(),
                                        isExpired,
                                        snoozedTime,
                                        serverMillis
                                );
                            }
                        } else {
                            reminderDb.insertOrUpdateSyncedReminder(
                                    serverReminder.getId(),
                                    serverReminder.getText(),
                                    serverReminder.getReminderTime(),
                                    isExpired,
                                    snoozedTime,
                                    serverMillis
                            );
                        }
                    }

                    // Push pending changes to server
                    List<Reminder> pendingReminders = new java.util.ArrayList<>();
                    for (Reminder local : reminderDb.getAllReminders()) {
                        if ("PENDING".equals(local.getSyncStatus())) {
                            pendingReminders.add(local);
                        }
                    }
                    uploadPendingReminders(pendingReminders, 0, () -> callback.onSuccess(null));
                } else {
                    callback.onError("HTTP error " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<ReminderResponse>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    private void uploadPendingReminders(List<Reminder> pending, int index, Runnable onFinished) {
        if (index >= pending.size()) {
            onFinished.run();
            return;
        }
        Reminder reminder = pending.get(index);
        uploadReminder(reminder.getId(), reminder.getText(), reminder.getTime(), reminder.isExpired(), reminder.getSnoozedUntil(), reminder.getServerId(), new SyncCallback<Long>() {
            @Override
            public void onSuccess(Long result) {
                uploadPendingReminders(pending, index + 1, onFinished);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to upload pending reminder: " + error);
                uploadPendingReminders(pending, index + 1, onFinished);
            }
        });
    }

    private void syncPaymentsBidirectional(SyncCallback<Void> callback) {
        repository.getPayments(new Callback<List<PaymentResponse>>() {
            @Override
            public void onResponse(Call<List<PaymentResponse>> call, Response<List<PaymentResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<PaymentResponse> serverPayments = response.body();
                    java.util.Set<Long> serverIds = new java.util.HashSet<>();
                    for (PaymentResponse payment : serverPayments) {
                        if (payment.getId() != null) {
                            serverIds.add(payment.getId());
                        }
                    }

                    // Prune local SYNCED records missing on server
                    List<MonthlyPayment> localPayments = paymentDb.getAllPayments();
                    for (MonthlyPayment local : localPayments) {
                        if (local.getServerId() != null && "SYNCED".equals(local.getSyncStatus())) {
                            if (!serverIds.contains(local.getServerId())) {
                                paymentDb.deletePayment(local.getId());
                            }
                        }
                    }

                    // Upsert server payments
                    for (PaymentResponse serverPayment : serverPayments) {
                        MonthlyPayment localPayment = null;
                        for (MonthlyPayment p : localPayments) {
                            if (p.getServerId() != null && p.getServerId().equals(serverPayment.getId())) {
                                localPayment = p;
                                break;
                            }
                        }

                        long serverMillis = parseInstant(serverPayment.getUpdatedAt());
                        boolean completed = serverPayment.getCompleted() != null && serverPayment.getCompleted();

                        if (localPayment != null) {
                            long localMillis = paymentDb.getPaymentUpdatedAt(localPayment.getId());
                            if (serverMillis >= localMillis) {
                                paymentDb.insertOrUpdateSyncedPayment(
                                        serverPayment.getId(),
                                        serverPayment.getName(),
                                        serverPayment.getDueDate(),
                                        completed,
                                        serverMillis
                                );
                            }
                        } else {
                            paymentDb.insertOrUpdateSyncedPayment(
                                    serverPayment.getId(),
                                    serverPayment.getName(),
                                    serverPayment.getDueDate(),
                                    completed,
                                    serverMillis
                            );
                        }
                    }

                    // Push pending changes to server
                    List<MonthlyPayment> pendingPayments = new java.util.ArrayList<>();
                    for (MonthlyPayment local : paymentDb.getAllPayments()) {
                        if ("PENDING".equals(local.getSyncStatus())) {
                            pendingPayments.add(local);
                        }
                    }
                    uploadPendingPayments(pendingPayments, 0, () -> callback.onSuccess(null));
                } else {
                    callback.onError("HTTP error " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<PaymentResponse>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    private void uploadPendingPayments(List<MonthlyPayment> pending, int index, Runnable onFinished) {
        if (index >= pending.size()) {
            onFinished.run();
            return;
        }
        MonthlyPayment payment = pending.get(index);
        uploadPayment(payment.getId(), payment.getName(), payment.getDueDate(), payment.isCompleted(), payment.getServerId(), new SyncCallback<Long>() {
            @Override
            public void onSuccess(Long result) {
                uploadPendingPayments(pending, index + 1, onFinished);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to upload pending payment: " + error);
                uploadPendingPayments(pending, index + 1, onFinished);
            }
        });
    }
}
