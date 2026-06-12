package com.example.reminder;

public class QuickNote {
    private int id;
    private Long serverId;
    private String text;
    private boolean isCompleted;
    private int position;
    private String syncStatus;

    public QuickNote(int id, String text, boolean isCompleted, int position) {
        this.id = id;
        this.text = text;
        this.isCompleted = isCompleted;
        this.position = position;
    }

    public QuickNote(int id, Long serverId, String text, boolean isCompleted, int position, String syncStatus) {
        this.id = id;
        this.serverId = serverId;
        this.text = text;
        this.isCompleted = isCompleted;
        this.position = position;
        this.syncStatus = syncStatus;
    }

    public int getId() {
        return id;
    }

    public Long getServerId() {
        return serverId;
    }

    public void setServerId(Long serverId) {
        this.serverId = serverId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

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

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }
}
