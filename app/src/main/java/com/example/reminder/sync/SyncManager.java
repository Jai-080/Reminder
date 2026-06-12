package com.example.reminder.sync;

import android.content.Context;
import android.util.Log;

import com.example.reminder.PaymentDatabaseHelper;
import com.example.reminder.QuickNoteDatabaseHelper;
import com.example.reminder.ReminderDatabaseHelper;
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
}
