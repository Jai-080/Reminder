package com.example.remainder;

public class Reminder {
    private int id;
    private String text;
    private long timeMillis;

    public Reminder(int id, String text, long timeMillis) {
        this.id = id;
        this.text = text;
        this.timeMillis = timeMillis;
    }
    private boolean isExpired;
    public boolean isExpired() { return isExpired; }
    public void setExpired(boolean expired) { this.isExpired = expired; }


    public int getId() {
        return id;
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

    private long snoozedUntil = -1;

    public void setSnoozedUntil(long timeMillis) {
        this.snoozedUntil = timeMillis;
    }

    public long getSnoozedUntil() {
        return snoozedUntil;
    }

}

