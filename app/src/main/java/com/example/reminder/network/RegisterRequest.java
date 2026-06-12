package com.example.reminder.network;

public class RegisterRequest {
    private String username;
    private String email;
    private String password;
    private String deviceName;
    private String platform;

    public RegisterRequest(String username, String email, String password, String deviceName, String platform) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.deviceName = deviceName;
        this.platform = platform;
    }

    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getDeviceName() { return deviceName; }
    public String getPlatform() { return platform; }
}
