package com.example.reminder.network;

public class PaymentRequest {
    private String name;
    private Long dueDate;
    private Boolean completed;

    public PaymentRequest(String name, Long dueDate, Boolean completed) {
        this.name = name;
        this.dueDate = dueDate;
        this.completed = completed;
    }

    public String getName() { return name; }
    public Long getDueDate() { return dueDate; }
    public Boolean getCompleted() { return completed; }
}
