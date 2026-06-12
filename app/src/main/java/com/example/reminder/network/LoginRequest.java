package com.example.reminder.network;

public class LoginRequest {
    private String email;
    private String password;
    private String deviceName;
    private String platform;

    public LoginRequest(String email, String password, String deviceName, String platform) {
        this.email = email;
        this.password = password;
        this.deviceName = deviceName;
        this.platform = platform;
    }

    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getDeviceName() { return deviceName; }
    public String getPlatform() { return platform; }
}
