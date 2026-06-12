package com.example.reminder.network;

public class ReminderResponse {
    private Long id;
    private String text;
    private Long reminderTime;
    private Boolean isExpired;
    private Long snoozedTime;
    private String createdAt;
    private String updatedAt;

    public Long getId() { return id; }
    public String getText() { return text; }
    public Long getReminderTime() { return reminderTime; }
    public Boolean getIsExpired() { return isExpired; }
    public Long getSnoozedTime() { return snoozedTime; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
}
