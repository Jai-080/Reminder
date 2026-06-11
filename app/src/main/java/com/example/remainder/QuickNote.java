package com.example.remainder;

public class QuickNote {
    private int id;
    private String text;
    private boolean isCompleted;
    private int position;

    public QuickNote(int id, String text, boolean isCompleted, int position) {
        this.id = id;
        this.text = text;
        this.isCompleted = isCompleted;
        this.position = position;
    }

    // Getter for id
    public int getId() {
        return id;
    }

    // Getter and setter for text
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    // Getter and setter for isCompleted
    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
