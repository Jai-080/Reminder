package com.example.remainder;

public class QuickNote {
    private int id;
    private String text;
    private boolean isCompleted;

    public QuickNote(int id, String text, boolean isCompleted) {
        this.id = id;
        this.text = text;
        this.isCompleted = isCompleted;
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
}
