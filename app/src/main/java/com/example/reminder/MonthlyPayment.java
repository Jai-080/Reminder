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
    private int originalDueDay;
    private int customIntervalDays;

    public MonthlyPayment(int id, String name, boolean isCompleted, long dueDateMillis) {
        this.id = id;
        this.name = name;
        this.isCompleted = isCompleted;
        this.dueDateMillis = dueDateMillis;
        this.amount = null;
        this.recurrence = RecurrenceType.MONTHLY;
        this.notificationOffsets = "0";
        this.customIntervalDays = 28;
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
        this.customIntervalDays = 28;
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
        this.customIntervalDays = 28;
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
        this.customIntervalDays = 28;
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
        this.customIntervalDays = 28;
    }

    public MonthlyPayment(int id, Long serverId, String name, boolean isCompleted, long dueDateMillis, String syncStatus, Double amount, RecurrenceType recurrence, String notificationOffsets, int originalDueDay) {
        this(id, serverId, name, isCompleted, dueDateMillis, syncStatus, amount, recurrence, notificationOffsets);
        this.originalDueDay = originalDueDay;
        this.customIntervalDays = 28;
    }

    public MonthlyPayment(int id, Long serverId, String name, boolean isCompleted, long dueDateMillis, String syncStatus, Double amount, RecurrenceType recurrence, String notificationOffsets, int originalDueDay, int customIntervalDays) {
        this(id, serverId, name, isCompleted, dueDateMillis, syncStatus, amount, recurrence, notificationOffsets, originalDueDay);
        this.customIntervalDays = customIntervalDays > 0 ? customIntervalDays : 28;
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

    public int getOriginalDueDay() {
        if (originalDueDay <= 0 && dueDateMillis > 0) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(dueDateMillis);
            originalDueDay = cal.get(java.util.Calendar.DAY_OF_MONTH);
        }
        return originalDueDay;
    }

    public void setOriginalDueDay(int originalDueDay) {
        this.originalDueDay = originalDueDay;
    }

    public int getCustomIntervalDays() {
        return customIntervalDays;
    }

    public void setCustomIntervalDays(int customIntervalDays) {
        this.customIntervalDays = customIntervalDays;
    }

    public static long calculateImmediateNextDueDate(long currentDueDateMillis, RecurrenceType recurrence, int originalDueDay) {
        return calculateNextDueDate(currentDueDateMillis, recurrence, originalDueDay, 28, Long.MIN_VALUE);
    }

    public static long calculateImmediateNextDueDate(long currentDueDateMillis, RecurrenceType recurrence, int originalDueDay, int customIntervalDays) {
        return calculateNextDueDate(currentDueDateMillis, recurrence, originalDueDay, customIntervalDays, Long.MIN_VALUE);
    }

    public static long calculateNextDueDate(long currentDueDateMillis, RecurrenceType recurrence, int originalDueDay) {
        return calculateNextDueDate(currentDueDateMillis, recurrence, originalDueDay, 28, System.currentTimeMillis());
    }

    public static long calculateNextDueDate(long currentDueDateMillis, RecurrenceType recurrence, int originalDueDay, int customIntervalDays) {
        return calculateNextDueDate(currentDueDateMillis, recurrence, originalDueDay, customIntervalDays, System.currentTimeMillis());
    }

    public static long calculateNextDueDate(long currentDueDateMillis, RecurrenceType recurrence, int originalDueDay, long referenceTimeMillis) {
        return calculateNextDueDate(currentDueDateMillis, recurrence, originalDueDay, 28, referenceTimeMillis);
    }

    public static long calculateNextDueDate(long currentDueDateMillis, RecurrenceType recurrence, int originalDueDay, int customIntervalDays, long referenceTimeMillis) {
        if (recurrence == null || recurrence == RecurrenceType.ONE_TIME || recurrence == RecurrenceType.UNKNOWN) {
            return currentDueDateMillis;
        }

        if (recurrence == RecurrenceType.CUSTOM_DAYS) {
            int interval = customIntervalDays;
            if (interval <= 0 || interval > 365) {
                interval = 28;
            }

            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(currentDueDateMillis);

            int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
            int minute = cal.get(java.util.Calendar.MINUTE);
            int second = cal.get(java.util.Calendar.SECOND);
            int millis = cal.get(java.util.Calendar.MILLISECOND);

            int iterations = 0;
            do {
                cal.add(java.util.Calendar.DATE, interval);
                cal.set(java.util.Calendar.HOUR_OF_DAY, hour);
                cal.set(java.util.Calendar.MINUTE, minute);
                cal.set(java.util.Calendar.SECOND, second);
                cal.set(java.util.Calendar.MILLISECOND, millis);
                iterations++;
            } while (cal.getTimeInMillis() <= referenceTimeMillis && iterations < 1000);

            return cal.getTimeInMillis();
        }

        int stepMonths;
        switch (recurrence) {
            case BI_MONTHLY:
                stepMonths = 2;
                break;
            case QUARTERLY:
                stepMonths = 3;
                break;
            case HALF_YEARLY:
                stepMonths = 6;
                break;
            case YEARLY:
                stepMonths = 12;
                break;
            case MONTHLY:
            default:
                stepMonths = 1;
                break;
        }

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(currentDueDateMillis);

        int targetDay = originalDueDay;
        if (targetDay <= 0 || targetDay > 31) {
            targetDay = cal.get(java.util.Calendar.DAY_OF_MONTH);
        }

        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        int minute = cal.get(java.util.Calendar.MINUTE);
        int second = cal.get(java.util.Calendar.SECOND);
        int millis = cal.get(java.util.Calendar.MILLISECOND);

        int iterations = 0;
        do {
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
            cal.add(java.util.Calendar.MONTH, stepMonths);
            int maxDays = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);
            int clampedDay = Math.min(targetDay, maxDays);
            cal.set(java.util.Calendar.DAY_OF_MONTH, clampedDay);
            cal.set(java.util.Calendar.HOUR_OF_DAY, hour);
            cal.set(java.util.Calendar.MINUTE, minute);
            cal.set(java.util.Calendar.SECOND, second);
            cal.set(java.util.Calendar.MILLISECOND, millis);
            iterations++;
        } while (cal.getTimeInMillis() <= referenceTimeMillis && iterations < 120);

        return cal.getTimeInMillis();
    }

    public Long getLastPaidAt() {
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
        if (recurrence == RecurrenceType.CUSTOM_DAYS) {
            if (dueDateMillis <= System.currentTimeMillis()) {
                return false;
            }
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
