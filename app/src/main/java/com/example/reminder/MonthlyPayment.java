package com.example.reminder;

public class MonthlyPayment {
    private int id;
    private Long serverId;
    private String name;
    private boolean isCompleted;
    private long dueDateMillis;
    private String syncStatus;
    private Double amount;
    private RecurrenceType recurrence;
    private String notificationOffsets;
    private Long lastPaidAt;

    public MonthlyPayment(int id, String name, boolean isCompleted, long dueDateMillis) {
        this.id = id;
        this.name = name;
        this.isCompleted = isCompleted;
        this.dueDateMillis = dueDateMillis;
        this.amount = null;
        this.recurrence = RecurrenceType.MONTHLY;
        this.notificationOffsets = "0";
    }

    public MonthlyPayment(int id, Long serverId, String name, boolean isCompleted, long dueDateMillis, String syncStatus) {
        this.id = id;
        this.serverId = serverId;
        this.name = name;
        this.isCompleted = isCompleted;
        this.dueDateMillis = dueDateMillis;
        this.syncStatus = syncStatus;
        this.amount = null;
        this.recurrence = RecurrenceType.MONTHLY;
        this.notificationOffsets = "0";
    }

    public MonthlyPayment(int id, Long serverId, String name, boolean isCompleted, long dueDateMillis, String syncStatus, Double amount) {
        this.id = id;
        this.serverId = serverId;
        this.name = name;
        this.isCompleted = isCompleted;
        this.dueDateMillis = dueDateMillis;
        this.syncStatus = syncStatus;
        this.amount = amount;
        this.recurrence = RecurrenceType.MONTHLY;
        this.notificationOffsets = "0";
    }

    public MonthlyPayment(int id, Long serverId, String name, boolean isCompleted, long dueDateMillis, String syncStatus, Double amount, RecurrenceType recurrence) {
        this.id = id;
        this.serverId = serverId;
        this.name = name;
        this.isCompleted = isCompleted;
        this.dueDateMillis = dueDateMillis;
        this.syncStatus = syncStatus;
        this.amount = amount;
        this.recurrence = recurrence;
        this.notificationOffsets = "0";
    }

    public MonthlyPayment(int id, Long serverId, String name, boolean isCompleted, long dueDateMillis, String syncStatus, Double amount, RecurrenceType recurrence, String notificationOffsets) {
        this.id = id;
        this.serverId = serverId;
        this.name = name;
        this.isCompleted = isCompleted;
        this.dueDateMillis = dueDateMillis;
        this.syncStatus = syncStatus;
        this.amount = amount;
        this.recurrence = recurrence;
        this.notificationOffsets = notificationOffsets;
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

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public RecurrenceType getRecurrence() {
        return recurrence;
    }

    public void setRecurrence(RecurrenceType recurrence) {
        this.recurrence = recurrence;
    }

    public String getNotificationOffsets() {
        return notificationOffsets;
    }

    public void setNotificationOffsets(String notificationOffsets) {
        this.notificationOffsets = notificationOffsets;
    }

    public Long getLastPaidAt() {
        if (lastPaidAt == null && isCompleted) {
            return System.currentTimeMillis();
        }
        return lastPaidAt;
    }

    public void setLastPaidAt(Long lastPaidAt) {
        this.lastPaidAt = lastPaidAt;
    }

    public boolean isRecentlyPaid(int currentMonth, int currentYear) {
        Long lpa = getLastPaidAt();
        if (lpa == null) return false;
        if (recurrence == RecurrenceType.ONE_TIME) {
            return true;
        }
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(lpa);
        int paidMonth = cal.get(java.util.Calendar.MONTH) + 1;
        int paidYear = cal.get(java.util.Calendar.YEAR);
        return paidMonth == currentMonth && paidYear == currentYear;
    }

    public boolean isRecentlyPaid() {
        java.util.Calendar now = java.util.Calendar.getInstance();
        int currentMonth = now.get(java.util.Calendar.MONTH) + 1;
        int currentYear = now.get(java.util.Calendar.YEAR);
        return isRecentlyPaid(currentMonth, currentYear);
    }

    public boolean isUpcoming(int currentMonth, int currentYear) {
        if (isRecentlyPaid(currentMonth, currentYear)) {
            return false;
        }
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(dueDateMillis);
        int dueMonth = cal.get(java.util.Calendar.MONTH) + 1;
        int dueYear = cal.get(java.util.Calendar.YEAR);
        return (dueYear < currentYear) || (dueYear == currentYear && dueMonth <= currentMonth);
    }

    public boolean isDueLater(int currentMonth, int currentYear) {
        if (isRecentlyPaid(currentMonth, currentYear)) {
            return false;
        }
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(dueDateMillis);
        int dueMonth = cal.get(java.util.Calendar.MONTH) + 1;
        int dueYear = cal.get(java.util.Calendar.YEAR);
        return (dueYear > currentYear) || (dueYear == currentYear && dueMonth > currentMonth);
    }
}
