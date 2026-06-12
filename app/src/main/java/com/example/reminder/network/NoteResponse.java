package com.example.reminder.network;

public class NoteResponse {
    private Long id;
    private String text;
    private Boolean isCompleted;
    private Integer position;
    private String createdAt;
    private String updatedAt;

    public Long getId() { return id; }
    public String getText() { return text; }
    public Boolean getIsCompleted() { return isCompleted; }
    public Integer getPosition() { return position; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
}
