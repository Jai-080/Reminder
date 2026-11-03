package com.example.reminder.network;

public class PaymentResponse {
    private Long id;
    private String name;
    private Long dueDate;
    private Boolean completed;
    private Double amount;
    private String recurrence;
    private String notificationOffsets;
    private String createdAt;
    private String updatedAt;
    private Long lastPaidAt;
    private Integer originalDueDay;
    private Integer customIntervalDays;

    public Long getId() { return id; }
    public String getName() { return name; }
    public Long getDueDate() { return dueDate; }
    public Boolean getCompleted() { return completed; }
    public Double getAmount() { return amount; }
    public String getRecurrence() { return recurrence; }
    public String getNotificationOffsets() { return notificationOffsets; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public Long getLastPaidAt() { return lastPaidAt; }
    public void setLastPaidAt(Long lastPaidAt) { this.lastPaidAt = lastPaidAt; }
    public Integer getOriginalDueDay() { return originalDueDay; }
    public void setOriginalDueDay(Integer originalDueDay) { this.originalDueDay = originalDueDay; }
    public Integer getCustomIntervalDays() { return customIntervalDays; }
    public void setCustomIntervalDays(Integer customIntervalDays) { this.customIntervalDays = customIntervalDays; }
}
