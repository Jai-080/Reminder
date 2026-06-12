package com.example.reminder;

public class Reminder {
    private int id;
    private Long serverId;
    private String text;
    private long timeMillis;
    private boolean isExpired;
    private long snoozedUntil = -1;
    private String syncStatus;

    public Reminder(int id, String text, long timeMillis) {
        this.id = id;
        this.text = text;
        this.timeMillis = timeMillis;
    }

    public Reminder(int id, Long serverId, String text, long timeMillis, boolean isExpired, long snoozedUntil, String syncStatus) {
        this.id = id;
        this.serverId = serverId;
        this.text = text;
        this.timeMillis = timeMillis;
        this.isExpired = isExpired;
        this.snoozedUntil = snoozedUntil;
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

    public long getTime() {
        return timeMillis;
    }

    public long getTimeMillis() {
        return timeMillis;
    }

    public boolean isExpired() {
        return isExpired;
    }

    public void setExpired(boolean expired) {
        this.isExpired = expired;
    }

    public void setSnoozedUntil(long timeMillis) {
        this.snoozedUntil = timeMillis;
    }

    public long getSnoozedUntil() {
        return snoozedUntil;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }
}
