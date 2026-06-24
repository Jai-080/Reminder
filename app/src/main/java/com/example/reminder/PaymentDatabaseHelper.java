package com.example.reminder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;

public class PaymentDatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "payments.db";
    private static final int DB_VERSION = 6;
    private static final String TAG = "PaymentDB";

    private static final String TABLE_NAME = "monthly_payments";
    private static final String COL_ID = "id";
    private static final String COL_SERVER_ID = "server_id";
    private static final String COL_NAME = "name";
    private static final String COL_DUE_DATE = "due_date";
    private static final String COL_COMPLETED = "completed";
    private static final String COL_UPDATED_AT = "updated_at";
    private static final String COL_SYNC_STATUS = "sync_status";
    private static final String COL_AMOUNT = "amount";
    private static final String COL_RECURRENCE = "recurrence";
    private static final String COL_NOTIFICATION_OFFSETS = "notification_offsets";
    private static final String COL_LAST_PAID_AT = "last_paid_at";

    public PaymentDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE " + TABLE_NAME + " (" +
                        COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COL_SERVER_ID + " BIGINT, " +
                        COL_NAME + " TEXT NOT NULL, " +
                        COL_DUE_DATE + " INTEGER NOT NULL, " +
                        COL_COMPLETED + " INTEGER NOT NULL DEFAULT 0, " +
                        COL_UPDATED_AT + " BIGINT, " +
                        COL_SYNC_STATUS + " TEXT, " +
                        COL_AMOUNT + " REAL, " +
                        COL_RECURRENCE + " TEXT DEFAULT 'MONTHLY', " +
                        COL_NOTIFICATION_OFFSETS + " TEXT DEFAULT '0', " +
                        "last_completed_month INTEGER, " +
                        "last_completed_year INTEGER, " +
                        "completed_at INTEGER, " +
                        COL_LAST_PAID_AT + " INTEGER)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_SERVER_ID + " BIGINT");
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_UPDATED_AT + " BIGINT");
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_SYNC_STATUS + " TEXT");
                Log.d(TAG, "Upgraded DB to version 2: added sync fields.");
            } catch (Exception e) {
                Log.w(TAG, "Sync columns may already exist.");
            }
        }
        if (oldVersion < 3) {
            Log.d(TAG, "Upgrading database to version 3 (ensuring sync columns exist for soft-delete support)");
            try {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_SERVER_ID + " BIGINT");
            } catch (Exception ignored) {}
            try {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_UPDATED_AT + " BIGINT");
            } catch (Exception ignored) {}
            try {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_SYNC_STATUS + " TEXT");
            } catch (Exception ignored) {}
            try {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_AMOUNT + " REAL");
            } catch (Exception ignored) {}
            try {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_RECURRENCE + " TEXT DEFAULT 'MONTHLY'");
            } catch (Exception ignored) {}
            try {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_NOTIFICATION_OFFSETS + " TEXT DEFAULT '0'");
            } catch (Exception ignored) {}
        }
        if (oldVersion < 4) {
            try { db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN last_completed_month INTEGER;"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN last_completed_year INTEGER;"); } catch (Exception ignored) {}
        }
        if (oldVersion < 5) {
            try { db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN completed_at INTEGER;"); } catch (Exception ignored) {}
        }
        if (oldVersion < 6) {
            try { db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_LAST_PAID_AT + " INTEGER;"); } catch (Exception ignored) {}
        }
    }

    public int insertPayment(String name, long dueDate, boolean isCompleted) {
        return insertPayment(name, dueDate, isCompleted, null, RecurrenceType.MONTHLY, "0");
    }

    public int insertPayment(String name, long dueDate, boolean isCompleted, Double amount) {
        return insertPayment(name, dueDate, isCompleted, amount, RecurrenceType.MONTHLY, "0");
    }

    public int insertPayment(String name, long dueDate, boolean isCompleted, Double amount, RecurrenceType recurrence) {
        return insertPayment(name, dueDate, isCompleted, amount, recurrence, "0");
    }

    public int insertPayment(String name, long dueDate, boolean isCompleted, Double amount, RecurrenceType recurrence, String notificationOffsets) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NAME, name);
        values.put(COL_DUE_DATE, dueDate);
        values.put(COL_COMPLETED, isCompleted ? 1 : 0);
        if (amount != null) {
            values.put(COL_AMOUNT, amount);
        } else {
            values.putNull(COL_AMOUNT);
        }
        values.put(COL_RECURRENCE, recurrence != null ? recurrence.name() : RecurrenceType.MONTHLY.name());
        values.put(COL_NOTIFICATION_OFFSETS, notificationOffsets != null ? notificationOffsets : "0");
        values.put(COL_UPDATED_AT, System.currentTimeMillis());
        values.put(COL_SYNC_STATUS, "PENDING");
        values.putNull(COL_LAST_PAID_AT);

        long id = db.insert(TABLE_NAME, null, values);
        db.close();
        return (int) id;
    }


    public ArrayList<MonthlyPayment> getAllPayments() {
        ArrayList<MonthlyPayment> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, null, COL_SYNC_STATUS + " IS NULL OR (" + COL_SYNC_STATUS + " != 'DELETE_PENDING' AND " + COL_SYNC_STATUS + " != 'DELETE_SYNCED')", null, null, null, null);

        while (cursor.moveToNext()) {
            int id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID));
            
            Long serverId = null;
            int serverIdIdx = cursor.getColumnIndexOrThrow(COL_SERVER_ID);
            if (!cursor.isNull(serverIdIdx)) {
                serverId = cursor.getLong(serverIdIdx);
            }
            
            String name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME));
            long dueDate = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DUE_DATE));
            boolean completed = cursor.getInt(cursor.getColumnIndexOrThrow(COL_COMPLETED)) == 1;
            String syncStatus = cursor.getString(cursor.getColumnIndexOrThrow(COL_SYNC_STATUS));
            Double amount = null;
            int amountIdx = cursor.getColumnIndex(COL_AMOUNT);
            if (amountIdx != -1 && !cursor.isNull(amountIdx)) {
                amount = cursor.getDouble(amountIdx);
            }
            String recurrenceStr = "MONTHLY";
            int recurrenceIdx = cursor.getColumnIndex(COL_RECURRENCE);
            if (recurrenceIdx != -1 && !cursor.isNull(recurrenceIdx)) {
                recurrenceStr = cursor.getString(recurrenceIdx);
            }
            RecurrenceType recurrence = RecurrenceType.MONTHLY;
            try {
                if (recurrenceStr != null) {
                    recurrence = RecurrenceType.valueOf(recurrenceStr.toUpperCase());
                }
            } catch (IllegalArgumentException e) {
                recurrence = RecurrenceType.MONTHLY;
            }
            String notificationOffsets = "0";
            int offsetsIdx = cursor.getColumnIndex(COL_NOTIFICATION_OFFSETS);
            if (offsetsIdx != -1 && !cursor.isNull(offsetsIdx)) {
                notificationOffsets = cursor.getString(offsetsIdx);
            }
            Long lastPaidAt = null;
            int lastPaidAtIdx = cursor.getColumnIndex(COL_LAST_PAID_AT);
            if (lastPaidAtIdx != -1 && !cursor.isNull(lastPaidAtIdx)) {
                lastPaidAt = cursor.getLong(lastPaidAtIdx);
            }

            MonthlyPayment payment = new MonthlyPayment(id, serverId, name, completed, dueDate, syncStatus, amount, recurrence, notificationOffsets);
            payment.setLastPaidAt(lastPaidAt);
            list.add(payment);
        }

        cursor.close();
        db.close();
        return list;
    }

    public void softDeletePayment(int id) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_SYNC_STATUS, "DELETE_PENDING");
        values.put(COL_UPDATED_AT, System.currentTimeMillis());
        db.update(TABLE_NAME, values, COL_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public ArrayList<MonthlyPayment> getDeletedPayments() {
        ArrayList<MonthlyPayment> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, null, COL_SYNC_STATUS + " = 'DELETE_PENDING'", null, null, null, null);

        while (cursor.moveToNext()) {
            int id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID));
            
            Long serverId = null;
            int serverIdIdx = cursor.getColumnIndexOrThrow(COL_SERVER_ID);
            if (!cursor.isNull(serverIdIdx)) {
                serverId = cursor.getLong(serverIdIdx);
            }
            
            String name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME));
            long dueDate = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DUE_DATE));
            boolean completed = cursor.getInt(cursor.getColumnIndexOrThrow(COL_COMPLETED)) == 1;
            String syncStatus = cursor.getString(cursor.getColumnIndexOrThrow(COL_SYNC_STATUS));
            Double amount = null;
            int amountIdx = cursor.getColumnIndex(COL_AMOUNT);
            if (amountIdx != -1 && !cursor.isNull(amountIdx)) {
                amount = cursor.getDouble(amountIdx);
            }
            String recurrenceStr = "MONTHLY";
            int recurrenceIdx = cursor.getColumnIndex(COL_RECURRENCE);
            if (recurrenceIdx != -1 && !cursor.isNull(recurrenceIdx)) {
                recurrenceStr = cursor.getString(recurrenceIdx);
            }
            RecurrenceType recurrence = RecurrenceType.MONTHLY;
            try {
                if (recurrenceStr != null) {
                    recurrence = RecurrenceType.valueOf(recurrenceStr.toUpperCase());
                }
            } catch (IllegalArgumentException e) {
                recurrence = RecurrenceType.MONTHLY;
            }
            String notificationOffsets = "0";
            int offsetsIdx = cursor.getColumnIndex(COL_NOTIFICATION_OFFSETS);
            if (offsetsIdx != -1 && !cursor.isNull(offsetsIdx)) {
                notificationOffsets = cursor.getString(offsetsIdx);
            }
            Long lastPaidAt = null;
            int lastPaidAtIdx = cursor.getColumnIndex(COL_LAST_PAID_AT);
            if (lastPaidAtIdx != -1 && !cursor.isNull(lastPaidAtIdx)) {
                lastPaidAt = cursor.getLong(lastPaidAtIdx);
            }

            MonthlyPayment payment = new MonthlyPayment(id, serverId, name, completed, dueDate, syncStatus, amount, recurrence, notificationOffsets);
            payment.setLastPaidAt(lastPaidAt);
            list.add(payment);
        }

        cursor.close();
        db.close();
        return list;
    }

    public void updatePaymentStatus(int id, boolean isCompleted) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_COMPLETED, isCompleted ? 1 : 0);
        values.put(COL_UPDATED_AT, System.currentTimeMillis());
        values.put(COL_SYNC_STATUS, "PENDING");
        db.update(TABLE_NAME, values, COL_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void updatePayment(MonthlyPayment payment) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NAME, payment.getName());
        values.put(COL_DUE_DATE, payment.getDueDate());
        values.put(COL_COMPLETED, payment.isCompleted() ? 1 : 0);
        if (payment.getAmount() != null) {
            values.put(COL_AMOUNT, payment.getAmount());
        } else {
            values.putNull(COL_AMOUNT);
        }
        values.put(COL_RECURRENCE, payment.getRecurrence() != null ? payment.getRecurrence().name() : RecurrenceType.MONTHLY.name());
        values.put(COL_NOTIFICATION_OFFSETS, payment.getNotificationOffsets() != null ? payment.getNotificationOffsets() : "0");
        values.put(COL_UPDATED_AT, System.currentTimeMillis());
        values.put(COL_SYNC_STATUS, "PENDING");
        if (payment.getLastPaidAt() != null) {
            values.put(COL_LAST_PAID_AT, payment.getLastPaidAt());
        } else {
            values.putNull(COL_LAST_PAID_AT);
        }
        db.update(TABLE_NAME, values, COL_ID + " = ?", new String[]{String.valueOf(payment.getId())});
        db.close();
    }

    public void deletePayment(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, COL_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public long insertOrUpdateSyncedPayment(long serverId, String name, long dueDate, boolean completed, long updatedAt) {
        return insertOrUpdateSyncedPayment(serverId, name, dueDate, completed, updatedAt, null, RecurrenceType.MONTHLY, "0", null);
    }

    public long insertOrUpdateSyncedPayment(long serverId, String name, long dueDate, boolean completed, long updatedAt, Double amount) {
        return insertOrUpdateSyncedPayment(serverId, name, dueDate, completed, updatedAt, amount, RecurrenceType.MONTHLY, "0", null);
    }

    public long insertOrUpdateSyncedPayment(long serverId, String name, long dueDate, boolean completed, long updatedAt, Double amount, RecurrenceType recurrence) {
        return insertOrUpdateSyncedPayment(serverId, name, dueDate, completed, updatedAt, amount, recurrence, "0", null);
    }

    public long insertOrUpdateSyncedPayment(long serverId, String name, long dueDate, boolean completed, long updatedAt, Double amount, RecurrenceType recurrence, String notificationOffsets) {
        return insertOrUpdateSyncedPayment(serverId, name, dueDate, completed, updatedAt, amount, recurrence, notificationOffsets, null);
    }

    public long insertOrUpdateSyncedPayment(long serverId, String name, long dueDate, boolean completed, long updatedAt, Double amount, RecurrenceType recurrence, String notificationOffsets, Long lastPaidAt) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("server_id", serverId);
        values.put("name", name);
        values.put("due_date", dueDate);
        values.put("completed", completed ? 1 : 0);
        values.put("updated_at", updatedAt);
        values.put("sync_status", "SYNCED");
        if (amount != null) {
            values.put(COL_AMOUNT, amount);
        } else {
            values.putNull(COL_AMOUNT);
        }
        values.put(COL_RECURRENCE, recurrence != null ? recurrence.name() : RecurrenceType.MONTHLY.name());
        values.put(COL_NOTIFICATION_OFFSETS, notificationOffsets != null ? notificationOffsets : "0");
        if (lastPaidAt != null) {
            values.put(COL_LAST_PAID_AT, lastPaidAt);
        } else {
            values.putNull(COL_LAST_PAID_AT);
        }

        long localId = -1;
        Cursor cursor = db.query(TABLE_NAME, new String[]{"id"}, "server_id = ?", new String[]{String.valueOf(serverId)}, null, null, null);
        if (cursor.moveToFirst()) {
            localId = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
            db.update(TABLE_NAME, values, "id = ?", new String[]{String.valueOf(localId)});
        } else {
            localId = db.insert(TABLE_NAME, null, values);
        }
        cursor.close();
        db.close();
        return localId;
    }

    public void updateSyncStatus(int localId, Long serverId, String status) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        if (serverId != null) {
            values.put("server_id", serverId);
        }
        values.put("sync_status", status);
        values.put("updated_at", System.currentTimeMillis());
        db.update(TABLE_NAME, values, "id = ?", new String[]{String.valueOf(localId)});
        db.close();
    }

    public long getPaymentUpdatedAt(int id) {
        SQLiteDatabase db = getReadableDatabase();
        long updatedAt = 0;
        Cursor cursor = db.query(TABLE_NAME, new String[]{COL_UPDATED_AT}, COL_ID + " = ?", new String[]{String.valueOf(id)}, null, null, null);
        if (cursor.moveToFirst()) {
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_UPDATED_AT));
        }
        cursor.close();
        db.close();
        return updatedAt;
    }
}
