package com.example.reminder.network;

public class NoteRequest {
    private String text;
    private Boolean isCompleted;
    private Integer position;

    public NoteRequest(String text, Boolean isCompleted, Integer position) {
        this.text = text;
        this.isCompleted = isCompleted;
        this.position = position;
    }

    public String getText() { return text; }
    public Boolean getIsCompleted() { return isCompleted; }
    public Integer getPosition() { return position; }
}
