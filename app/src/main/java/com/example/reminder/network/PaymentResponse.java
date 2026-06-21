package com.example.reminder.network;

public class PaymentResponse {
    private Long id;
    private String name;
    private Long dueDate;
    private Boolean completed;
    private Double amount;
    private String recurrence;
    private String createdAt;
    private String updatedAt;

    public Long getId() { return id; }
    public String getName() { return name; }
    public Long getDueDate() { return dueDate; }
    public Boolean getCompleted() { return completed; }
    public Double getAmount() { return amount; }
    public String getRecurrence() { return recurrence; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
}
