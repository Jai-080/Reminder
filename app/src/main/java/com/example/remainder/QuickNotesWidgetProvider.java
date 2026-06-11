package com.example.remainder;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.util.List;

public class QuickNotesWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        QuickNoteDatabaseHelper dbHelper = new QuickNoteDatabaseHelper(context);
        List<QuickNote> notes = dbHelper.getAllNotes();

        StringBuilder noteTextBuilder = new StringBuilder();
        for (QuickNote note : notes) {
            if (!note.isCompleted()) {
                noteTextBuilder.append("• ").append(note.getText()).append("\n");
            }
        }

        String widgetText = noteTextBuilder.length() > 0
                ? noteTextBuilder.toString().trim()
                : "No quick notes";

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_quick_notes);
        views.setTextViewText(R.id.widget_quick_notes_text, widgetText);

        // Launch MainActivity when entire widget is clicked
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    // Call this method to update the widget externally (after add/edit/delete)
    public static void updateWidget(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName widget = new ComponentName(context, QuickNotesWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(widget);
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }
}
