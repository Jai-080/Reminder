package com.example.reminder;

import com.example.reminder.sync.SyncManager;
import android.util.Log;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.CheckBox;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;

public class QuickNoteAdapter extends RecyclerView.Adapter<QuickNoteAdapter.NoteViewHolder> {

    private ArrayList<QuickNote> notes;
    private Context context;
    private QuickNoteDatabaseHelper noteDbHelper;

    public QuickNoteAdapter(Context context, ArrayList<QuickNote> notes, QuickNoteDatabaseHelper noteDbHelper) {
        this.context = context;
        this.notes = notes;
        this.noteDbHelper = noteDbHelper;
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_quick_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        QuickNote note = notes.get(position);
        holder.noteText.setText(note.getText());

        // Dynamic styling depending on whether the note is completed
        if (note.isCompleted()) {
            holder.noteText.setPaintFlags(holder.noteText.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            holder.noteText.setTextColor(context.getResources().getColor(R.color.colorTextMuted, context.getTheme()));
        } else {
            holder.noteText.setPaintFlags(holder.noteText.getPaintFlags() & (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
            holder.noteText.setTextColor(context.getResources().getColor(R.color.colorTextPrimary, context.getTheme()));
        }

        // Set checkbox checked state
        holder.noteCheckBox.setChecked(note.isCompleted());

        // Toggle completion on card click or checkbox click
        View.OnClickListener toggleListener = v -> {
            note.setCompleted(!note.isCompleted());
            noteDbHelper.updateNote(note.getId(), note.getText(), note.isCompleted());

            // Sync update to server
            SyncManager.getInstance(context).uploadNote(note.getId(), note.getText(), note.isCompleted(), note.getPosition(), note.getServerId(), new SyncManager.SyncCallback<Long>() {
                @Override
                public void onSuccess(Long result) {
                    Log.d("QuickNoteAdapter", "Note completion toggle synced to server");
                }

                @Override
                public void onError(String error) {
                    Log.e("QuickNoteAdapter", "Failed to sync note completion toggle: " + error);
                }
            });

            // Update item view state
            holder.noteCheckBox.setChecked(note.isCompleted());
            if (note.isCompleted()) {
                holder.noteText.setPaintFlags(holder.noteText.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                holder.noteText.setTextColor(context.getResources().getColor(R.color.colorTextMuted, context.getTheme()));
            } else {
                holder.noteText.setPaintFlags(holder.noteText.getPaintFlags() & (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
                holder.noteText.setTextColor(context.getResources().getColor(R.color.colorTextPrimary, context.getTheme()));
            }

            QuickNotesWidgetProvider.updateWidget(context);
            if (context instanceof MainActivity) {
                ((MainActivity) context).refreshDashboardStats();
            }
        };

        holder.noteCheckBox.setOnClickListener(toggleListener);
        holder.itemView.setOnClickListener(toggleListener);

        // Edit note text on clicking the text directly
        holder.noteText.setOnClickListener(v -> {
            showEditDialog(position);
        });

        // Quick delete note button click
        holder.btnDeleteNote.setOnClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("Delete Note")
                    .setMessage("Are you sure you want to delete this note?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        // Sync deletion
                        SyncManager.getInstance(context).deleteNote(note.getId(), note.getServerId(), new SyncManager.SyncCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                Log.d("QuickNoteAdapter", "Note deletion synced to server");
                            }

                            @Override
                            public void onError(String error) {
                                Log.e("QuickNoteAdapter", "Failed to sync note deletion: " + error);
                            }
                        });

                        notes.remove(position);
                        notifyItemRemoved(position);
                        notifyItemRangeChanged(position, notes.size());
                        QuickNotesWidgetProvider.updateWidget(context);
                        if (context instanceof MainActivity) {
                            ((MainActivity) context).refreshDashboardStats();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void showEditDialog(int position) {
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(notes.get(position).getText());

        new AlertDialog.Builder(context)
                .setTitle("Edit Note")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newText = input.getText().toString().trim();
                    if (!newText.isEmpty()) {
                        QuickNote note = notes.get(position);
                        note.setText(newText);
                        noteDbHelper.updateNote(note.getId(), newText, note.isCompleted());

                        // Sync update text to server
                        SyncManager.getInstance(context).uploadNote(note.getId(), newText, note.isCompleted(), note.getPosition(), note.getServerId(), new SyncManager.SyncCallback<Long>() {
                            @Override
                            public void onSuccess(Long result) {
                                Log.d("QuickNoteAdapter", "Note text edit synced to server");
                            }

                            @Override
                            public void onError(String error) {
                                Log.e("QuickNoteAdapter", "Failed to sync note text edit: " + error);
                            }
                        });

                        notifyItemChanged(position);
                        QuickNotesWidgetProvider.updateWidget(context);
                        if (context instanceof MainActivity) {
                            ((MainActivity) context).refreshDashboardStats();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(notes, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(notes, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);

        // Update positions in database
        for (int i = 0; i < notes.size(); i++) {
            QuickNote note = notes.get(i);
            note.setPosition(i);
            noteDbHelper.updateNotePosition(note.getId(), i);
        }
        QuickNotesWidgetProvider.updateWidget(context);
        if (context instanceof MainActivity) {
            ((MainActivity) context).refreshDashboardStats();
        }
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView noteText;
        CheckBox noteCheckBox;
        ImageView btnDeleteNote;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            noteText = itemView.findViewById(R.id.noteText);
            noteCheckBox = itemView.findViewById(R.id.noteCheckBox);
            btnDeleteNote = itemView.findViewById(R.id.btnDeleteNote);
        }
    }
}
