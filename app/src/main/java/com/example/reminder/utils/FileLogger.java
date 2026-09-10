package com.example.reminder.utils;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Reusable, thread-safe authentication logging utility.
 * Writes append-only logs directly to /storage/emulated/0/log/auth_debug_log.txt.
 * Flushes immediately, never truncates, never crashes the app.
 */
public class FileLogger {
    private static final String TAG = "FileLogger";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static final Object FILE_LOCK = new Object();

    public static void checkAndRequestStoragePermission(android.app.Activity activity) {
        if (activity == null) return;
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                if (!android.os.Environment.isExternalStorageManager()) {
                    log("PERMISSIONS", "MANAGE_EXTERNAL_STORAGE not granted. Launching Settings to prompt user.");
                    try {
                        android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(android.net.Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(intent);
                        android.widget.Toast.makeText(activity, "Please allow 'All files access' for debug auth logging", android.widget.Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        try {
                            android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            activity.startActivity(intent);
                        } catch (Exception ignored) {}
                    }
                } else {
                    log("PERMISSIONS", "MANAGE_EXTERNAL_STORAGE is granted.");
                }
            } else {
                if (androidx.core.content.ContextCompat.checkSelfPermission(activity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    log("PERMISSIONS", "WRITE_EXTERNAL_STORAGE not granted. Requesting runtime permission.");
                    androidx.core.app.ActivityCompat.requestPermissions(
                            activity,
                            new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE},
                            1002
                    );
                } else {
                    log("PERMISSIONS", "Storage permission is granted.");
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "checkAndRequestStoragePermission failed: " + t.getMessage(), t);
        }
    }

    // Required path: /storage/emulated/0/log/auth_debug_log.txt
    private static final String DIR_PATH = "/storage/emulated/0/log";
    private static final String FILE_NAME = "auth_debug_log.txt";

    public static void log(String component, String message) {
        log(component, message, null);
    }

    public static void log(String component, String message, Throwable throwable) {
        try {
            String timestamp;
            synchronized (DATE_FORMAT) {
                timestamp = DATE_FORMAT.format(new Date());
            }

            String threadName = Thread.currentThread().getName();

            // Format: [YYYY-MM-DD HH:mm:ss.SSS] <ThreadName> COMPONENT : Message
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(timestamp).append("] ")
              .append(threadName).append(" ")
              .append(component).append(" : ")
              .append(message);

            if (throwable != null) {
                sb.append("\n");
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                throwable.printStackTrace(pw);
                sb.append(sw.toString());
            }

            String logEntry = sb.toString();

            // Also output to Logcat for real-time visibility
            if (throwable != null) {
                Log.e(component, message, throwable);
            } else {
                Log.d(component, message);
            }

            writeToFile(logEntry);
        } catch (Throwable t) {
            // Logger MUST NEVER crash the application
            Log.e(TAG, "FileLogger internal error: " + t.getMessage(), t);
        }
    }

    public static void logClearSession(String component, String reason) {
        try {
            String stackTrace = getCallStack(20);
            StringBuilder sb = new StringBuilder();
            sb.append("clearSession() called. Reason: ").append(reason)
              .append("\nCall stack:").append(stackTrace);
            log(component, sb.toString());
        } catch (Throwable t) {
            Log.e(TAG, "Failed logging clearSession: " + t.getMessage(), t);
        }
    }

    public static String getCallStack(int maxDepth) {
        try {
            StackTraceElement[] elements = Thread.currentThread().getStackTrace();
            StringBuilder sb = new StringBuilder();
            int count = 0;
            // Skip getStackTrace, getCallStack, and logger frames
            for (int i = 3; i < elements.length && count < maxDepth; i++) {
                sb.append("\n    at ").append(elements[i].toString());
                count++;
            }
            return sb.toString();
        } catch (Throwable t) {
            return "[unable to capture stack trace: " + t.getMessage() + "]";
        }
    }

    /**
     * Safely logs token metadata without EVER exposing raw token secrets or JWT strings.
     */
    public static String formatTokenMeta(String token) {
        if (token == null) {
            return "[exists=false]";
        }
        if (token.isEmpty()) {
            return "[exists=false, empty=true]";
        }
        return "[exists=true, length=" + token.length() + "]";
    }

    private static void writeToFile(String entry) {
        synchronized (FILE_LOCK) {
            try {
                File dir = new File(DIR_PATH);
                if (!dir.exists()) {
                    boolean created = dir.mkdirs();
                    if (!created && !dir.exists()) {
                        Log.w(TAG, "Could not create directory " + DIR_PATH + "; attempting fallback if available.");
                    }
                }

                File logFile = new File(dir, FILE_NAME);
                try (FileWriter writer = new FileWriter(logFile, true)) {
                    writer.write(entry);
                    writer.write("\n");
                    writer.flush();
                }
            } catch (Exception e) {
                // Try fallback with Environment.getExternalStorageDirectory()
                try {
                    File fallbackDir = new File(Environment.getExternalStorageDirectory(), "log");
                    if (!fallbackDir.exists()) {
                        fallbackDir.mkdirs();
                    }
                    File logFile = new File(fallbackDir, FILE_NAME);
                    try (FileWriter writer = new FileWriter(logFile, true)) {
                        writer.write(entry);
                        writer.write("\n");
                        writer.flush();
                    }
                } catch (Exception fallbackEx) {
                    // Gracefully log error to Logcat without crashing
                    Log.e(TAG, "FileLogger write failed to /storage/emulated/0/log/auth_debug_log.txt: " + e.getMessage(), fallbackEx);
                }
            }
        }
    }
}
