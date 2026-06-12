package com.example.reminder.network;

public class ReminderRequest {
    private String text;
    private Long reminderTime;
    private Boolean isExpired;
    private Long snoozedTime;

    public ReminderRequest(String text, Long reminderTime, Boolean isExpired, Long snoozedTime) {
        this.text = text;
        this.reminderTime = reminderTime;
        this.isExpired = isExpired;
        this.snoozedTime = snoozedTime;
    }

    public String getText() { return text; }
    public Long getReminderTime() { return reminderTime; }
    public Boolean getIsExpired() { return isExpired; }
    public Long getSnoozedTime() { return snoozedTime; }
}
