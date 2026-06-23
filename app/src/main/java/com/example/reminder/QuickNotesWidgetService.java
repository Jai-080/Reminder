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
        private List<QuickNote> notes = new ArrayList<>();

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
                List<QuickNote> allNotes = dbHelper.getAllNotes();
                allNotes.sort((n1, n2) -> {
                    if (n1.isCompleted() != n2.isCompleted()) {
                        return Boolean.compare(n1.isCompleted(), n2.isCompleted());
                    }
                    return Integer.compare(n1.getPosition(), n2.getPosition());
                });
                notes = allNotes;
                Log.d(TAG, "Total notes found: " + notes.size());
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

            QuickNote note = notes.get(position);
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_item_note);

            // Apply strike-through and dim color for completed notes
            if (note.isCompleted()) {
                android.text.SpannableString spannableText = new android.text.SpannableString(note.getText());
                spannableText.setSpan(new android.text.style.StrikethroughSpan(), 0, note.getText().length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannableText.setSpan(new android.text.style.ForegroundColorSpan(context.getResources().getColor(R.color.colorTextMuted, context.getTheme())), 0, note.getText().length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                views.setTextViewText(R.id.widget_item_text, spannableText);
                views.setInt(R.id.widget_item_check_btn, "setImageAlpha", 102);
            } else {
                views.setTextViewText(R.id.widget_item_text, note.getText());
                views.setInt(R.id.widget_item_check_btn, "setImageAlpha", 255);
            }

            // 1. Text click opens the app
            Intent textIntent = new Intent();
            textIntent.putExtra("open_app", true);
            views.setOnClickFillInIntent(R.id.widget_item_text, textIntent);

            // 2. Bullet checkbox click completes the note
            Intent checkIntent = new Intent();
            checkIntent.putExtra("note_id", note.getId());
            checkIntent.putExtra("note_text", note.getText());
            checkIntent.putExtra("note_position", note.getPosition());
            checkIntent.putExtra("note_server_id", note.getServerId() != null ? note.getServerId() : -1L);
            views.setOnClickFillInIntent(R.id.widget_item_check_btn, checkIntent);

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
