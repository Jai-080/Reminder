package com.example.remainder;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.widget.RemoteViews;

import java.util.ArrayList;

public class QuickNotesWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // Update all active widgets
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        // Inflate the layout for the widget
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_quick_notes);

        // Load note texts from database
        QuickNoteDatabaseHelper dbHelper = new QuickNoteDatabaseHelper(context);
        ArrayList<String> noteTexts = dbHelper.getNoteTexts();

        // Build combined string
        StringBuilder notesBuilder = new StringBuilder();
        for (String note : noteTexts) {
            notesBuilder.append("• ").append(note).append("\n");
        }

        if (noteTexts.isEmpty()) {
            notesBuilder.append("No notes yet.");
        }

        // Set text in widget
        views.setTextViewText(R.id.widget_quick_notes_text, notesBuilder.toString());

        // Update widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    // Optional: Call this method from anywhere in your app to refresh the widget
    public static void refreshWidget(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName widget = new ComponentName(context, QuickNotesWidget.class);
        int[] widgetIds = appWidgetManager.getAppWidgetIds(widget);
        for (int id : widgetIds) {
            updateAppWidget(context, appWidgetManager, id);
        }
    }
}
