package com.example.remainder;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

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

        // Strikethrough if completed
        if (note.isCompleted()) {
            holder.noteText.setPaintFlags(holder.noteText.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            holder.noteText.setTextColor(0xFF555555);
        } else {
            holder.noteText.setPaintFlags(holder.noteText.getPaintFlags() & (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
            holder.noteText.setTextColor(0xFFBBBBBB);
        }

        // ✅ Toggle completion on the whole item row click
        holder.itemView.setOnLongClickListener(v -> {
            note.setCompleted(!note.isCompleted());
            noteDbHelper.updateNote(note.getId(), note.getText(), note.isCompleted());
            notes.remove(position);
            notes.add(note);
            notifyDataSetChanged();
            QuickNotesWidgetProvider.updateWidget(context);
            return true;
        });

        // Single tap shows Edit/Delete options
        holder.noteText.setOnClickListener(v -> {
            String[] options = {"Edit", "Delete"};
            new AlertDialog.Builder(context)
                    .setTitle("Choose Action")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            showEditDialog(position);
                        } else {
                            noteDbHelper.deleteNote(note.getId());
                            notes.remove(position);
                            notifyItemRemoved(position);
                            QuickNotesWidgetProvider.updateWidget(context);
                        }
                    })
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
                        notifyItemChanged(position);
                        QuickNotesWidgetProvider.updateWidget(context);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView noteText;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            noteText = itemView.findViewById(R.id.noteText);
            // ✅ bulletIcon removed — dot is now a decorative View, no ID needed
        }
    }
}