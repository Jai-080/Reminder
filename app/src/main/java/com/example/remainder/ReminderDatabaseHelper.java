package com.example.remainder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;

public class ReminderDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "reminders.db";
    private static final int DATABASE_VERSION = 4;
    private static final String TAG = "ReminderDB";

    public ReminderDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS reminders (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "text TEXT NOT NULL, " +
                "time INTEGER NOT NULL, " +
                "is_expired INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE IF NOT EXISTS quick_notes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "text TEXT NOT NULL, " +
                "is_completed INTEGER DEFAULT 0)");

        Log.d(TAG, "Database created.");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE reminders ADD COLUMN is_expired INTEGER DEFAULT 0");
                Log.d(TAG, "Upgraded DB: added is_expired column");
            } catch (Exception e) {
                Log.w(TAG, "Column is_expired may already exist.");
            }
        }

        if (oldVersion < 4) {
            db.execSQL("CREATE TABLE IF NOT EXISTS quick_notes (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "text TEXT NOT NULL, " +
                    "is_completed INTEGER DEFAULT 0)");
            Log.d(TAG, "Quick Notes table created.");
        }
    }

    // Reminders Logic (existing)
    public void addReminder(String text, long timeMillis) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("text", text);
        values.put("time", timeMillis);
        values.put("is_expired", 0);
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
        int rows = db.delete("reminders", "id=?", new String[]{String.valueOf(id)});
        Log.d(TAG, "Deleted reminder ID: " + id + ", rows affected: " + rows);
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

            Log.d(TAG, "Loading pending reminders. Count: " + cursor.getCount());

            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                String text = cursor.getString(cursor.getColumnIndexOrThrow("text"));
                long time = cursor.getLong(cursor.getColumnIndexOrThrow("time"));
                reminders.add(new Reminder(id, text, time));
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

            Log.d(TAG, "Loading expired reminders. Count: " + cursor.getCount());

            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                String text = cursor.getString(cursor.getColumnIndexOrThrow("text"));
                long time = cursor.getLong(cursor.getColumnIndexOrThrow("time"));
                reminders.add(new Reminder(id, text, time));
            }
        }

        db.close();
        return reminders;
    }

    public void markAsExpired(int id) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_expired", 1);
        int rows = db.update("reminders", values, "id=?", new String[]{String.valueOf(id)});
        Log.d(TAG, "Marked reminder ID " + id + " as expired. Rows affected: " + rows);
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
                String text = cursor.getString(cursor.getColumnIndexOrThrow("text"));
                long time = cursor.getLong(cursor.getColumnIndexOrThrow("time"));
                reminders.add(new Reminder(id, text, time));
            }
        }

        db.close();
        return reminders;
    }

    // ✅ Quick Notes Logic
    public void addQuickNote(String text) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("text", text);
        values.put("is_completed", 0);
        db.insert("quick_notes", null, values);
        db.close();
    }

    public ArrayList<QuickNote> getAllQuickNotes() {
        ArrayList<QuickNote> notes = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.query("quick_notes", null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                String text = cursor.getString(cursor.getColumnIndexOrThrow("text"));
                int isCompleted = cursor.getInt(cursor.getColumnIndexOrThrow("is_completed"));
                notes.add(new QuickNote(id, text, isCompleted == 1));
            }
        }

        db.close();
        return notes;
    }

    public void updateQuickNote(int id, String newText) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("text", newText);
        db.update("quick_notes", values, "id=?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void deleteQuickNote(int id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("quick_notes", "id=?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void toggleQuickNoteCompletion(int id, boolean isCompleted) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_completed", isCompleted ? 1 : 0);
        db.update("quick_notes", values, "id=?", new String[]{String.valueOf(id)});
        db.close();
    }
}
