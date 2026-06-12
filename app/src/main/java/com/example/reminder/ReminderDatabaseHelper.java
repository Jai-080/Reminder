package com.example.reminder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;

public class ReminderDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "reminders.db";
    private static final int DATABASE_VERSION = 6;
    private static final String TAG = "ReminderDB";

    public ReminderDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS reminders (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "server_id BIGINT, " +
                "text TEXT NOT NULL, " +
                "time BIGINT NOT NULL, " +
                "is_expired INTEGER DEFAULT 0, " +
                "snoozed_time BIGINT DEFAULT 0, " +
                "updated_at BIGINT, " +
                "sync_status TEXT)");

        Log.d(TAG, "Database created.");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE reminders ADD COLUMN is_expired INTEGER DEFAULT 0");
            } catch (Exception e) {
                Log.w(TAG, "Column is_expired may already exist.");
            }
        }

        if (oldVersion < 5) {
            try {
                db.execSQL("ALTER TABLE reminders ADD COLUMN snoozed_time BIGINT DEFAULT 0");
            } catch (Exception e) {
                Log.w(TAG, "Column snoozed_time may already exist.");
            }
        }

        if (oldVersion < 6) {
            try {
                // Add sync fields
                db.execSQL("ALTER TABLE reminders ADD COLUMN server_id BIGINT");
                db.execSQL("ALTER TABLE reminders ADD COLUMN updated_at BIGINT");
                db.execSQL("ALTER TABLE reminders ADD COLUMN sync_status TEXT");
                
                // Remove quick_notes table from this DB if it exists
                db.execSQL("DROP TABLE IF EXISTS quick_notes");
                Log.d(TAG, "Upgraded DB to version 6: added sync fields and removed quick_notes table.");
            } catch (Exception e) {
                Log.w(TAG, "Error during version 6 upgrade: " + e.getMessage());
            }
        }
    }

    // ------------------------
    // Reminders Logic
    // ------------------------

    public void addReminder(String text, long timeMillis) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("text", text);
        values.put("time", timeMillis);
        values.put("is_expired", 0);
        values.put("snoozed_time", 0);
        values.put("updated_at", System.currentTimeMillis());
        values.put("sync_status", "PENDING");
        long result = db.insert("reminders", null, values);
        if (result == -1) {
            Log.e(TAG, "Failed to insert reminder: " + text);
        } else {
            Log.d(TAG, "Added reminder ID " + result + ": " + text + " at " + timeMillis);
        }
        db.close();
    }

    public void deleteReminder(int id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("reminders", "id=?", new String[]{String.valueOf(id)});
        db.close();
    }

    public ArrayList<Reminder> getPendingReminders() {
        ArrayList<Reminder> reminders = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.query(
                "reminders",
                null,
                "is_expired=?",
                new String[]{"0"},
                null, null,
                "time ASC")) {

            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                
                Long serverId = null;
                int serverIdIdx = cursor.getColumnIndexOrThrow("server_id");
                if (!cursor.isNull(serverIdIdx)) {
                    serverId = cursor.getLong(serverIdIdx);
                }
                
                String text = cursor.getString(cursor.getColumnIndexOrThrow("text"));
                long time = cursor.getLong(cursor.getColumnIndexOrThrow("time"));
                boolean isExpired = cursor.getInt(cursor.getColumnIndexOrThrow("is_expired")) == 1;
                long snoozedTime = cursor.getLong(cursor.getColumnIndexOrThrow("snoozed_time"));
                String syncStatus = cursor.getString(cursor.getColumnIndexOrThrow("sync_status"));
                
                reminders.add(new Reminder(id, serverId, text, time, isExpired, snoozedTime, syncStatus));
            }
        }

        db.close();
        return reminders;
    }

    public ArrayList<Reminder> getExpiredReminders() {
        ArrayList<Reminder> reminders = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.query(
                "reminders",
                null,
                "is_expired=?",
                new String[]{"1"},
                null, null,
                "time ASC")) {

            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                
                Long serverId = null;
                int serverIdIdx = cursor.getColumnIndexOrThrow("server_id");
                if (!cursor.isNull(serverIdIdx)) {
                    serverId = cursor.getLong(serverIdIdx);
                }
                
                String text = cursor.getString(cursor.getColumnIndexOrThrow("text"));
                long time = cursor.getLong(cursor.getColumnIndexOrThrow("time"));
                boolean isExpired = cursor.getInt(cursor.getColumnIndexOrThrow("is_expired")) == 1;
                long snoozedTime = cursor.getLong(cursor.getColumnIndexOrThrow("snoozed_time"));
                String syncStatus = cursor.getString(cursor.getColumnIndexOrThrow("sync_status"));
                
                reminders.add(new Reminder(id, serverId, text, time, isExpired, snoozedTime, syncStatus));
            }
        }

        db.close();
        return reminders;
    }

    public void markAsExpired(int id) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_expired", 1);
        values.put("updated_at", System.currentTimeMillis());
        values.put("sync_status", "PENDING");
        db.update("reminders", values, "id=?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void markAsPending(int id) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_expired", 0);
        values.put("updated_at", System.currentTimeMillis());
        values.put("sync_status", "PENDING");
        db.update("reminders", values, "id=?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void updateReminderStatus(int reminderId, String status) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        if ("expired".equalsIgnoreCase(status)) {
            values.put("is_expired", 1);
        } else if ("pending".equalsIgnoreCase(status)) {
            values.put("is_expired", 0);
        }
        values.put("updated_at", System.currentTimeMillis());
        values.put("sync_status", "PENDING");

        db.update("reminders", values, "id = ?", new String[]{String.valueOf(reminderId)});
        db.close();
    }

    public ArrayList<Reminder> getAllReminders() {
        ArrayList<Reminder> reminders = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.query(
                "reminders",
                null,
                null,
                null,
                null, null,
                "time ASC")) {

            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                
                Long serverId = null;
                int serverIdIdx = cursor.getColumnIndexOrThrow("server_id");
                if (!cursor.isNull(serverIdIdx)) {
                    serverId = cursor.getLong(serverIdIdx);
                }
                
                String text = cursor.getString(cursor.getColumnIndexOrThrow("text"));
                long time = cursor.getLong(cursor.getColumnIndexOrThrow("time"));
                boolean isExpired = cursor.getInt(cursor.getColumnIndexOrThrow("is_expired")) == 1;
                long snoozedTime = cursor.getLong(cursor.getColumnIndexOrThrow("snoozed_time"));
                String syncStatus = cursor.getString(cursor.getColumnIndexOrThrow("sync_status"));
                
                reminders.add(new Reminder(id, serverId, text, time, isExpired, snoozedTime, syncStatus));
            }
        }

        db.close();
        return reminders;
    }

    public void snoozeReminder(int id, long newTimeMillis) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("snoozed_time", newTimeMillis);
        values.put("time", newTimeMillis);
        values.put("is_expired", 0); 
        values.put("updated_at", System.currentTimeMillis());
        values.put("sync_status", "PENDING");
        db.update("reminders", values, "id=?", new String[]{String.valueOf(id)});
        db.close();
    }

    public long getSnoozedTime(int id) {
        SQLiteDatabase db = getReadableDatabase();
        long snoozedTime = 0;

        try (Cursor cursor = db.query("reminders", new String[]{"snoozed_time"}, "id=?", new String[]{String.valueOf(id)}, null, null, null)) {
            if (cursor.moveToFirst()) {
                snoozedTime = cursor.getLong(cursor.getColumnIndexOrThrow("snoozed_time"));
            }
        }

        db.close();
        return snoozedTime;
    }

    public void clearSnoozeTime(int id) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("snoozed_time", 0);
        values.put("updated_at", System.currentTimeMillis());
        values.put("sync_status", "PENDING");
        db.update("reminders", values, "id=?", new String[]{String.valueOf(id)});
        db.close();
    }

    public long insertOrUpdateSyncedReminder(long serverId, String text, long time, boolean isExpired, long snoozedTime, long updatedAt) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("server_id", serverId);
        values.put("text", text);
        values.put("time", time);
        values.put("is_expired", isExpired ? 1 : 0);
        values.put("snoozed_time", snoozedTime);
        values.put("updated_at", updatedAt);
        values.put("sync_status", "SYNCED");

        long localId = -1;
        Cursor cursor = db.query("reminders", new String[]{"id"}, "server_id = ?", new String[]{String.valueOf(serverId)}, null, null, null);
        if (cursor.moveToFirst()) {
            localId = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
            db.update("reminders", values, "id = ?", new String[]{String.valueOf(localId)});
        } else {
            localId = db.insert("reminders", null, values);
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
        db.update("reminders", values, "id = ?", new String[]{String.valueOf(localId)});
        db.close();
    }
}
