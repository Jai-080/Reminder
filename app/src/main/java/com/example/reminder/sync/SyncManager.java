package com.example.reminder.sync;

import android.content.Context;
import android.util.Log;

import com.example.reminder.PaymentDatabaseHelper;
import com.example.reminder.QuickNoteDatabaseHelper;
import com.example.reminder.ReminderDatabaseHelper;
import com.example.reminder.QuickNote;
import com.example.reminder.Reminder;
import com.example.reminder.MonthlyPayment;
import com.example.reminder.AlarmUtils;
import com.example.reminder.ReminderApplication;
import com.example.reminder.auth.TokenManager;
import com.example.reminder.network.NoteRequest;
import com.example.reminder.network.NoteResponse;
import com.example.reminder.network.PaymentRequest;
import com.example.reminder.network.PaymentResponse;
import com.example.reminder.network.ReminderRequest;
import com.example.reminder.network.ReminderResponse;

import java.util.List;
import java.util.ArrayList;
import com.example.reminder.RecurrenceType;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SyncManager {
    private static final String TAG = "SyncManager";
    private static SyncManager instance;
    public static final String ACTION_SYNC_COMPLETED = "com.example.reminder.SYNC_COMPLETED";

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
                        long localId = reminderDb.insertOrUpdateSyncedReminder(
                                reminder.getId(),
                                reminder.getText(),
                                reminder.getReminderTime(),
                                reminder.getIsExpired() != null ? reminder.getIsExpired() : false,
                                reminder.getSnoozedTime() != null ? reminder.getSnoozedTime() : 0L,
                                System.currentTimeMillis()
                        );
                        // Schedule alarm if not expired and in the future
                        boolean isExpired = reminder.getIsExpired() != null ? reminder.getIsExpired() : false;
                        if (!isExpired && reminder.getReminderTime() > System.currentTimeMillis()) {
                            Log.d("REMINDER SCHEDULER", "Scheduling reminder:\nlocalId=" + localId + "\nserverId=" + reminder.getId() + "\ntime=" + reminder.getReminderTime() + "\nsuccess=true");
                            com.example.reminder.AlarmUtils.scheduleReminder(context, (int) localId, reminder.getText(), reminder.getReminderTime());
                        }
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
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                    cal.set(java.util.Calendar.MINUTE, 0);
                    cal.set(java.util.Calendar.SECOND, 0);
                    cal.set(java.util.Calendar.MILLISECOND, 0);
                    long startOfToday = cal.getTimeInMillis();

                    for (PaymentResponse payment : serverPayments) {
                        Double amt = payment.getAmount();
                        String recStr = payment.getRecurrence();
                        RecurrenceType rec = RecurrenceType.MONTHLY;
                        if (recStr != null) {
                            try {
                                rec = RecurrenceType.valueOf(recStr.toUpperCase());
                            } catch (IllegalArgumentException ignored) {}
                        }
                        String offsets = payment.getNotificationOffsets() != null ? payment.getNotificationOffsets() : "0";

                        long localId = paymentDb.insertOrUpdateSyncedPayment(
                                payment.getId(),
                                payment.getName(),
                                payment.getDueDate(),
                                payment.getCompleted() != null ? payment.getCompleted() : false,
                                System.currentTimeMillis(),
                                amt,
                                rec,
                                offsets
                        );
                        boolean completed = payment.getCompleted() != null ? payment.getCompleted() : false;
                        if (!completed) {
                            if (payment.getDueDate() > System.currentTimeMillis()) {
                                Log.d("PAYMENT SCHEDULER", "Scheduling payment:\nlocalId=" + localId + "\nserverId=" + payment.getId() + "\ndueDate=" + payment.getDueDate() + "\nsuccess=true");
                                AlarmUtils.schedulePaymentAlarm(context, (int) localId, payment.getName(), payment.getDueDate());
                            } else if (payment.getDueDate() >= startOfToday) {
                                Log.d("PAYMENT SCHEDULER", "Triggering immediate payment notification (due today):\nlocalId=" + localId + "\nserverId=" + payment.getId() + "\ndueDate=" + payment.getDueDate());
                                AlarmUtils.showMonthlyPaymentNotification(context, (int) localId, payment.getName());
                            }
                        }
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
        ReminderApplication.enqueueSyncWorker(context);
        long localUpdatedAt = noteDb.getNoteUpdatedAt(localId);
        NoteRequest request = new NoteRequest(text, completed, position, localUpdatedAt);

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
        ReminderApplication.enqueueSyncWorker(context);
        if (serverId == null || serverId <= 0) {
            // Local-only note, no server call needed, hard delete immediately
            noteDb.deleteNote(localId);
            callback.onSuccess(null);
            return;
        }

        // Soft-delete locally first so it persists even if we are offline
        noteDb.softDeleteNote(localId);

        repository.deleteNote(serverId, new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful() || response.code() == 404) {
                    noteDb.updateSyncStatus(localId, serverId, "DELETE_SYNCED");
                    noteDb.deleteNote(localId); // Hard-delete locally on success or 404
                    callback.onSuccess(null);
                } else {
                    callback.onError("Server delete failed: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onError("Network failure on delete: " + t.getMessage());
            }
        });
    }

    // --- Reminders CRUD ---
    public void uploadReminder(int localId, String text, long time, boolean expired, long snoozedTime, Long serverId, SyncCallback<Long> callback) {
        ReminderApplication.enqueueSyncWorker(context);
        long localUpdatedAt = reminderDb.getReminderUpdatedAt(localId);
        ReminderRequest request = new ReminderRequest(text, time, expired, snoozedTime, localUpdatedAt);

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
        ReminderApplication.enqueueSyncWorker(context);
        if (serverId == null || serverId <= 0) {
            // Local-only reminder, no server call needed, hard delete immediately
            reminderDb.deleteReminder(localId);
            callback.onSuccess(null);
            return;
        }

        // Soft-delete locally first so it persists even if we are offline
        reminderDb.softDeleteReminder(localId);

        repository.deleteReminder(serverId, new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful() || response.code() == 404) {
                    reminderDb.updateSyncStatus(localId, serverId, "DELETE_SYNCED");
                    reminderDb.deleteReminder(localId); // Hard-delete locally on success or 404
                    callback.onSuccess(null);
                } else {
                    callback.onError("Server delete failed: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onError("Network failure on delete: " + t.getMessage());
            }
        });
    }

    // --- Payments CRUD ---
    public void uploadPayment(int localId, String name, long dueDate, boolean completed, Long serverId, SyncCallback<Long> callback) {
        ArrayList<MonthlyPayment> all = paymentDb.getAllPayments();
        MonthlyPayment target = null;
        for (MonthlyPayment p : all) {
            if (p.getId() == localId) {
                target = p;
                break;
            }
        }
        if (target == null) {
            ArrayList<MonthlyPayment> deleted = paymentDb.getDeletedPayments();
            for (MonthlyPayment p : deleted) {
                if (p.getId() == localId) {
                    target = p;
                    break;
                }
            }
        }
        if (target != null) {
            uploadPayment(target, callback);
        } else {
            MonthlyPayment fallback = new MonthlyPayment(localId, serverId, name, completed, dueDate, "PENDING", null, RecurrenceType.MONTHLY, "0");
            uploadPayment(fallback, callback);
        }
    }

    public void uploadPayment(MonthlyPayment payment, SyncCallback<Long> callback) {
        ReminderApplication.enqueueSyncWorker(context);
        int localId = payment.getId();
        long localUpdatedAt = paymentDb.getPaymentUpdatedAt(localId);
        PaymentRequest request = new PaymentRequest(
                payment.getName(),
                payment.getDueDate(),
                payment.isCompleted(),
                localUpdatedAt,
                payment.getAmount(),
                payment.getRecurrence() != null ? payment.getRecurrence().name() : "MONTHLY",
                payment.getNotificationOffsets() != null ? payment.getNotificationOffsets() : "0"
        );
        Long serverId = payment.getServerId();

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
        ReminderApplication.enqueueSyncWorker(context);
        if (serverId == null || serverId <= 0) {
            // Local-only payment, no server call needed, hard delete immediately
            paymentDb.deletePayment(localId);
            callback.onSuccess(null);
            return;
        }

        // Soft-delete locally first so it persists even if we are offline
        paymentDb.softDeletePayment(localId);

        repository.deletePayment(serverId, new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful() || response.code() == 404) {
                    paymentDb.updateSyncStatus(localId, serverId, "DELETE_SYNCED");
                    paymentDb.deletePayment(localId); // Hard-delete locally on success or 404
                    callback.onSuccess(null);
                } else {
                    callback.onError("Server delete failed: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onError("Network failure on delete: " + t.getMessage());
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
        Log.d(TAG, "performFullSync started");
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
                                Log.d(TAG, "performFullSync completed");
                                
                                // Broadcast sync completed
                                android.content.Intent intent = new android.content.Intent(ACTION_SYNC_COMPLETED);
                                intent.setPackage(context.getPackageName());
                                context.sendBroadcast(intent);

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

    private void syncDeletedNotes(List<QuickNote> deleted, int index, Runnable onFinished) {
        if (index >= deleted.size()) {
            onFinished.run();
            return;
        }
        QuickNote note = deleted.get(index);
        if (note.getServerId() == null || note.getServerId() <= 0) {
            noteDb.deleteNote(note.getId());
            syncDeletedNotes(deleted, index + 1, onFinished);
            return;
        }
        repository.deleteNote(note.getServerId(), new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful() || response.code() == 404) {
                    noteDb.updateSyncStatus(note.getId(), note.getServerId(), "DELETE_SYNCED");
                    noteDb.deleteNote(note.getId());
                }
                syncDeletedNotes(deleted, index + 1, onFinished);
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG, "Failed to sync delete note offline: " + t.getMessage());
                syncDeletedNotes(deleted, index + 1, onFinished);
            }
        });
    }

    private void syncNotes(SyncCallback<Void> callback) {
        List<QuickNote> deleted = noteDb.getDeletedNotes();
        syncDeletedNotes(deleted, 0, () -> {
            java.util.Set<Long> deletedServerIds = new java.util.HashSet<>();
            for (QuickNote dn : deleted) {
                if (dn.getServerId() != null) {
                    deletedServerIds.add(dn.getServerId());
                }
            }

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
                            if (serverNote.getId() != null && deletedServerIds.contains(serverNote.getId())) {
                                continue; // Skip since it's locally deleted and pending deletion sync
                            }

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
                                if (serverMillis > localMillis) {
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

    private void syncDeletedReminders(List<Reminder> deleted, int index, Runnable onFinished) {
        if (index >= deleted.size()) {
            onFinished.run();
            return;
        }
        Reminder reminder = deleted.get(index);
        if (reminder.getServerId() == null || reminder.getServerId() <= 0) {
            reminderDb.deleteReminder(reminder.getId());
            syncDeletedReminders(deleted, index + 1, onFinished);
            return;
        }
        repository.deleteReminder(reminder.getServerId(), new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful() || response.code() == 404) {
                    reminderDb.updateSyncStatus(reminder.getId(), reminder.getServerId(), "DELETE_SYNCED");
                    reminderDb.deleteReminder(reminder.getId());
                }
                syncDeletedReminders(deleted, index + 1, onFinished);
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG, "Failed to sync delete reminder offline: " + t.getMessage());
                syncDeletedReminders(deleted, index + 1, onFinished);
            }
        });
    }

    private void syncRemindersBidirectional(SyncCallback<Void> callback) {
        List<Reminder> deleted = reminderDb.getDeletedReminders();
        syncDeletedReminders(deleted, 0, () -> {
            java.util.Set<Long> deletedServerIds = new java.util.HashSet<>();
            for (Reminder dr : deleted) {
                if (dr.getServerId() != null) {
                    deletedServerIds.add(dr.getServerId());
                }
            }

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
                                    Log.d("REMINDER SCHEDULER", "Cancelling reminder: localId=" + local.getId());
                                    com.example.reminder.AlarmUtils.cancelReminder(context, local.getId());
                                }
                            }
                        }

                        // Upsert server reminders
                        for (ReminderResponse serverReminder : serverReminders) {
                            if (serverReminder.getId() != null && deletedServerIds.contains(serverReminder.getId())) {
                                continue; // Skip since it's locally deleted
                            }

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
                                if (serverMillis > localMillis) {
                                    boolean reminderTimeChanged = serverReminder.getReminderTime() != localReminder.getTimeMillis();
                                    long localId = reminderDb.insertOrUpdateSyncedReminder(
                                            serverReminder.getId(),
                                            serverReminder.getText(),
                                            serverReminder.getReminderTime(),
                                            isExpired,
                                            snoozedTime,
                                            serverMillis
                                    );
                                    // Only cancel+reschedule if the reminder TIME actually changed.
                                    // If only other fields changed (e.g. updatedAt from a server touch),
                                    // do NOT disrupt an already-scheduled alarm — that creates race conditions.
                                    if (reminderTimeChanged) {
                                        Log.d("REMINDER SCHEDULER", "Reminder time changed, cancelling and rescheduling: localId=" + localId);
                                        com.example.reminder.AlarmUtils.cancelReminder(context, (int) localId);
                                        // Guard: only schedule if at least 30 seconds remain to avoid near-fire cancellation race
                                        if (!isExpired && serverReminder.getReminderTime() > System.currentTimeMillis() + 30000) {
                                            Log.d("REMINDER SCHEDULER", "Scheduling reminder:\nlocalId=" + localId + "\nserverId=" + serverReminder.getId() + "\ntime=" + serverReminder.getReminderTime() + "\nsuccess=true");
                                            com.example.reminder.AlarmUtils.scheduleReminder(context, (int) localId, serverReminder.getText(), serverReminder.getReminderTime());
                                        } else {
                                            Log.d("REMINDER SCHEDULER", "Skipping reschedule for localId=" + localId + ": isExpired=" + isExpired + " or fire time too near/past.");
                                        }
                                    } else {
                                        Log.d("REMINDER SCHEDULER", "Reminder time unchanged for localId=" + localId + ", not disturbing existing alarm.");
                                    }
                                }
                            } else {
                                // New reminder from server — insert and schedule
                                long localId = reminderDb.insertOrUpdateSyncedReminder(
                                        serverReminder.getId(),
                                        serverReminder.getText(),
                                        serverReminder.getReminderTime(),
                                        isExpired,
                                        snoozedTime,
                                        serverMillis
                                );
                                // Fresh insert: always schedule if in the future (no cancel risk)
                                if (!isExpired && serverReminder.getReminderTime() > System.currentTimeMillis()) {
                                    Log.d("REMINDER SCHEDULER", "Scheduling reminder (new):\nlocalId=" + localId + "\nserverId=" + serverReminder.getId() + "\ntime=" + serverReminder.getReminderTime() + "\nsuccess=true");
                                    com.example.reminder.AlarmUtils.scheduleReminder(context, (int) localId, serverReminder.getText(), serverReminder.getReminderTime());
                                }
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

    private void syncDeletedPayments(List<MonthlyPayment> deletedPayments, int index, Runnable onFinished) {
        if (index >= deletedPayments.size()) {
            onFinished.run();
            return;
        }
        MonthlyPayment payment = deletedPayments.get(index);
        if (payment.getServerId() == null || payment.getServerId() <= 0) {
            paymentDb.deletePayment(payment.getId());
            syncDeletedPayments(deletedPayments, index + 1, onFinished);
            return;
        }
        repository.deletePayment(payment.getServerId(), new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful() || response.code() == 404) {
                    paymentDb.deletePayment(payment.getId());
                }
                syncDeletedPayments(deletedPayments, index + 1, onFinished);
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                // Keep soft-deleted locally, retry next time
                syncDeletedPayments(deletedPayments, index + 1, onFinished);
            }
        });
    }

    private void syncPaymentsBidirectional(SyncCallback<Void> callback) {
        List<MonthlyPayment> deletedPayments = paymentDb.getDeletedPayments();
        syncDeletedPayments(deletedPayments, 0, () -> {
            java.util.Set<Long> localDeletedServerIds = new java.util.HashSet<>();
            for (MonthlyPayment dp : deletedPayments) {
                if (dp.getServerId() != null) {
                    localDeletedServerIds.add(dp.getServerId());
                }
            }

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
                                    Log.d("PAYMENT SCHEDULER", "Cancelling payment alarm: localId=" + local.getId());
                                    AlarmUtils.cancelPaymentAlarm(context, local.getId(), local.getName());
                                    AlarmUtils.cancelNotification(context, local.getId());
                                }
                            }
                        }

                        java.util.Calendar cal = java.util.Calendar.getInstance();
                        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                        cal.set(java.util.Calendar.MINUTE, 0);
                        cal.set(java.util.Calendar.SECOND, 0);
                        cal.set(java.util.Calendar.MILLISECOND, 0);
                        long startOfToday = cal.getTimeInMillis();

                        // Upsert server payments
                        for (PaymentResponse serverPayment : serverPayments) {
                            if (serverPayment.getId() != null && localDeletedServerIds.contains(serverPayment.getId())) {
                                continue; // Skip deleted payments
                            }

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
                                if (serverMillis > localMillis) {
                                    Double amt = serverPayment.getAmount();
                                    String recStr = serverPayment.getRecurrence();
                                    RecurrenceType rec = RecurrenceType.MONTHLY;
                                    if (recStr != null) {
                                        try {
                                            rec = RecurrenceType.valueOf(recStr.toUpperCase());
                                        } catch (IllegalArgumentException ignored) {}
                                    }
                                    String offsets = serverPayment.getNotificationOffsets() != null ? serverPayment.getNotificationOffsets() : "0";

                                    long localId = paymentDb.insertOrUpdateSyncedPayment(
                                            serverPayment.getId(),
                                            serverPayment.getName(),
                                            serverPayment.getDueDate(),
                                            completed,
                                            serverMillis,
                                            amt,
                                            rec,
                                            offsets
                                    );
                                    Log.d("PAYMENT SCHEDULER", "Cancelling previous payment alarm: localId=" + localId);
                                    AlarmUtils.cancelPaymentAlarm(context, (int) localId, serverPayment.getName());
                                    if (completed) {
                                        AlarmUtils.cancelNotification(context, (int) localId);
                                    } else {
                                        if (serverPayment.getDueDate() > System.currentTimeMillis()) {
                                            Log.d("PAYMENT SCHEDULER", "Scheduling payment:\nlocalId=" + localId + "\nserverId=" + serverPayment.getId() + "\ndueDate=" + serverPayment.getDueDate() + "\nsuccess=true");
                                            AlarmUtils.schedulePaymentAlarm(context, (int) localId, serverPayment.getName(), serverPayment.getDueDate());
                                        } else if (serverPayment.getDueDate() >= startOfToday) {
                                            Log.d("PAYMENT SCHEDULER", "Triggering immediate payment notification (due today):\nlocalId=" + localId + "\nserverId=" + serverPayment.getId() + "\ndueDate=" + serverPayment.getDueDate());
                                            AlarmUtils.showMonthlyPaymentNotification(context, (int) localId, serverPayment.getName());
                                        }
                                    }
                                }
                            } else {
                                Double amt = serverPayment.getAmount();
                                String recStr = serverPayment.getRecurrence();
                                RecurrenceType rec = RecurrenceType.MONTHLY;
                                if (recStr != null) {
                                    try {
                                        rec = RecurrenceType.valueOf(recStr.toUpperCase());
                                    } catch (IllegalArgumentException ignored) {}
                                }
                                String offsets = serverPayment.getNotificationOffsets() != null ? serverPayment.getNotificationOffsets() : "0";

                                long localId = paymentDb.insertOrUpdateSyncedPayment(
                                        serverPayment.getId(),
                                        serverPayment.getName(),
                                        serverPayment.getDueDate(),
                                        completed,
                                        serverMillis,
                                        amt,
                                        rec,
                                        offsets
                                );
                                if (!completed) {
                                    if (serverPayment.getDueDate() > System.currentTimeMillis()) {
                                        Log.d("PAYMENT SCHEDULER", "Scheduling payment:\nlocalId=" + localId + "\nserverId=" + serverPayment.getId() + "\ndueDate=" + serverPayment.getDueDate() + "\nsuccess=true");
                                        AlarmUtils.schedulePaymentAlarm(context, (int) localId, serverPayment.getName(), serverPayment.getDueDate());
                                    } else if (serverPayment.getDueDate() >= startOfToday) {
                                        Log.d("PAYMENT SCHEDULER", "Triggering immediate payment notification (due today):\nlocalId=" + localId + "\nserverId=" + serverPayment.getId() + "\ndueDate=" + serverPayment.getDueDate());
                                        AlarmUtils.showMonthlyPaymentNotification(context, (int) localId, serverPayment.getName());
                                    }
                                }
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
        });
    }

    private void uploadPendingPayments(List<MonthlyPayment> pending, int index, Runnable onFinished) {
        if (index >= pending.size()) {
            onFinished.run();
            return;
        }
        MonthlyPayment payment = pending.get(index);
        uploadPayment(payment, new SyncCallback<Long>() {
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
