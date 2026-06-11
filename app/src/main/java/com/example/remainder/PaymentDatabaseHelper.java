package com.example.remainder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;

public class PaymentDatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "payments.db";
    private static final int DB_VERSION = 2;
    private static final String TAG = "PaymentDB";

    private static final String TABLE_NAME = "monthly_payments";
    private static final String COL_ID = "id";
    private static final String COL_SERVER_ID = "server_id";
    private static final String COL_NAME = "name";
    private static final String COL_DUE_DATE = "due_date";
    private static final String COL_COMPLETED = "completed";
    private static final String COL_UPDATED_AT = "updated_at";
    private static final String COL_SYNC_STATUS = "sync_status";

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
                        COL_SYNC_STATUS + " TEXT)"
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
    }

    public int insertPayment(String name, long dueDate, boolean isCompleted) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NAME, name);
        values.put(COL_DUE_DATE, dueDate);
        values.put(COL_COMPLETED, isCompleted ? 1 : 0);
        values.put(COL_UPDATED_AT, System.currentTimeMillis());
        values.put(COL_SYNC_STATUS, "PENDING");

        long id = db.insert(TABLE_NAME, null, values);
        db.close();
        return (int) id;
    }


    public ArrayList<MonthlyPayment> getAllPayments() {
        ArrayList<MonthlyPayment> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, null, null, null, null, null, null);

        while (cursor.moveToNext()) {
            int id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID));
            String name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME));
            long dueDate = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DUE_DATE));
            boolean completed = cursor.getInt(cursor.getColumnIndexOrThrow(COL_COMPLETED)) == 1;

            list.add(new MonthlyPayment(id, name, completed, dueDate));
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

    public void deletePayment(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, COL_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void deleteAllPayments() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, null, null);
        db.close();
    }
}
