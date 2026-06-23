package com.example.reminder;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

public class QuickNotesWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_quick_notes);

        // Set up the intent that starts the QuickNotesWidgetService, which will
        // provide the views for this collection.
        Intent intent = new Intent(context, QuickNotesWidgetService.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        // When intents are compared, the extras are ignored, so we need to embed a unique
        // data URI so that the extras are not ignored.
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.widget_list, intent);

        // The empty view is displayed when the collection has no items. It should be a sibling
        // of the collection view.
        views.setEmptyView(R.id.widget_list, R.id.widget_empty_view);

        // Launch MainActivity when entire widget is clicked
        Intent mainIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent);

        // Set up the click template for individual items (Broadcast template)
        Intent clickIntent = new Intent(context, QuickNotesWidgetProvider.class);
        clickIntent.setAction(ACTION_WIDGET_CLICK);
        PendingIntent clickPendingIntent = PendingIntent.getBroadcast(
                context, 0, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        views.setPendingIntentTemplate(R.id.widget_list, clickPendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    public static final String ACTION_WIDGET_CLICK = "com.example.reminder.ACTION_WIDGET_CLICK";

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && ACTION_WIDGET_CLICK.equals(intent.getAction())) {
            int noteId = intent.getIntExtra("note_id", -1);
            boolean openApp = intent.getBooleanExtra("open_app", false);

            if (openApp || noteId == -1) {
                Intent mainIntent = new Intent(context, MainActivity.class);
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(mainIntent);
            } else {
                String text = intent.getStringExtra("note_text");
                int position = intent.getIntExtra("note_position", 0);
                long serverId = intent.getLongExtra("note_server_id", -1L);

                QuickNoteDatabaseHelper dbHelper = new QuickNoteDatabaseHelper(context);
                java.util.ArrayList<QuickNote> allNotes = dbHelper.getAllNotes();
                boolean targetCompletedState = true;
                for (QuickNote n : allNotes) {
                    if (n.getId() == noteId) {
                        targetCompletedState = !n.isCompleted();
                        break;
                    }
                }

                dbHelper.updateNote(noteId, text, targetCompletedState);
                dbHelper.close();

                final boolean finalCompletedState = targetCompletedState;
                Long sId = serverId == -1L ? null : serverId;
                com.example.reminder.sync.SyncManager.getInstance(context).uploadNote(
                        noteId, text, finalCompletedState, position, sId, new com.example.reminder.sync.SyncManager.SyncCallback<Long>() {
                            @Override
                            public void onSuccess(Long result) {
                                android.util.Log.d("WidgetProvider", "Widget note toggle sync succeeded: new state=" + finalCompletedState);
                            }

                            @Override
                            public void onError(String error) {
                                android.util.Log.e("WidgetProvider", "Widget note toggle sync failed: " + error);
                            }
                        }
                );

                // Broadcast sync completed to refresh MainActivity UI in case it is open
                android.content.Intent syncIntent = new android.content.Intent(com.example.reminder.sync.SyncManager.ACTION_SYNC_COMPLETED);
                syncIntent.setPackage(context.getPackageName());
                context.sendBroadcast(syncIntent);

                // Refresh widget list
                updateWidget(context);
            }
        }
    }

    // Call this method to update the widget externally (after add/edit/delete)
    public static void updateWidget(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName widget = new ComponentName(context, QuickNotesWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(widget);

        // Notify the list view to refresh its data source
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list);

        // Also trigger an update for the views themselves (e.g., if we changed layout)
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }
}
