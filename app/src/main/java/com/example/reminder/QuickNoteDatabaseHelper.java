package com.example.reminder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;

public class QuickNoteDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "quick_notes.db";
    private static final int DATABASE_VERSION = 3;
    private static final String TABLE_NAME = "quick_notes";
    private static final String TAG = "QuickNoteDB";

    public QuickNoteDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_NAME + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "server_id BIGINT, " +
                "text TEXT NOT NULL, " +
                "is_completed INTEGER DEFAULT 0, " +
                "position INTEGER DEFAULT 0, " +
                "updated_at BIGINT, " +
                "sync_status TEXT)");
        Log.d(TAG, "Database and table created.");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN position INTEGER DEFAULT 0");
            } catch (Exception e) {
                Log.w(TAG, "Column position may already exist.");
            }
        }
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN server_id BIGINT");
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN updated_at BIGINT");
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN sync_status TEXT");
            } catch (Exception e) {
                Log.w(TAG, "Sync columns may already exist.");
            }
        }
    }

    public long addNote(String text, boolean isCompleted) {
        SQLiteDatabase db = getWritableDatabase();
        
        // Get current max position
        int maxPos = 0;
        Cursor cursor = db.rawQuery("SELECT MAX(position) FROM " + TABLE_NAME, null);
        if (cursor.moveToFirst()) {
            maxPos = cursor.getInt(0);
        }
        cursor.close();

        ContentValues values = new ContentValues();
        values.put("text", text);
        values.put("is_completed", isCompleted ? 1 : 0);
        values.put("position", maxPos + 1);
        values.put("updated_at", System.currentTimeMillis());
        values.put("sync_status", "PENDING");
        long id = db.insert(TABLE_NAME, null, values);
        db.close();
        return id;
    }

    public long addNote(String noteText) {
        return addNote(noteText, false);
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
        values.put("updated_at", System.currentTimeMillis());
        values.put("sync_status", "PENDING");
        db.update(TABLE_NAME, values, "id = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public ArrayList<QuickNote> getAllNotes() {
        ArrayList<QuickNote> notes = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, null, null, null, null, null, "position ASC");

        while (cursor.moveToNext()) {
            int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
            
            Long serverId = null;
            int serverIdIdx = cursor.getColumnIndexOrThrow("server_id");
            if (!cursor.isNull(serverIdIdx)) {
                serverId = cursor.getLong(serverIdIdx);
            }
            
            String text = cursor.getString(cursor.getColumnIndexOrThrow("text"));
            boolean isCompleted = cursor.getInt(cursor.getColumnIndexOrThrow("is_completed")) == 1;
            int position = cursor.getInt(cursor.getColumnIndexOrThrow("position"));
            String syncStatus = cursor.getString(cursor.getColumnIndexOrThrow("sync_status"));
            
            notes.add(new QuickNote(id, serverId, text, isCompleted, position, syncStatus));
        }

        cursor.close();
        db.close();
        return notes;
    }

    public ArrayList<String> getNoteTexts() {
        ArrayList<String> noteTexts = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, new String[]{"text"}, "is_completed = 0", null, null, null, "position ASC");

        while (cursor.moveToNext()) {
            noteTexts.add(cursor.getString(cursor.getColumnIndexOrThrow("text")));
        }

        cursor.close();
        db.close();
        return noteTexts;
    }

    public void updateNotePosition(int id, int newPosition) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("position", newPosition);
        values.put("updated_at", System.currentTimeMillis());
        values.put("sync_status", "PENDING");
        db.update(TABLE_NAME, values, "id = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void clearAllNotes() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_NAME, null, null);
        db.close();
    }

    public long insertOrUpdateSyncedNote(long serverId, String text, boolean isCompleted, int position, long updatedAt) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("server_id", serverId);
        values.put("text", text);
        values.put("is_completed", isCompleted ? 1 : 0);
        values.put("position", position);
        values.put("updated_at", updatedAt);
        values.put("sync_status", "SYNCED");

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
}
