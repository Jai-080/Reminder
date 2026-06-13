package com.example.reminder.network;

public class PaymentRequest {
    private String name;
    private Long dueDate;
    private Boolean completed;
    private Long updatedAt;

    public PaymentRequest(String name, Long dueDate, Boolean completed) {
        this.name = name;
        this.dueDate = dueDate;
        this.completed = completed;
    }

    public PaymentRequest(String name, Long dueDate, Boolean completed, Long updatedAt) {
        this.name = name;
        this.dueDate = dueDate;
        this.completed = completed;
        this.updatedAt = updatedAt;
    }

    public String getName() { return name; }
    public Long getDueDate() { return dueDate; }
    public Boolean getCompleted() { return completed; }
    public Long getUpdatedAt() { return updatedAt; }
}
