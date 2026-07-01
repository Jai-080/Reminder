# Reminder — Android App: Full Project Summary

## Table of Contents
1. [Project Overview](#1-project-overview)
2. [Tech Stack & Build Config](#2-tech-stack--build-config)
3. [Package Structure](#3-package-structure)
4. [Features & Modules](#4-features--modules)
5. [Data Models & DB Schemas](#5-data-models--db-schemas)
6. [Activities & UI](#6-activities--ui)
7. [Background Processing](#7-background-processing)
8. [Ecosystem Synchronization Architecture](#8-ecosystem-synchronization-architecture)
9. [Permissions](#9-permissions)
10. [Diagnostics & Logs](#10-diagnostics--logs)

---

## 1. Project Overview

| Field | Value |
|---|---|
| App Name | **Reminder** |
| Package ID | `com.example.reminder` |
| Language | Java |
| Min SDK | 33 (Android 13) |
| Target SDK | 34 (Android 14) |
| Version | 1.2.1 |
| Build Tool | Gradle (AGP 9.2.1) |

The Android client is a component of a multi-platform ecosystem (including Spring Boot Server and Windows Desktop client) designed to coordinate:
* **Quick Notes** — Draggable checklist backed by a home-screen widget.
* **Timed Reminders** — AlarmManager alerts with snoozing support.
* **Monthly Payments** — Recurring notifications controlled by a foreground service.
* **Bi-directional Cloud Sync** — High-performance LWW REST synchronization with real-time push and background worker routines.

---

## 2. Tech Stack & Build Config

### Key Dependencies (`app/build.gradle`)
```groovy
// UI & AndroidX Core
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'androidx.core:core-ktx:1.10.1'
implementation 'com.google.android.material:material:1.12.0'
implementation 'androidx.work:work-runtime:2.9.0'

// Networking (Retrofit & OkHttp)
implementation 'com.squareup.retrofit2:retrofit:2.9.0'
implementation 'com.squareup.retrofit2:converter-gson:2.9.0'

// Real-Time Communication (WebSockets)
implementation 'com.github.NaikSoftware:StompProtocolAndroid:1.6.6'
implementation 'io.reactivex:rxjava:2.2.21'
implementation 'io.reactivex:rxandroid:2.1.1'

// Security & Encryption
implementation 'androidx.security:security-crypto:1.1.0-alpha06'
```

---

## 3. Package Structure

```
com.example.reminder/
├── MainActivity.java
├── TimedRemindersActivity.java
├── MonthlyPaymentsActivity.java
├── ExpiredRemindersActivity.java
├── SnoozeOptionsActivity.java
├── AlarmUtils.java
├── ReminderReceiver.java
├── Paymentalarmreceiver.java
├── SnoozeReceiver.java
├── PaymentNotificationService.java
├── QuickNotesWidgetProvider.java
│
├── auth/
│   ├── LoginActivity.java               — UI: User Login and IP Configuration
│   ├── RegisterActivity.java            — UI: User Sign-Up
│   ├── AuthManager.java                 — Central authentication and token refresh coordinator
│   └── TokenManager.java                — EncryptedSharedPreferences storage manager
│
├── network/
│   ├── ApiClient.java                   — OkHttp client and Retrofit configuration
│   ├── AuthApi.java                     — Retrofit authentication interfaces
│   ├── NoteApi.java                     — Retrofit note CRUD interfaces
│   ├── PaymentApi.java                  — Retrofit payment CRUD interfaces
│   ├── ReminderApi.java                 — Retrofit reminder CRUD interfaces
│   ├── TokenRefreshAuthenticator.java   — Silent token refresh OkHttp Authenticator
│   └── [Requests & Responses]           — Data contract objects (e.g. AuthResponse, NoteRequest)
│
├── sync/
│   ├── SyncManager.java                 — Orchestrates bidirectional local-remote database updates
│   ├── SyncRepository.java              — Access layer to trigger Retrofit REST API endpoints
│   ├── SyncWorker.java                  — WorkManager periodic background execution task
│   └── WebSocketManager.java            — STOMP WebSocket push notifications client listener
│
└── utils/
    └── AuthLogger.java                  — Diagnostical persistent logging helper
```

---

## 4. Features & Modules

### 4.1 Quick Notes
* Draggable list items with completion checkbox states.
* Instantly updates the home widget `QuickNotesWidgetProvider` after modifications.

### 4.2 Timed Reminders
* Custom Spinners for picking date and time.
* Leverages `AlarmManager` for firing `ReminderReceiver` which posts high-importance notification alerts with a Snooze option.

### 4.3 Monthly Payments
* Normalizes alarms to **9:00 AM** on the specified due date.
* Managed by `PaymentNotificationService` to maintain persistent, clearable silent foreground notifications.

---

## 5. Data Models & DB Schemas

All SQLite databases are located at `/data/data/com.example.reminder/databases/`. Table schemas are augmented with sync metadata columns:

### A. Quick Notes (`quick_notes.db` -> Version 3)
```sql
CREATE TABLE quick_notes (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    text         TEXT NOT NULL,
    is_completed INTEGER DEFAULT 0,
    position     INTEGER DEFAULT 0,
    server_id    INTEGER,
    last_updated INTEGER DEFAULT 0
);
```

### B. Timed Reminders (`reminders.db` -> Version 6)
```sql
CREATE TABLE reminders (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    text         TEXT NOT NULL,
    time         INTEGER NOT NULL,
    is_expired   INTEGER DEFAULT 0,
    snoozed_time INTEGER DEFAULT 0,
    server_id    INTEGER,
    last_updated INTEGER DEFAULT 0
);
```

### C. Monthly Payments (`payments.db` -> Version 2)
```sql
CREATE TABLE monthly_payments (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    name         TEXT NOT NULL,
    due_date     INTEGER NOT NULL,
    completed    INTEGER NOT NULL DEFAULT 0,
    server_id    INTEGER,
    last_updated INTEGER DEFAULT 0,
    amount       REAL,
    recurrence   TEXT DEFAULT 'MONTHLY',
    notification_offsets TEXT DEFAULT '0',
    last_paid_at INTEGER
);
```

---

## 6. Activities & UI

* **`LoginActivity`**: Connects to the server, saves server base URL, guides users to `RegisterActivity` or main dashboard, and includes a debug developer bypass option.
* **`RegisterActivity`**: Connects users to the registration REST endpoints.
* **`MainActivity`**: Verifies login states, redirects unauthenticated users, hosts the checklist, and links to specialized modules.

---

## 7. Background Processing

* **`SyncWorker` (WorkManager)**: Runs in the background (15-minute periodic schedule) to execute bidirectional synchronization routines.
* **`WebSocketManager` (STOMP Client)**: Opens a persistent background WebSocket channel. Listening threads receive database mutation broadcast alerts from the server and instantly schedule local synchronization runs.

---

## 8. Ecosystem Synchronization Architecture

```
  Android (SyncManager)  <==== Bi-Directional REST ====>  Spring Boot Server
           |                                                      ^
           |                                                      |
           +----- WebSocket (STOMP Database Mutations Log) -------+
```

### 8.1 Bi-Directional Conflict Resolution
The system relies on a **Last-Write-Wins (LWW)** reconciliation logic:
1. Checks for records modified since the last sync timestamp.
2. Identifies new local records (lacking `server_id`) and uploads them to the server.
3. Compares timestamps of locally updated records with the server's timestamps.
4. Downloads modified data from the server, replacing local entries with newer modification timestamps.

### 8.2 Centralized Token Refresh Coordinator
To avoid race conditions when the access token expires (such as concurrent requests from `WebSocketManager` and `SyncWorker` both executing `/api/auth/refresh` simultaneously), `AuthManager.getValidAccessToken()` coordinates refresh calls using a reentrant monitor lock (`refreshLock`):
* **Request Merging**: If a refresh is already in progress, other threads block on `refreshLock`. Once the lock releases, they read the newly rotated credentials instead of starting a new `/refresh` request.
* **Accidental Logout Safety Checks**: When a refresh fails (e.g. timeout or transient API issues), the `TokenRefreshAuthenticator` checks if the stored refresh token has changed since it began the operation. If the token changed, it knows another concurrent thread succeeded and safely retries the request with the new tokens rather than executing an unwanted `forceLogout()`.

---

## 9. Permissions

* `INTERNET`: Communicates with REST endpoints and WebSocket ports.
* `POST_NOTIFICATIONS`: Shows alarm and payment notifications on Android 13+.
* `SCHEDULE_EXACT_ALARM` & `USE_EXACT_ALARM`: Schedules exact timed reminders.
* `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_SPECIAL_USE`: Runs the monthly payment foreground notification tracker.

---

## 10. Diagnostics & Logs

A robust, thread-safe, append-only logger utility writes diagnostic statements to:
* **Log File Location**: `/Android/data/com.example.reminder/files/logs/auth_debug_log.txt`

This directory does not require runtime permissions on Android 4.4+. It captures all database operations, token refreshes, server response states, and thread details.
