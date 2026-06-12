package com.example.reminder;

public class MonthlyPayment {
    private int id;
    private Long serverId;
    private String name;
    private boolean isCompleted;
    private long dueDateMillis;
    private String syncStatus;

    public MonthlyPayment(int id, String name, boolean isCompleted, long dueDateMillis) {
        this.id = id;
        this.name = name;
        this.isCompleted = isCompleted;
        this.dueDateMillis = dueDateMillis;
    }

    public MonthlyPayment(int id, Long serverId, String name, boolean isCompleted, long dueDateMillis, String syncStatus) {
        this.id = id;
        this.serverId = serverId;
        this.name = name;
        this.isCompleted = isCompleted;
        this.dueDateMillis = dueDateMillis;
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

    public String getName() {
        return name;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public long getDueDateMillis() {
        return dueDateMillis;
    }

    public long getDueDate() {
        return dueDateMillis;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public void setDueDateMillis(long dueDateMillis) {
        this.dueDateMillis = dueDateMillis;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }
}
