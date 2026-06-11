package com.example.remainder;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

public class QuickNotesWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new QuickNotesRemoteViewsFactory(this.getApplicationContext());
    }
}

class QuickNotesRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private Context context;
    private List<String> notes = new ArrayList<>();

    public QuickNotesRemoteViewsFactory(Context context) {
        this.context = context;
    }

    @Override
    public void onCreate() {
        // Data initialized in onDataSetChanged
    }

    @Override
    public void onDataSetChanged() {
        QuickNoteDatabaseHelper dbHelper = new QuickNoteDatabaseHelper(context);
        notes = dbHelper.getNoteTexts();
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
        if (position >= notes.size()) return null;

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_item_note);
        views.setTextViewText(R.id.widget_item_text, "• " + notes.get(position));

        // Create fill-in intent for clicks if needed later
        Intent fillInIntent = new Intent();
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
