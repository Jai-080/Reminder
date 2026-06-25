package com.example.reminder.utils;

import android.os.Build;

public final class DeviceUtils {
    private DeviceUtils() {}

    public static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        
        if (manufacturer == null) manufacturer = "";
        if (model == null) model = "";
        
        String name;
        if (model.toLowerCase().startsWith(manufacturer.toLowerCase())) {
            name = model;
        } else {
            name = manufacturer + " " + model;
        }
        
        name = name.trim();
        if (name.isEmpty()) {
            return "Android-Device";
        }
        return name;
    }
}
