package com.example.reminder;

import com.example.reminder.sync.SyncManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.util.Log;

import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.Toast;

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

            // Smoothly animate the note sliding to its sorted position
            int oldPos = notes.indexOf(note);
            if (oldPos != -1) {
                notes.remove(oldPos);

                int newPos = 0;
                for (int k = 0; k < notes.size(); k++) {
                    QuickNote other = notes.get(k);
                    if (note.isCompleted() == other.isCompleted()) {
                        if (note.getPosition() > other.getPosition()) {
                            newPos = k + 1;
                        } else {
                            break;
                        }
                    } else if (!note.isCompleted() && other.isCompleted()) {
                        break;
                    } else {
                        newPos = k + 1;
                    }
                }
                notes.add(newPos, note);

                if (oldPos != newPos) {
                    notifyItemMoved(oldPos, newPos);
                    notifyItemChanged(newPos);
                } else {
                    notifyItemChanged(oldPos);
                }
            }

            QuickNotesWidgetProvider.updateWidget(context);
            if (context instanceof MainActivity) {
                ((MainActivity) context).refreshDashboardStats();
            }
        };

        // Only checking the checkbox toggles completion state
        holder.noteCheckBox.setOnClickListener(toggleListener);

        // Clicking the text, container or card opens the Edit Note dialog
        View.OnClickListener editClickListener = v -> {
            showEditDialog(holder.getAdapterPosition());
        };
        holder.itemView.setOnClickListener(editClickListener);
        if (holder.contentContainer != null) {
            holder.contentContainer.setOnClickListener(editClickListener);
        }
        holder.noteText.setOnClickListener(editClickListener);

        // Long press anywhere on the note copies text to clipboard and shows Toast
        View.OnLongClickListener copyLongClickListener = v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && pos < notes.size()) {
                QuickNote noteToCopy = notes.get(pos);
                if (noteToCopy != null && noteToCopy.getText() != null) {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        android.content.ClipData clip = android.content.ClipData.newPlainText("Quick Note", noteToCopy.getText());
                        clipboard.setPrimaryClip(clip);
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    }
                }
            }
            return true;
        };
        holder.itemView.setOnLongClickListener(copyLongClickListener);
        if (holder.contentContainer != null) {
            holder.contentContainer.setOnLongClickListener(copyLongClickListener);
        }
        holder.noteText.setOnLongClickListener(copyLongClickListener);
        holder.noteCheckBox.setOnLongClickListener(copyLongClickListener);

        // Quick delete note button click with Material 3 confirmation dialog
        holder.btnDeleteNote.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION || pos >= notes.size()) return;
            QuickNote noteToDelete = notes.get(pos);

            new MaterialAlertDialogBuilder(context)
                    .setTitle("Delete Note")
                    .setMessage("Are you sure you want to delete this note?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        int currentPos = holder.getAdapterPosition();
                        if (currentPos == RecyclerView.NO_POSITION || currentPos >= notes.size()) return;

                        // Sync deletion
                        SyncManager.getInstance(context).deleteNote(noteToDelete.getId(), noteToDelete.getServerId(), new SyncManager.SyncCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                Log.d("QuickNoteAdapter", "Note deletion synced to server");
                            }

                            @Override
                            public void onError(String error) {
                                Log.e("QuickNoteAdapter", "Failed to sync note deletion: " + error);
                            }
                        });

                        notes.remove(currentPos);
                        notifyItemRemoved(currentPos);
                        notifyItemRangeChanged(currentPos, notes.size());
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
        if (position == RecyclerView.NO_POSITION || position >= notes.size()) return;
        QuickNote note = notes.get(position);

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_quick_note, null);
        com.google.android.material.textfield.TextInputEditText input = dialogView.findViewById(R.id.dialogNoteTextInput);
        input.setText(note.getText());
        if (input.getText() != null) {
            input.setSelection(input.getText().length());
        }

        androidx.appcompat.app.AlertDialog editDialog = new MaterialAlertDialogBuilder(context)
                .setTitle("Edit Note")
                .setView(dialogView)
                .setPositiveButton("Save", (dialogInterface, which) -> {
                    String newText = input.getText().toString().trim();
                    if (!newText.isEmpty()) {
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
                .create();

        editDialog.setOnShowListener(d -> {
            input.requestFocus();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        });

        if (editDialog.getWindow() != null) {
            editDialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        editDialog.show();
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
        View contentContainer;
        TextView noteText;
        CheckBox noteCheckBox;
        ImageView btnDeleteNote;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            contentContainer = itemView.findViewById(R.id.noteContentContainer);
            noteText = itemView.findViewById(R.id.noteText);
            noteCheckBox = itemView.findViewById(R.id.noteCheckBox);
            btnDeleteNote = itemView.findViewById(R.id.btnDeleteNote);
        }
    }
}
