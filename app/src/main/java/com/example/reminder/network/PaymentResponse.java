package com.example.reminder.network;

public class PaymentResponse {
    private Long id;
    private String name;
    private Long dueDate;
    private Boolean completed;
    private String createdAt;
    private String updatedAt;

    public Long getId() { return id; }
    public String getName() { return name; }
    public Long getDueDate() { return dueDate; }
    public Boolean getCompleted() { return completed; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
}
