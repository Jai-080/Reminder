package com.example.reminder;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import java.util.ArrayList;
import java.util.List;

public class QuickNotesWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new QuickNotesRemoteViewsFactory(this.getApplicationContext());
    }

    public static class QuickNotesRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
        private static final String TAG = "WidgetFactory";
        private final Context context;
        private List<String> notes = new ArrayList<>();

        public QuickNotesRemoteViewsFactory(Context context) {
            this.context = context;
        }

        @Override
        public void onCreate() {
        }

        @Override
        public void onDataSetChanged() {
            Log.d(TAG, "onDataSetChanged called");
            QuickNoteDatabaseHelper dbHelper = new QuickNoteDatabaseHelper(context);
            try {
                notes = dbHelper.getNoteTexts();
                Log.d(TAG, "Notes found: " + notes.size());
            } catch (Exception e) {
                Log.e(TAG, "Error fetching notes", e);
            } finally {
                dbHelper.close();
            }
        }

        @Override
        public void onDestroy() {
            notes.clear();
        }

        @Override
        public int getCount() {
            return notes.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= notes.size()) {
                return null;
            }

            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_item_note);
            views.setTextViewText(R.id.widget_item_text, "• " + notes.get(position));

            // Fill-in intent for the item click. This will be merged with the 
            // PendingIntent template in the Provider.
            Intent fillInIntent = new Intent();
            // You can add extras here if you want to pass data to MainActivity
            // fillInIntent.putExtra("note_text", notes.get(position));
            views.setOnClickFillInIntent(R.id.widget_item_text, fillInIntent);

            return views;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
