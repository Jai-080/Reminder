package com.example.reminder.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.example.reminder.ReminderApplication;
import com.example.reminder.auth.TokenManager;
import com.example.reminder.utils.FileLogger;
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
        FileLogger.log("WEBSOCKET", "WebSocket connect() called: isLoggedIn=" + tokenManager.isLoggedIn()
                + ", isConnected=" + isConnected + ", isConnecting=" + isConnecting);
        if (!tokenManager.isLoggedIn()) {
            FileLogger.log("WEBSOCKET", "WebSocket connect(): Cannot connect WebSocket: User not logged in.");
            Log.d(TAG, "Cannot connect WebSocket: User not logged in.");
            return;
        }

        userWantsConnection = true;

        if (isConnected || isConnecting) {
            FileLogger.log("WEBSOCKET", "WebSocket connect(): Already connected or connecting.");
            Log.d(TAG, "WebSocket is already connected or connecting.");
            return;
        }

        isConnecting = true;
        reconnectAttempts = 0;
        executeConnect();
    }

    private String getValidTokenOrRefresh() {
        FileLogger.log("WEBSOCKET", "getValidTokenOrRefresh() called");
        FileLogger.log("WEBSOCKET", "Access token expired check initiated");
        try {
            com.example.reminder.auth.AuthManager authManager = com.example.reminder.auth.AuthManager.getInstance(context);
            FileLogger.log("WEBSOCKET", "Refresh requested from AuthManager coordinator");
            String token = authManager.getValidAccessToken();
            FileLogger.log("WEBSOCKET", "Refresh completed: Token acquired: " + FileLogger.formatTokenMeta(token));
            return token;
        } catch (retrofit2.HttpException e) {
            // Definitive server rejection (e.g. HTTP 400/401 from /api/auth/refresh) -
            // AuthManager has already determined the refresh token is invalid. Do NOT
            // fall back to the stale access token and do NOT open a WebSocket with it;
            // abort this connection attempt and let TokenRefreshAuthenticator/
            // forceLogout() remain the single source of truth for session teardown.
            FileLogger.log("WEBSOCKET", "Refresh rejected by server (HTTP " + e.code() + "). Not opening WebSocket with stale token.");
            Log.w(TAG, "Refresh rejected by server. Aborting WebSocket connection instead of using stale token.", e);
            return null;
        } catch (java.io.IOException e) {
            // Network/timeout failure, not a definitive auth rejection - preserve the
            // existing fallback/retry behavior.
            FileLogger.log("WEBSOCKET", "Refresh failed (network): " + e.getMessage(), e);
            Log.w(TAG, "Refresh failed due to network exception. Falling back to currently stored token.", e);
            return tokenManager.getAccessToken();
        } catch (Exception e) {
            FileLogger.log("WEBSOCKET", "Refresh failed: " + e.getMessage(), e);
            Log.e(TAG, "Unexpected error obtaining valid access token from coordinator. Falling back to currently stored token.", e);
            return tokenManager.getAccessToken();
        }
    }

    private synchronized void executeConnect() {
        FileLogger.log("WEBSOCKET", "executeConnect() called");
        new Thread(() -> {
            String token = getValidTokenOrRefresh();
            if (token == null) {
                FileLogger.log("WEBSOCKET", "executeConnect(): Cannot connect: valid token is null");
                Log.e(TAG, "Cannot connect: valid token is null");
                synchronized (WebSocketManager.this) {
                    isConnecting = false;
                }
                return;
            }

            // Format WebSocket URL from Base URL
            String baseUrl = tokenManager.getBaseUrl();
            String wsUrl = baseUrl.replace("http://", "ws://").replace("https://", "wss://");
            if (!wsUrl.endsWith("/")) {
                wsUrl += "/";
            }
            wsUrl += "ws?token=" + token;

            FileLogger.log("WEBSOCKET", "executeConnect(): Opening WebSocket connection to server");
            Log.d(TAG, "Connecting to WebSocket at: " + wsUrl);

            OkHttpClient client = new OkHttpClient.Builder()
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .build();

            Request request = new Request.Builder()
                    .url(wsUrl)
                    .build();

            synchronized (WebSocketManager.this) {
                if (!userWantsConnection) {
                    FileLogger.log("WEBSOCKET", "executeConnect(): userWantsConnection is false. Aborting connection.");
                    isConnecting = false;
                    return;
                }

                webSocket = client.newWebSocket(request, new WebSocketListener() {
                    @Override
                    public void onOpen(WebSocket ws, Response response) {
                        FileLogger.log("WEBSOCKET", "WebSocket transport opened (HTTP " + response.code() + "). Sending STOMP CONNECT frame.");
                        Log.d(TAG, "WebSocket connected");
                        String connectFrame = "CONNECT\n" +
                                "accept-version:1.1,1.2\n" +
                                "heart-beat:0,0\n" +
                                "Authorization:Bearer " + token + "\n" +
                                "\n" +
                                "\u0000";
                        Log.d(TAG, "STOMP Frame Outgoing [CONNECT]:\n" + connectFrame);
                        ws.send(connectFrame);
                    }

                    @Override
                    public void onMessage(WebSocket ws, String text) {
                        handleStompFrame(text);
                    }

                    @Override
                    public void onClosing(WebSocket ws, int code, String reason) {
                        FileLogger.log("WEBSOCKET", "WebSocket closing: code=" + code + ", reason=" + reason);
                        Log.d(TAG, "WebSocket closing: " + reason);
                        ws.close(1000, null);
                    }

                    @Override
                    public void onClosed(WebSocket ws, int code, String reason) {
                        FileLogger.log("WEBSOCKET", "Socket disconnected: WebSocket closed: code=" + code + ", reason=" + reason);
                        Log.d(TAG, "WebSocket closed: " + reason);
                        synchronized (WebSocketManager.this) {
                            isConnected = false;
                            isConnecting = false;
                        }
                        triggerReconnectIfNeeded();
                    }

                    @Override
                    public void onFailure(WebSocket ws, Throwable t, Response response) {
                        FileLogger.log("WEBSOCKET", "Socket disconnected: WebSocket failure: " + t.getMessage(), t);
                        FileLogger.log("WEBSOCKET", "Socket authentication failed: connection failure");
                        Log.e(TAG, "WebSocket failure: " + t.getMessage(), t);
                        synchronized (WebSocketManager.this) {
                            isConnected = false;
                            isConnecting = false;
                        }
                        triggerReconnectIfNeeded();
                    }
                });
            }
        }).start();
    }

    private synchronized void handleStompFrame(String frameText) {
        if (frameText == null || frameText.isEmpty()) return;

        String[] lines = frameText.split("\n");
        if (lines.length == 0) return;

        String command = lines[0].trim();
        Log.d(TAG, "Received STOMP command: " + command);

        if ("CONNECTED".equals(command)) {
            FileLogger.log("WEBSOCKET", "Socket authentication successful: STOMP CONNECTED received");
            if (reconnectAttempts > 0) {
                FileLogger.log("WEBSOCKET", "Reconnect successful");
            }
            Log.d(TAG, "STOMP CONNECTED");
            Log.d(TAG, "STOMP Frame Incoming [CONNECTED]:\n" + frameText);
            synchronized (this) {
                isConnected = true;
                isConnecting = false;
                reconnectAttempts = 0;
            }
            String subscribeFrame = "SUBSCRIBE\n" +
                    "id:sub-0\n" +
                    "destination:/user/topic/sync\n" +
                    "\n" +
                    "\u0000";
            FileLogger.log("WEBSOCKET", "STOMP SUBSCRIBE sent to /user/topic/sync");
            Log.d(TAG, "SUBSCRIBE sent");
            Log.d(TAG, "STOMP Frame Outgoing [SUBSCRIBE]:\n" + subscribeFrame);
            if (webSocket != null) {
                webSocket.send(subscribeFrame);
            }

            // Phase 13 Startup Recovery: trigger immediate sync after CONNECTED
            Log.d(TAG, "WebSocket connected successfully. Enqueuing recovery sync.");
            handler.post(() -> ReminderApplication.enqueueSyncWorker(context));

        } else if ("MESSAGE".equals(command)) {
            FileLogger.log("WEBSOCKET", "STOMP MESSAGE received");
            Log.d(TAG, "MESSAGE received");
            Log.d(TAG, "MESSAGE received on thread: " + Thread.currentThread().getName());
            Log.d(TAG, "MESSAGE handler entered");
            Log.d(TAG, "STOMP Frame Incoming [MESSAGE]:\n" + frameText);
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
                Log.d(TAG, "SyncEvent payload: " + body);

                // Signal to trigger existing sync engine immediately on a background thread
                new Thread(() -> {
                    Log.d(TAG, "Starting sync on thread: " + Thread.currentThread().getName());
                    if (SyncManager.getInstance(context).isSyncRunning()) {
                        FileLogger.log("WEBSOCKET", "Immediate sync skipped: sync is already running");
                        Log.d(TAG, "Immediate sync skipped because syncRunning=true");
                    } else {
                        FileLogger.log("WEBSOCKET", "About to execute immediate sync from STOMP event");
                        Log.d(TAG, "About to execute immediate sync");
                        SyncManager.getInstance(context).performFullSync(new SyncManager.SyncCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                FileLogger.log("WEBSOCKET", "Immediate sync from STOMP event finished successfully");
                                Log.d(TAG, "Immediate sync finished");
                            }

                            @Override
                            public void onError(String error) {
                                FileLogger.log("WEBSOCKET", "Immediate sync from STOMP event finished with error: " + error);
                                Log.d(TAG, "Immediate sync finished");
                            }
                        });
                    }
                }).start();
            }
        } else if ("ERROR".equals(command)) {
            FileLogger.log("WEBSOCKET", "STOMP ERROR received: " + frameText);
            FileLogger.log("WEBSOCKET", "Socket authentication failed: STOMP ERROR received");
            Log.e(TAG, "STOMP Frame Incoming [ERROR]:\n" + frameText);
        }
    }

    public synchronized void disconnect() {
        userWantsConnection = false;
        FileLogger.log("WEBSOCKET", "Socket disconnected: WebSocket disconnect() called");
        if (webSocket != null) {
            String disconnectFrame = "DISCONNECT\n\n\u0000";
            Log.d(TAG, "STOMP Frame Outgoing [DISCONNECT]:\n" + disconnectFrame);
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

        FileLogger.log("WEBSOCKET", "Reconnect scheduled: attempt #" + reconnectAttempts + " in " + finalDelayMs + " ms");
        Log.d(TAG, "Scheduling reconnect attempt #" + reconnectAttempts + " in " + finalDelayMs + " ms.");
        handler.postDelayed(() -> {
            synchronized (WebSocketManager.this) {
                if (userWantsConnection && tokenManager.isLoggedIn() && !isConnected && !isConnecting) {
                    FileLogger.log("WEBSOCKET", "Reconnect started: executing connection for attempt #" + reconnectAttempts);
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
