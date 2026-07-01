# Reminder — Android Client

The Android client for the **Reminder Ecosystem**, facilitating cross-platform synchronization of Quick Notes, Timed Reminders, and Monthly Payments between Android devices, Windows desktop applications, and a centralized Spring Boot Server backend.

---

## 🚀 Key Features

* **Quick Notes**: A fluid, draggable task checklist on the main dashboard, synced instantly with a home-screen app widget (`QuickNotesWidgetProvider`).
* **Timed Reminders**: Precise, alarm-based alerts with configurable snooze periods (1, 5, or 10 minutes) using `AlarmManager`.
* **Monthly Payments**: Payment tracking that displays persistent, foreground service notifications (`PaymentNotificationService`) on their due dates.
* **Bi-directional Synchronization**: Performs automated synchronization with the backend server database using a "Last-Write-Wins" (LWW) conflict resolution policy.
* **Real-time Push Notifications**: Features a robust STOMP WebSocket client (`WebSocketManager`) that listens for live server-side database modifications and triggers immediate background syncs.
* **Background Processing**: Employs Jetpack `WorkManager` (`SyncWorker`) to perform periodic background sync operations even when the app is closed.
* **Secure Authentication**: Includes signup, login, and secure credentials storage. JWT access and refresh tokens are encrypted on-disk using Android Jetpack's `EncryptedSharedPreferences`.

---

## 🛠️ Architecture & Technical Stack

```
        +-------------------------------------------------+
        |                    Android App                  |
        |  +-------------------------------------------+  |
        |  |                 Activities                |  |
        |  +---------------------+---------------------+  |
        |                        |                        |
        |  +---------------------+---------------------+  |
        |  |                 SyncManager               |  |
        |  +----------+--------------------+-----------+  |
        |             |                    |              |
        |  +----------v----------+  +------v-----------+  |
        |  |     SyncRepository  |  | WebSocketManager |  |
        |  +----------+----------+  +------+-----------+  |
        |             |                    |              |
        +-------------|--------------------|--------------+
                      | REST               | STOMP (WS)
                      v                    v
        +-------------------------------------------------+
        |               Spring Boot Server                |
        +-------------------------------------------------+
```

### 1. Networking Layer (Retrofit & OkHttp)
* REST API calls are handled via **Retrofit**.
* Authenticated endpoints automatically append a JWT `Bearer` token using a custom interceptor.
* **TokenRefreshAuthenticator**: A custom OkHttp `Authenticator` intercepts `401 Unauthorized` responses and coordinates silent token refreshes.

### 2. Centralized Token Refresh Coordinator
* Guarded by a thread-safe reentrant lock (`refreshLock`) inside `AuthManager.getValidAccessToken()`.
* **Concurrency Prevention**: Prevents duplicate server-side rotation requests. If `SyncWorker` and `WebSocketManager` trigger a refresh simultaneously, only one network request is sent; concurrent callers block and reuse the freshly rotated credentials.
* **Session Protection**: Features a safety check that compares the original refresh token against the current storage token on network failures. If the storage token has already been rotated forward by another thread, the authenticator aborts the logout flow and retries with the new credentials.

### 3. Persistent Local Storage (SQLite)
* Local database helpers (`QuickNoteDatabaseHelper`, `ReminderDatabaseHelper`, `PaymentDatabaseHelper`) store data in local SQLite files.
* Every database record is augmented with a `server_id` and a `last_updated` timestamp to coordinate bi-directional merges.

---

## 📦 Getting Started

### Prerequisites
* Android Studio (Koala or newer)
* Android SDK 33+ (Build tools 34+)
* Java Development Kit (JDK) 17+

### 1. Configuration
Before compiling or packaging the app, configure the server base address:
1. Open [ServerConfig.java](file:///c:/Users/Jai/AndroidStudioProjects/Remainder/app/src/main/java/com/example/reminder/config/ServerConfig.java).
2. Set the `DEFAULT_BASE_URL` to point to your Spring Boot Server instance (e.g., `http://192.168.1.100:8080`).

### 2. Compilation and Build
Build the debug APK using the Gradle wrapper:
```bash
./gradlew assembleDebug
```
