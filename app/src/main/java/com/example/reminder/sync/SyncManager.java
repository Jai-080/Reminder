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

    private final java.util.concurrent.atomic.AtomicBoolean syncRunning = new java.util.concurrent.atomic.AtomicBoolean(false);

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
        System.out.println("GET /api/notes: full sync");
        Log.d(TAG, "GET /api/notes: full sync");

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
        System.out.println("GET /api/reminders: full sync");
        Log.d(TAG, "GET /api/reminders: full sync");
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
        System.out.println("GET /api/payments: full sync");
        Log.d(TAG, "GET /api/payments: full sync");
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
                                offsets,
                                payment.getLastPaidAt()
                        );
                        Long lpa = payment.getLastPaidAt();
                        boolean isRecentlyPaid = false;
                        if (lpa != null) {
                            if (rec == RecurrenceType.ONE_TIME) {
                                isRecentlyPaid = true;
                            } else {
                                java.util.Calendar calPaid = java.util.Calendar.getInstance();
                                calPaid.setTimeInMillis(lpa);
                                int paidMonth = calPaid.get(java.util.Calendar.MONTH) + 1;
                                int paidYear = calPaid.get(java.util.Calendar.YEAR);
                                java.util.Calendar calNow = java.util.Calendar.getInstance();
                                int curMonth = calNow.get(java.util.Calendar.MONTH) + 1;
                                int curYear = calNow.get(java.util.Calendar.YEAR);
                                isRecentlyPaid = (paidMonth == curMonth && paidYear == curYear);
                            }
                        }
                        if (isRecentlyPaid) {
                            AlarmUtils.cancelNotification(context, (int) localId);
                        } else {
                            if (payment.getDueDate() <= System.currentTimeMillis()) {
                                Log.d("PAYMENT SCHEDULER", "Triggering immediate payment notification:\nlocalId=" + localId + "\nserverId=" + payment.getId() + "\ndueDate=" + payment.getDueDate());
                                AlarmUtils.showMonthlyPaymentNotification(context, (int) localId, payment.getName());
                            } else {
                                Log.d("PAYMENT SCHEDULER", "Scheduling payment:\nlocalId=" + localId + "\nserverId=" + payment.getId() + "\ndueDate=" + payment.getDueDate() + "\nsuccess=true");
                                AlarmUtils.schedulePaymentAlarm(context, (int) localId, payment.getName(), payment.getDueDate());
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
        request.setLastPaidAt(payment.getLastPaidAt());
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

    public boolean isSyncRunning() {
        return syncRunning.get();
    }

    public void performFullSync(SyncCallback<Void> callback) {
        if (!tokenManager.isLoggedIn()) {
            if (callback != null) callback.onError("Not authenticated.");
            return;
        }
        if (!syncRunning.compareAndSet(false, true)) {
            Log.d(TAG, "Sync already running.");
            if (callback != null) {
                callback.onError("Sync already running.");
            }
            return;
        }
        System.out.println("syncRunning=true");
        Log.d(TAG, "syncRunning=true");
        long lastSync = tokenManager.getLastSyncTimestamp();
        long currentSync = System.currentTimeMillis();
        System.out.println("Stored lastSyncTime=" + lastSync);
        Log.d(TAG, "Stored lastSyncTime=" + lastSync);
        System.out.println("Current sync start time=" + currentSync);
        Log.d(TAG, "Current sync start time=" + currentSync);
        System.out.println("Last sync timestamp used=" + lastSync);
        Log.d(TAG, "Last sync timestamp used=" + lastSync);

        Log.d(TAG, "performFullSync started");
        System.out.println("performFullSync started");
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
                                System.out.println("performFullSync completed");

                                // Update the widget automatically
                                com.example.reminder.QuickNotesWidgetProvider.updateWidget(context);
                                
                                // Broadcast sync completed
                                android.content.Intent intent = new android.content.Intent(ACTION_SYNC_COMPLETED);
                                intent.setPackage(context.getPackageName());
                                context.sendBroadcast(intent);
                                Log.d(TAG, "SYNC_COMPLETED broadcast sent");
                                System.out.println("SYNC_COMPLETED broadcast sent");

                                int finalNotes = noteDb.getAllNotes().size();
                                int finalReminders = reminderDb.getAllReminders().size();
                                int finalPayments = paymentDb.getAllPayments().size();
                                System.out.println("Final note count=" + finalNotes);
                                Log.d(TAG, "Final note count=" + finalNotes);
                                System.out.println("Final reminder count=" + finalReminders);
                                Log.d(TAG, "Final reminder count=" + finalReminders);
                                System.out.println("Final payment count=" + finalPayments);
                                Log.d(TAG, "Final payment count=" + finalPayments);

                                System.out.println("syncRunning=false");
                                Log.d(TAG, "syncRunning=false");
                                syncRunning.set(false);
                                if (callback != null) callback.onSuccess(null);
                            }

                            @Override
                            public void onError(String error) {
                                int finalNotes = noteDb.getAllNotes().size();
                                int finalReminders = reminderDb.getAllReminders().size();
                                int finalPayments = paymentDb.getAllPayments().size();
                                System.out.println("Final note count=" + finalNotes);
                                Log.d(TAG, "Final note count=" + finalNotes);
                                System.out.println("Final reminder count=" + finalReminders);
                                Log.d(TAG, "Final reminder count=" + finalReminders);
                                System.out.println("Final payment count=" + finalPayments);
                                Log.d(TAG, "Final payment count=" + finalPayments);

                                System.out.println("syncRunning=false");
                                Log.d(TAG, "syncRunning=false");
                                syncRunning.set(false);
                                if (callback != null) callback.onError("Payment sync failed: " + error);
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        int finalNotes = noteDb.getAllNotes().size();
                        int finalReminders = reminderDb.getAllReminders().size();
                        int finalPayments = paymentDb.getAllPayments().size();
                        System.out.println("Final note count=" + finalNotes);
                        Log.d(TAG, "Final note count=" + finalNotes);
                        System.out.println("Final reminder count=" + finalReminders);
                        Log.d(TAG, "Final reminder count=" + finalReminders);
                        System.out.println("Final payment count=" + finalPayments);
                        Log.d(TAG, "Final payment count=" + finalPayments);

                        System.out.println("syncRunning=false");
                        Log.d(TAG, "syncRunning=false");
                        syncRunning.set(false);
                        if (callback != null) callback.onError("Reminder sync failed: " + error);
                    }
                });
            }

            @Override
            public void onError(String error) {
                int finalNotes = noteDb.getAllNotes().size();
                int finalReminders = reminderDb.getAllReminders().size();
                int finalPayments = paymentDb.getAllPayments().size();
                System.out.println("Final note count=" + finalNotes);
                Log.d(TAG, "Final note count=" + finalNotes);
                System.out.println("Final reminder count=" + finalReminders);
                Log.d(TAG, "Final reminder count=" + finalReminders);
                System.out.println("Final payment count=" + finalPayments);
                Log.d(TAG, "Final payment count=" + finalPayments);

                System.out.println("syncRunning=false");
                Log.d(TAG, "syncRunning=false");
                syncRunning.set(false);
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
                    String syncType = (tokenManager.getLastSyncTimestamp() > 0) ? "incremental sync" : "full sync";
                    System.out.println("GET /api/notes: " + syncType);
                    Log.d(TAG, "GET /api/notes: " + syncType);

                    if (response.isSuccessful() && response.body() != null) {
                        List<NoteResponse> serverNotes = response.body();
                        System.out.println("Server notes returned: " + serverNotes.size());
                        Log.d(TAG, "Server notes returned: " + serverNotes.size());

                        List<QuickNote> localNotesBefore = noteDb.getAllNotes();
                        System.out.println("Local notes before sync: " + localNotesBefore.size());
                        Log.d(TAG, "Local notes before sync: " + localNotesBefore.size());

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
                            System.out.println("Evaluating server note:\nserverId=" + serverNote.getId());
                            Log.d(TAG, "Evaluating server note:\nserverId=" + serverNote.getId());
                            System.out.println("Server updatedAt=" + serverNote.getUpdatedAt());
                            Log.d(TAG, "Server updatedAt=" + serverNote.getUpdatedAt());

                            if (serverNote.getId() != null && deletedServerIds.contains(serverNote.getId())) {
                                System.out.println("deleted=true");
                                Log.d(TAG, "deleted=true");
                                System.out.println("SKIPPING note because deleted");
                                Log.d(TAG, "SKIPPING note because deleted");
                                continue; // Skip since it's locally deleted and pending deletion sync
                            }

                            System.out.println("deleted=false");
                            Log.d(TAG, "deleted=false");

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
                                System.out.println("Local updatedAt=" + localMillis);
                                Log.d(TAG, "Local updatedAt=" + localMillis);

                                if (serverMillis > localMillis) {
                                    System.out.println("LWW RESULT: SERVER_WINS");
                                    Log.d(TAG, "LWW RESULT: SERVER_WINS");
                                    System.out.println("UPDATING note");
                                    Log.d(TAG, "UPDATING note");
                                    noteDb.insertOrUpdateSyncedNote(
                                            serverNote.getId(),
                                            serverNote.getText(),
                                            serverNote.getIsCompleted() != null && serverNote.getIsCompleted(),
                                            serverNote.getPosition() != null ? serverNote.getPosition() : 0,
                                            serverMillis
                                    );
                                } else {
                                    System.out.println("LWW RESULT: LOCAL_WINS");
                                    Log.d(TAG, "LWW RESULT: LOCAL_WINS");
                                    System.out.println("SKIPPING note because local is newer");
                                    Log.d(TAG, "SKIPPING note because local is newer");
                                }
                            } else {
                                System.out.println("Local updatedAt=0");
                                Log.d(TAG, "Local updatedAt=0");
                                System.out.println("LWW RESULT: SERVER_WINS");
                                Log.d(TAG, "LWW RESULT: SERVER_WINS");
                                System.out.println("INSERTING note");
                                Log.d(TAG, "INSERTING note");
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
                        uploadPendingNotes(pendingNotes, 0, () -> {
                            List<QuickNote> localNotesAfter = noteDb.getAllNotes();
                            System.out.println("Local notes after sync: " + localNotesAfter.size());
                            Log.d(TAG, "Local notes after sync: " + localNotesAfter.size());
                            callback.onSuccess(null);
                        });
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
                    String syncType = (tokenManager.getLastSyncTimestamp() > 0) ? "incremental sync" : "full sync";
                    System.out.println("GET /api/reminders: " + syncType);
                    Log.d(TAG, "GET /api/reminders: " + syncType);

                    if (response.isSuccessful() && response.body() != null) {
                        List<ReminderResponse> serverReminders = response.body();
                        System.out.println("Server reminders returned: " + serverReminders.size());
                        Log.d(TAG, "Server reminders returned: " + serverReminders.size());

                        List<Reminder> localRemindersBefore = reminderDb.getAllReminders();
                        System.out.println("Local reminders before sync: " + localRemindersBefore.size());
                        Log.d(TAG, "Local reminders before sync: " + localRemindersBefore.size());

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
                            System.out.println("Evaluating server reminder:\nserverId=" + serverReminder.getId());
                            Log.d(TAG, "Evaluating server reminder:\nserverId=" + serverReminder.getId());
                            System.out.println("Server updatedAt=" + serverReminder.getUpdatedAt());
                            Log.d(TAG, "Server updatedAt=" + serverReminder.getUpdatedAt());

                            if (serverReminder.getId() != null && deletedServerIds.contains(serverReminder.getId())) {
                                System.out.println("deleted=true");
                                Log.d(TAG, "deleted=true");
                                System.out.println("SKIPPING reminder because deleted");
                                Log.d(TAG, "SKIPPING reminder because deleted");
                                continue; // Skip since it's locally deleted
                            }

                            System.out.println("deleted=false");
                            Log.d(TAG, "deleted=false");

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
                                System.out.println("Local updatedAt=" + localMillis);
                                Log.d(TAG, "Local updatedAt=" + localMillis);

                                if (serverMillis > localMillis) {
                                    System.out.println("LWW RESULT: SERVER_WINS");
                                    Log.d(TAG, "LWW RESULT: SERVER_WINS");
                                    System.out.println("UPDATING reminder");
                                    Log.d(TAG, "UPDATING reminder");
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
                                } else {
                                    System.out.println("LWW RESULT: LOCAL_WINS");
                                    Log.d(TAG, "LWW RESULT: LOCAL_WINS");
                                    System.out.println("SKIPPING reminder because local is newer");
                                    Log.d(TAG, "SKIPPING reminder because local is newer");
                                }
                            } else {
                                // New reminder from server — insert and schedule
                                System.out.println("Local updatedAt=0");
                                Log.d(TAG, "Local updatedAt=0");
                                System.out.println("LWW RESULT: SERVER_WINS");
                                Log.d(TAG, "LWW RESULT: SERVER_WINS");
                                System.out.println("INSERTING reminder");
                                Log.d(TAG, "INSERTING reminder");
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
                        uploadPendingReminders(pendingReminders, 0, () -> {
                            List<Reminder> localRemindersAfter = reminderDb.getAllReminders();
                            System.out.println("Local reminders after sync: " + localRemindersAfter.size());
                            Log.d(TAG, "Local reminders after sync: " + localRemindersAfter.size());
                            callback.onSuccess(null);
                        });
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
    }    private void syncPaymentsBidirectional(SyncCallback<Void> callback) {
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
                    String syncType = (tokenManager.getLastSyncTimestamp() > 0) ? "incremental sync" : "full sync";
                    System.out.println("GET /api/payments: " + syncType);
                    Log.d(TAG, "GET /api/payments: " + syncType);

                    if (response.isSuccessful() && response.body() != null) {
                        List<PaymentResponse> serverPayments = response.body();
                        System.out.println("Server payments returned: " + serverPayments.size());
                        Log.d(TAG, "Server payments returned: " + serverPayments.size());

                        List<MonthlyPayment> localPaymentsBefore = paymentDb.getAllPayments();
                        System.out.println("Local payments before sync: " + localPaymentsBefore.size());
                        Log.d(TAG, "Local payments before sync: " + localPaymentsBefore.size());

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
                            System.out.println("Evaluating server payment:\nserverId=" + serverPayment.getId());
                            Log.d(TAG, "Evaluating server payment:\nserverId=" + serverPayment.getId());
                            System.out.println("Server updatedAt=" + serverPayment.getUpdatedAt());
                            Log.d(TAG, "Server updatedAt=" + serverPayment.getUpdatedAt());

                            if (serverPayment.getId() != null && localDeletedServerIds.contains(serverPayment.getId())) {
                                System.out.println("deleted=true");
                                Log.d(TAG, "deleted=true");
                                System.out.println("SKIPPING payment because deleted");
                                Log.d(TAG, "SKIPPING payment because deleted");
                                continue; // Skip deleted payments
                            }

                            System.out.println("deleted=false");
                            Log.d(TAG, "deleted=false");

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
                                System.out.println("Local updatedAt=" + localMillis);
                                Log.d(TAG, "Local updatedAt=" + localMillis);

                                if (serverMillis > localMillis) {
                                    System.out.println("LWW RESULT: SERVER_WINS");
                                    Log.d(TAG, "LWW RESULT: SERVER_WINS");
                                    System.out.println("UPDATING payment");
                                    Log.d(TAG, "UPDATING payment");
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
                                            offsets,
                                            serverPayment.getLastPaidAt()
                                    );
                                    Log.d("PAYMENT SCHEDULER", "Cancelling previous payment alarm: localId=" + localId);
                                    AlarmUtils.cancelPaymentAlarm(context, (int) localId, serverPayment.getName());
                                    
                                    Long lpa = serverPayment.getLastPaidAt();
                                    boolean isRecentlyPaid = false;
                                    if (lpa != null) {
                                        if (rec == RecurrenceType.ONE_TIME) {
                                            isRecentlyPaid = true;
                                        } else {
                                            java.util.Calendar calPaid = java.util.Calendar.getInstance();
                                            calPaid.setTimeInMillis(lpa);
                                            int paidMonth = calPaid.get(java.util.Calendar.MONTH) + 1;
                                            int paidYear = calPaid.get(java.util.Calendar.YEAR);
                                            java.util.Calendar calNow = java.util.Calendar.getInstance();
                                            int curMonth = calNow.get(java.util.Calendar.MONTH) + 1;
                                            int curYear = calNow.get(java.util.Calendar.YEAR);
                                            isRecentlyPaid = (paidMonth == curMonth && paidYear == curYear);
                                        }
                                    }
                                    if (isRecentlyPaid) {
                                        AlarmUtils.cancelNotification(context, (int) localId);
                                    } else {
                                        if (serverPayment.getDueDate() <= System.currentTimeMillis()) {
                                            Log.d("PAYMENT SCHEDULER", "Triggering immediate payment notification:\nlocalId=" + localId + "\nserverId=" + serverPayment.getId() + "\ndueDate=" + serverPayment.getDueDate());
                                            AlarmUtils.showMonthlyPaymentNotification(context, (int) localId, serverPayment.getName());
                                        } else {
                                            Log.d("PAYMENT SCHEDULER", "Scheduling payment:\nlocalId=" + localId + "\nserverId=" + serverPayment.getId() + "\ndueDate=" + serverPayment.getDueDate() + "\nsuccess=true");
                                            AlarmUtils.schedulePaymentAlarm(context, (int) localId, serverPayment.getName(), serverPayment.getDueDate());
                                        }
                                    }
                                } else {
                                    System.out.println("LWW RESULT: LOCAL_WINS");
                                    Log.d(TAG, "LWW RESULT: LOCAL_WINS");
                                    System.out.println("SKIPPING payment because local is newer");
                                    Log.d(TAG, "SKIPPING payment because local is newer");
                                }
                            } else {
                                System.out.println("Local updatedAt=0");
                                Log.d(TAG, "Local updatedAt=0");
                                System.out.println("LWW RESULT: SERVER_WINS");
                                Log.d(TAG, "LWW RESULT: SERVER_WINS");
                                System.out.println("INSERTING payment");
                                Log.d(TAG, "INSERTING payment");
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
                                        offsets,
                                        serverPayment.getLastPaidAt()
                                );
                                
                                Long lpa = serverPayment.getLastPaidAt();
                                boolean isRecentlyPaid = false;
                                if (lpa != null) {
                                    if (rec == RecurrenceType.ONE_TIME) {
                                        isRecentlyPaid = true;
                                    } else {
                                        java.util.Calendar calPaid = java.util.Calendar.getInstance();
                                        calPaid.setTimeInMillis(lpa);
                                        int paidMonth = calPaid.get(java.util.Calendar.MONTH) + 1;
                                        int paidYear = calPaid.get(java.util.Calendar.YEAR);
                                        java.util.Calendar calNow = java.util.Calendar.getInstance();
                                        int curMonth = calNow.get(java.util.Calendar.MONTH) + 1;
                                        int curYear = calNow.get(java.util.Calendar.YEAR);
                                        isRecentlyPaid = (paidMonth == curMonth && paidYear == curYear);
                                    }
                                }
                                if (isRecentlyPaid) {
                                    AlarmUtils.cancelNotification(context, (int) localId);
                                } else {
                                    if (serverPayment.getDueDate() <= System.currentTimeMillis()) {
                                        Log.d("PAYMENT SCHEDULER", "Triggering immediate payment notification:\nlocalId=" + localId + "\nserverId=" + serverPayment.getId() + "\ndueDate=" + serverPayment.getDueDate());
                                        AlarmUtils.showMonthlyPaymentNotification(context, (int) localId, serverPayment.getName());
                                    } else {
                                        Log.d("PAYMENT SCHEDULER", "Scheduling payment:\nlocalId=" + localId + "\nserverId=" + serverPayment.getId() + "\ndueDate=" + serverPayment.getDueDate() + "\nsuccess=true");
                                        AlarmUtils.schedulePaymentAlarm(context, (int) localId, serverPayment.getName(), serverPayment.getDueDate());
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
                        uploadPendingPayments(pendingPayments, 0, () -> {
                            List<MonthlyPayment> localPaymentsAfter = paymentDb.getAllPayments();
                            System.out.println("Local payments after sync: " + localPaymentsAfter.size());
                            Log.d(TAG, "Local payments after sync: " + localPaymentsAfter.size());
                            callback.onSuccess(null);
                        });
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
