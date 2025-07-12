package com.example.remainder;

public class MonthlyPayment {
    private int id;
    private String name;
    private boolean isCompleted;
    private long dueDateMillis; // Timestamp for the due date

    public MonthlyPayment(int id, String name, boolean isCompleted, long dueDateMillis) {
        this.id = id;
        this.name = name;
        this.isCompleted = isCompleted;
        this.dueDateMillis = dueDateMillis;
    }

    public int getId() {
        return id;
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

    // Alias for compatibility with adapter code
    public long getDueDate() {
        return dueDateMillis;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public void setDueDateMillis(long dueDateMillis) {
        this.dueDateMillis = dueDateMillis;
    }

    // Optional setter for consistency
    public void setName(String name) {
        this.name = name;
    }

    public void setId(int id) {
        this.id = id;
    }
}
