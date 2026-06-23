package com.example.reminder;

import android.content.Context;
import android.widget.TimePicker;

public class CustomTimePickerDialog extends android.app.TimePickerDialog {

    private int lastHour;
    private int lastMinute;
    private boolean isUpdating = false;

    public CustomTimePickerDialog(Context context, OnTimeSetListener listener, int hourOfDay, int minute, boolean is24HourView) {
        super(context, listener, hourOfDay, minute, is24HourView);
        lastHour = hourOfDay;
        lastMinute = minute;
    }

    public CustomTimePickerDialog(Context context, int themeResId, OnTimeSetListener listener, int hourOfDay, int minute, boolean is24HourView) {
        super(context, themeResId, listener, hourOfDay, minute, is24HourView);
        lastHour = hourOfDay;
        lastMinute = minute;
    }

    @Override
    public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
        if (isUpdating) {
            super.onTimeChanged(view, hourOfDay, minute);
            return;
        }

        int targetHour = hourOfDay;
        if (lastMinute > 45 && minute < 15) {
            isUpdating = true;
            targetHour = (hourOfDay + 1) % 24;
            view.setHour(targetHour);
            isUpdating = false;
        } else if (lastMinute < 15 && minute > 45) {
            isUpdating = true;
            targetHour = (hourOfDay - 1 + 24) % 24;
            view.setHour(targetHour);
            isUpdating = false;
        }

        lastHour = targetHour;
        lastMinute = minute;

        super.onTimeChanged(view, targetHour, minute);
    }
}
