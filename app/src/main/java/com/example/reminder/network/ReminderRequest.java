package com.example.reminder.network;

public class ReminderRequest {
    private String text;
    private Long reminderTime;
    private Boolean isExpired;
    private Long snoozedTime;
    private Long updatedAt;

    public ReminderRequest(String text, Long reminderTime, Boolean isExpired, Long snoozedTime) {
        this.text = text;
        this.reminderTime = reminderTime;
        this.isExpired = isExpired;
        this.snoozedTime = snoozedTime;
    }

    public ReminderRequest(String text, Long reminderTime, Boolean isExpired, Long snoozedTime, Long updatedAt) {
        this.text = text;
        this.reminderTime = reminderTime;
        this.isExpired = isExpired;
        this.snoozedTime = snoozedTime;
        this.updatedAt = updatedAt;
    }

    public String getText() { return text; }
    public Long getReminderTime() { return reminderTime; }
    public Boolean getIsExpired() { return isExpired; }
    public Long getSnoozedTime() { return snoozedTime; }
    public Long getUpdatedAt() { return updatedAt; }
}
