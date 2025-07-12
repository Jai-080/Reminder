package com.example.remainder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

public class PaymentDatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "payments.db";
    private static final int DB_VERSION = 1;

    private static final String TABLE_NAME = "monthly_payments";
    private static final String COL_ID = "id";
    private static final String COL_NAME = "name";
    private static final String COL_DUE_DATE = "due_date";
    private static final String COL_COMPLETED = "completed";

    public PaymentDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE " + TABLE_NAME + " (" +
                        COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COL_NAME + " TEXT NOT NULL, " +
                        COL_DUE_DATE + " INTEGER NOT NULL, " +
                        COL_COMPLETED + " INTEGER NOT NULL DEFAULT 0)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public void insertPayment(String name, long dueDateMillis, boolean isCompleted) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NAME, name);
        values.put(COL_DUE_DATE, dueDateMillis);
        values.put(COL_COMPLETED, isCompleted ? 1 : 0);
        db.insert(TABLE_NAME, null, values);
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
        return list;
    }

    public void updatePaymentStatus(int id, boolean isCompleted) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_COMPLETED, isCompleted ? 1 : 0);
        db.update(TABLE_NAME, values, COL_ID + " = ?", new String[]{String.valueOf(id)});
    }
    public void deletePayment(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, "id = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void deleteAllPayments() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, null, null);
        db.close();
    }


}
