package com.example.reminder.network;

public class PaymentRequest {
    private String name;
    private Long dueDate;
    private Boolean completed;
    private Long updatedAt;
    private Double amount;
    private String recurrence;

    public PaymentRequest(String name, Long dueDate, Boolean completed) {
        this.name = name;
        this.dueDate = dueDate;
        this.completed = completed;
        this.amount = null;
        this.recurrence = "MONTHLY";
    }

    public PaymentRequest(String name, Long dueDate, Boolean completed, Long updatedAt) {
        this.name = name;
        this.dueDate = dueDate;
        this.completed = completed;
        this.updatedAt = updatedAt;
        this.amount = null;
        this.recurrence = "MONTHLY";
    }

    public PaymentRequest(String name, Long dueDate, Boolean completed, Long updatedAt, Double amount) {
        this.name = name;
        this.dueDate = dueDate;
        this.completed = completed;
        this.updatedAt = updatedAt;
        this.amount = amount;
        this.recurrence = "MONTHLY";
    }

    public PaymentRequest(String name, Long dueDate, Boolean completed, Long updatedAt, Double amount, String recurrence) {
        this.name = name;
        this.dueDate = dueDate;
        this.completed = completed;
        this.updatedAt = updatedAt;
        this.amount = amount;
        this.recurrence = recurrence;
    }

    public String getName() { return name; }
    public Long getDueDate() { return dueDate; }
    public Boolean getCompleted() { return completed; }
    public Long getUpdatedAt() { return updatedAt; }
    public Double getAmount() { return amount; }
    public String getRecurrence() { return recurrence; }
}
