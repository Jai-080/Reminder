package com.example.remainder;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
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

        if (note.isCompleted()) {
            holder.noteText.setPaintFlags(holder.noteText.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            holder.noteText.setPaintFlags(holder.noteText.getPaintFlags() & (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
        }

        // Toggle completion on bullet icon click
        holder.bullet.setOnClickListener(v -> {
            note.setCompleted(!note.isCompleted());
            noteDbHelper.updateNote(note.getId(), note.getText(), note.isCompleted());

            // Move to bottom if completed
            notes.remove(position);
            notes.add(note);
            notifyDataSetChanged();
        });

        // Tap to edit
        holder.noteText.setOnClickListener(v -> showEditDialog(position));

        // Long press for edit/delete
        holder.noteText.setOnLongClickListener(v -> {
            String[] options = {"Edit", "Delete"};
            new AlertDialog.Builder(context)
                    .setTitle("Choose Action")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) { // Edit
                            showEditDialog(position);
                        } else { // Delete
                            noteDbHelper.deleteNote(note.getId());
                            notes.remove(position);
                            notifyItemRemoved(position);
                        }
                    })
                    .show();
            return true;
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
        ImageView bullet;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            noteText = itemView.findViewById(R.id.noteText);
            bullet = itemView.findViewById(R.id.bulletIcon);
        }
    }
}
