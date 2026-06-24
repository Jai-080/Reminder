package com.example.reminder.network;

public class PaymentRequest {
    private String name;
    private Long dueDate;
    private Boolean completed;
    private Long updatedAt;
    private Double amount;
    private String recurrence;
    private String notificationOffsets;
    private Long lastPaidAt;

    public PaymentRequest(String name, Long dueDate, Boolean completed) {
        this.name = name;
        this.dueDate = dueDate;
        this.completed = completed;
        this.amount = null;
        this.recurrence = "MONTHLY";
        this.notificationOffsets = "0";
    }

    public PaymentRequest(String name, Long dueDate, Boolean completed, Long updatedAt) {
        this.name = name;
        this.dueDate = dueDate;
        this.completed = completed;
        this.updatedAt = updatedAt;
        this.amount = null;
        this.recurrence = "MONTHLY";
        this.notificationOffsets = "0";
    }

    public PaymentRequest(String name, Long dueDate, Boolean completed, Long updatedAt, Double amount) {
        this.name = name;
        this.dueDate = dueDate;
        this.completed = completed;
        this.updatedAt = updatedAt;
        this.amount = amount;
        this.recurrence = "MONTHLY";
        this.notificationOffsets = "0";
    }

    public PaymentRequest(String name, Long dueDate, Boolean completed, Long updatedAt, Double amount, String recurrence) {
        this.name = name;
        this.dueDate = dueDate;
        this.completed = completed;
        this.updatedAt = updatedAt;
        this.amount = amount;
        this.recurrence = recurrence;
        this.notificationOffsets = "0";
    }

    public PaymentRequest(String name, Long dueDate, Boolean completed, Long updatedAt, Double amount, String recurrence, String notificationOffsets) {
        this.name = name;
        this.dueDate = dueDate;
        this.completed = completed;
        this.updatedAt = updatedAt;
        this.amount = amount;
        this.recurrence = recurrence;
        this.notificationOffsets = notificationOffsets;
    }

    public String getName() { return name; }
    public Long getDueDate() { return dueDate; }
    public Boolean getCompleted() { return completed; }
    public Long getUpdatedAt() { return updatedAt; }
    public Double getAmount() { return amount; }
    public String getRecurrence() { return recurrence; }
    public String getNotificationOffsets() { return notificationOffsets; }
    public Long getLastPaidAt() { return lastPaidAt; }
    public void setLastPaidAt(Long lastPaidAt) { this.lastPaidAt = lastPaidAt; }
}
