package com.example.reminder.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.example.reminder.ReminderApplication;
import com.example.reminder.auth.TokenManager;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebSocketManager {
    private static final String TAG = "WebSocketManager";
    private static WebSocketManager instance;

    private final Context context;
    private final TokenManager tokenManager;
    private final Handler handler;
    private final Random random;

    private WebSocket webSocket;
    private boolean isConnecting = false;
    private boolean isConnected = false;
    private boolean userWantsConnection = false;
    private int reconnectAttempts = 0;

    private WebSocketManager(Context context) {
        this.context = context.getApplicationContext();
        this.tokenManager = TokenManager.getInstance(context);
        this.handler = new Handler(Looper.getMainLooper());
        this.random = new Random();
    }

    public static synchronized WebSocketManager getInstance(Context context) {
        if (instance == null) {
            instance = new WebSocketManager(context);
        }
        return instance;
    }

    public synchronized void connect() {
        if (!tokenManager.isLoggedIn()) {
            Log.d(TAG, "Cannot connect WebSocket: User not logged in.");
            return;
        }

        userWantsConnection = true;

        if (isConnected || isConnecting) {
            Log.d(TAG, "WebSocket is already connected or connecting.");
            return;
        }

        isConnecting = true;
        reconnectAttempts = 0;
        executeConnect();
    }

    private synchronized void executeConnect() {
        String token = tokenManager.getAccessToken();
        if (token == null) {
            Log.e(TAG, "Cannot connect: accessToken is null");
            isConnecting = false;
            return;
        }

        // Format WebSocket URL from Base URL
        String baseUrl = tokenManager.getBaseUrl();
        String wsUrl = baseUrl.replace("http://", "ws://").replace("https://", "wss://");
        if (!wsUrl.endsWith("/")) {
            wsUrl += "/";
        }
        wsUrl += "ws?token=" + token;

        Log.d(TAG, "Connecting to WebSocket at: " + wsUrl);

        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();

        Request request = new Request.Builder()
                .url(wsUrl)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                Log.d(TAG, "WebSocket transport opened. Sending STOMP CONNECT frame.");
                String connectFrame = "CONNECT\n" +
                        "accept-version:1.1,1.2\n" +
                        "heart-beat:0,0\n" +
                        "Authorization:Bearer " + token + "\n" +
                        "\n" +
                        "\u0000";
                ws.send(connectFrame);
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                handleStompFrame(text);
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                Log.d(TAG, "WebSocket closing: " + reason);
                ws.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                Log.d(TAG, "WebSocket closed: " + reason);
                synchronized (WebSocketManager.this) {
                    isConnected = false;
                    isConnecting = false;
                }
                triggerReconnectIfNeeded();
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                Log.e(TAG, "WebSocket failure: " + t.getMessage(), t);
                synchronized (WebSocketManager.this) {
                    isConnected = false;
                    isConnecting = false;
                }
                triggerReconnectIfNeeded();
            }
        });
    }

    private synchronized void handleStompFrame(String frameText) {
        if (frameText == null || frameText.isEmpty()) return;

        String[] lines = frameText.split("\n");
        if (lines.length == 0) return;

        String command = lines[0].trim();
        Log.d(TAG, "Received STOMP command: " + command);

        if ("CONNECTED".equals(command)) {
            synchronized (this) {
                isConnected = true;
                isConnecting = false;
                reconnectAttempts = 0;
            }
            Log.d(TAG, "STOMP Connection established. Subscribing to user sync channel.");
            String subscribeFrame = "SUBSCRIBE\n" +
                    "id:sub-0\n" +
                    "destination:/user/topic/sync\n" +
                    "\n" +
                    "\u0000";
            if (webSocket != null) {
                webSocket.send(subscribeFrame);
            }

            // Phase 13 Startup Recovery: trigger immediate sync after CONNECTED
            Log.d(TAG, "WebSocket connected successfully. Enqueuing recovery sync.");
            handler.post(() -> ReminderApplication.enqueueSyncWorker(context));

        } else if ("MESSAGE".equals(command)) {
            int bodyStartIndex = -1;
            for (int i = 1; i < lines.length; i++) {
                if (lines[i].trim().isEmpty()) {
                    bodyStartIndex = i + 1;
                    break;
                }
            }

            if (bodyStartIndex != -1 && bodyStartIndex < lines.length) {
                StringBuilder bodyBuilder = new StringBuilder();
                for (int i = bodyStartIndex; i < lines.length; i++) {
                    bodyBuilder.append(lines[i]);
                }
                String body = bodyBuilder.toString().replace("\u0000", "").trim();
                Log.d(TAG, "Received WebSocket SyncEvent payload: " + body);

                // Signal to trigger existing sync engine
                handler.post(() -> {
                    Log.d(TAG, "Sync event received. Enqueuing sync worker.");
                    ReminderApplication.enqueueSyncWorker(context);
                });
            }
        } else if ("ERROR".equals(command)) {
            Log.e(TAG, "STOMP Error frame received: " + frameText);
        }
    }

    public synchronized void disconnect() {
        userWantsConnection = false;
        if (webSocket != null) {
            Log.d(TAG, "Disconnecting WebSocket...");
            String disconnectFrame = "DISCONNECT\n\n\u0000";
            try {
                webSocket.send(disconnectFrame);
            } catch (Exception ignored) {}
            webSocket.close(1000, "User logout");
            webSocket = null;
        }
        isConnected = false;
        isConnecting = false;
    }

    private synchronized void triggerReconnectIfNeeded() {
        if (!userWantsConnection || !tokenManager.isLoggedIn()) {
            return;
        }

        reconnectAttempts++;
        long delaySec;
        if (reconnectAttempts == 1) delaySec = 5;
        else if (reconnectAttempts == 2) delaySec = 10;
        else if (reconnectAttempts == 3) delaySec = 20;
        else delaySec = 60;

        // Add randomized jitter of ±1-2 seconds
        long jitter = random.nextInt(3) - 1; // -1, 0, or 1 seconds
        long finalDelayMs = Math.max(1000, (delaySec + jitter) * 1000);

        Log.d(TAG, "Scheduling reconnect attempt #" + reconnectAttempts + " in " + finalDelayMs + " ms.");
        handler.postDelayed(() -> {
            synchronized (WebSocketManager.this) {
                if (userWantsConnection && tokenManager.isLoggedIn() && !isConnected && !isConnecting) {
                    isConnecting = true;
                    executeConnect();
                }
            }
        }, finalDelayMs);
    }

    public synchronized boolean isConnected() {
        return isConnected;
    }
}
