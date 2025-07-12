package com.example.remainder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;

public class QuickNoteDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "quick_notes.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_NAME = "quick_notes";
    private static final String TAG = "QuickNoteDB";

    public QuickNoteDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_NAME + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "text TEXT NOT NULL, " +
                "is_completed INTEGER DEFAULT 0)");
        Log.d(TAG, "Database and table created.");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Handle upgrades if needed
    }

    public long addNote(String text, boolean isCompleted) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("text", text);
        values.put("is_completed", isCompleted ? 1 : 0);
        long id = db.insert(TABLE_NAME, null, values);
        db.close();
        return id;
    }

    public void deleteNote(int id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_NAME, "id = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void updateNote(int id, String newText, boolean isCompleted) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("text", newText);
        values.put("is_completed", isCompleted ? 1 : 0);
        db.update(TABLE_NAME, values, "id = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public ArrayList<QuickNote> getAllNotes() {
        ArrayList<QuickNote> notes = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, null, null, null, null, null, null);

        while (cursor.moveToNext()) {
            int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
            String text = cursor.getString(cursor.getColumnIndexOrThrow("text"));
            boolean isCompleted = cursor.getInt(cursor.getColumnIndexOrThrow("is_completed")) == 1;
            notes.add(new QuickNote(id, text, isCompleted));
        }

        cursor.close();
        db.close();
        return notes;
    }

    public long addNote(String noteText) {
        return addNote(noteText, false);
    }

}
