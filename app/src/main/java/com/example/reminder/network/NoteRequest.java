package com.example.reminder.network;

public class NoteRequest {
    private String text;
    private Boolean isCompleted;
    private Integer position;
    private Long updatedAt;

    public NoteRequest(String text, Boolean isCompleted, Integer position) {
        this.text = text;
        this.isCompleted = isCompleted;
        this.position = position;
    }

    public NoteRequest(String text, Boolean isCompleted, Integer position, Long updatedAt) {
        this.text = text;
        this.isCompleted = isCompleted;
        this.position = position;
        this.updatedAt = updatedAt;
    }

    public String getText() { return text; }
    public Boolean getIsCompleted() { return isCompleted; }
    public Integer getPosition() { return position; }
    public Long getUpdatedAt() { return updatedAt; }
}
