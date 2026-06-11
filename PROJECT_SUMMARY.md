# Reminder — Android App: Full Project Summary

## Table of Contents
1. [Project Overview](#1-project-overview)
2. [Tech Stack & Build Config](#2-tech-stack--build-config)
3. [Package Structure](#3-package-structure)
4. [Features & Modules](#4-features--modules)
5. [Data Models](#5-data-models)
6. [Database Layer](#6-database-layer)
7. [Activities & UI](#7-activities--ui)
8. [Background Processing (Alarms, Receivers, Services)](#8-background-processing)
9. [Home Screen Widget](#9-home-screen-widget)
10. [Permissions](#10-permissions)
11. [UI Resources & Theming](#11-ui-resources--theming)
12. [Connecting to a Windows Application](#12-connecting-to-a-windows-application)

---

## 1. Project Overview

| Field | Value |
|---|---|
| App Name | **Reminder** |
| Package ID | `com.example.Reminder` |
| Language | Java |
| Min SDK | 33 (Android 13) |
| Target SDK | 34 (Android 14) |
| Version | 1.0 (versionCode 1) |
| Build Tool | Gradle (AGP 9.2.1) |
| Release APK | `app/release/app-release.apk` |

The app is a personal productivity tool with three core modules:
- **Quick Notes** — a fast, draggable checklist on the home screen and a home-screen widget
- **Timed Reminders** — one-shot alarm-based reminders with snooze support
- **Monthly Payments** — scheduled payment reminders that fire a persistent foreground notification on the due date

---

## 2. Tech Stack & Build Config

### Dependencies (`app/build.gradle`)
```groovy
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'androidx.core:core-ktx:1.10.1'
implementation 'com.google.android.material:material:1.12.0'
```

### Key Build Flags
- `minifyEnabled false` — no code shrinking in release builds
- `buildConfig = true` — BuildConfig class is generated
- `compileOptions` — Java 1.8 source/target compatibility

### Gradle Wrapper
- Gradle versions present in cache: 7.5, 8.13, 8.14.5, 9.4.1
- `settings.gradle` root project name: `Reminder`

---

## 3. Package Structure

```
com.example.Reminder/
├── MainActivity.java                  — App entry point, Quick Notes host
├── TimedRemindersActivity.java        — Create/manage timed reminders
├── MonthlyPaymentsActivity.java       — Create/manage monthly payment reminders
├── ExpiredRemindersActivity.java      — View expired reminders
├── SnoozeOptionsActivity.java         — Snooze UI (1/5/10 min)
│
├── Reminder.java                      — Data model: timed reminder
├── QuickNote.java                     — Data model: quick note
├── MonthlyPayment.java                — Data model: monthly payment
│
├── ReminderDatabaseHelper.java        — SQLite: reminders + quick_notes tables
├── QuickNoteDatabaseHelper.java       — SQLite: quick_notes table (standalone)
├── PaymentDatabaseHelper.java         — SQLite: monthly_payments table
│
├── ReminderAdapter.java               — RecyclerView adapter for reminders
├── QuickNoteAdapter.java              — RecyclerView adapter for quick notes
├── MonthlyPaymentAdapter.java         — RecyclerView adapter for payments
│
├── AlarmUtils.java                    — Central alarm scheduling + notification helpers
├── ReminderReceiver.java              — BroadcastReceiver: fires on reminder alarm
├── Paymentalarmreceiver.java          — BroadcastReceiver: fires on payment alarm
├── SnoozeReceiver.java                — BroadcastReceiver: opens snooze activity
├── PaymentNotificationService.java    — ForegroundService: persistent payment notifications
│
└── QuickNotesWidgetProvider.java      — AppWidgetProvider: home-screen Quick Notes widget
```

---

## 4. Features & Modules

### 4.1 Quick Notes
- Text input + add button on `MainActivity`
- Notes stored in `quick_notes.db` via `QuickNoteDatabaseHelper`
- Drag-and-drop reordering via `ItemTouchHelper`
- Each note has a completion toggle (checkbox-style)
- After any add/edit/delete, the home-screen widget is refreshed via `QuickNotesWidgetProvider.updateWidget()`

### 4.2 Timed Reminders
- User picks a date (DatePickerDialog) and time (TimePickerDialog with spinner style)
- Alarm scheduled using `AlarmUtils.scheduleReminder()` → `AlarmManager.setExact()` or `setExactAndAllowWhileIdle()`
- When alarm fires → `ReminderReceiver` shows a high-priority notification with a **Snooze** action button
- Tapping Snooze opens `SnoozeOptionsActivity` (1, 5, or 10 minutes)
- Reminders are split into **Pending** and **Expired** lists in `TimedRemindersActivity`
- A local broadcast (`com.example.Reminder.REMINDER_EXPIRED`) is sent when a reminder fires so the UI updates live

### 4.3 Monthly Payments
- User enters a payment name and selects a due date via `DatePickerDialog`
- Due date time is normalized to **9:00 AM** on the selected day
- Alarm scheduled via `Paymentalarmreceiver` using `setExactAndAllowWhileIdle()`
- When alarm fires → `PaymentNotificationService` (foreground service) shows a **persistent silent notification**
- "Clear All" cancels all alarms, deletes all DB records, and stops the foreground service
- Individual payment deletion cancels its specific alarm and removes its notification

---

## 5. Data Models

### `Reminder`
| Field | Type | Notes |
|---|---|---|
| `id` | `int` | Primary key |
| `text` | `String` | Reminder message |
| `timeMillis` | `long` | Unix timestamp (ms) for alarm trigger |
| `isExpired` | `boolean` | Whether the alarm has already fired |
| `snoozedUntil` | `long` | New time after snooze (-1 if not snoozed) |

### `QuickNote`
| Field | Type | Notes |
|---|---|---|
| `id` | `int` | Primary key |
| `text` | `String` | Note content |
| `isCompleted` | `boolean` | Completion toggle |
| `position` | `int` | Drag-and-drop order index |

### `MonthlyPayment`
| Field | Type | Notes |
|---|---|---|
| `id` | `int` | Primary key |
| `name` | `String` | Payment label |
| `isCompleted` | `boolean` | Manually marked paid |
| `dueDateMillis` | `long` | Unix timestamp for due date at 9 AM |

---

## 6. Database Layer

### `reminders.db` — managed by `ReminderDatabaseHelper` (v5)

**Table: `reminders`**
```sql
CREATE TABLE reminders (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    text        TEXT NOT NULL,
    time        INTEGER NOT NULL,
    is_expired  INTEGER DEFAULT 0,
    snoozed_time INTEGER DEFAULT 0
);
```

**Table: `quick_notes`** (also in this DB for legacy reasons)
```sql
CREATE TABLE quick_notes (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    text         TEXT NOT NULL,
    is_completed INTEGER DEFAULT 0
);
```

**Key methods:**
- `addReminder(text, timeMillis)`
- `deleteReminder(id)`
- `getPendingReminders()` — `is_expired = 0`
- `getExpiredReminders()` — `is_expired = 1`
- `markAsExpired(id)` / `markAsPending(id)`
- `updateReminderStatus(id, "expired"|"pending")`
- `snoozeReminder(id, newTimeMillis)` — updates `time`, `snoozed_time`, resets `is_expired`
- `getSnoozedTime(id)` / `clearSnoozeTime(id)`
- `getAllReminders()` — all rows ordered by time ASC

---

### `quick_notes.db` — managed by `QuickNoteDatabaseHelper` (v2)

**Table: `quick_notes`**
```sql
CREATE TABLE quick_notes (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    text         TEXT NOT NULL,
    is_completed INTEGER DEFAULT 0,
    position     INTEGER DEFAULT 0
);
```

**Key methods:**
- `addNote(text)` / `addNote(text, isCompleted)`
- `deleteNote(id)`
- `updateNote(id, newText, isCompleted)`
- `getAllNotes()` — ordered by `position ASC`
- `updateNotePosition(id, newPosition)`
- `getNoteTexts()` — returns `List<String>` for widget

---

### `payments.db` — managed by `PaymentDatabaseHelper` (v1)

**Table: `monthly_payments`**
```sql
CREATE TABLE monthly_payments (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    name      TEXT NOT NULL,
    due_date  INTEGER NOT NULL,
    completed INTEGER NOT NULL DEFAULT 0
);
```

**Key methods:**
- `insertPayment(name, dueDate, isCompleted)` → returns `int` id
- `getAllPayments()` → `ArrayList<MonthlyPayment>`
- `updatePaymentStatus(id, isCompleted)`
- `deletePayment(id)` / `deleteAllPayments()`

---

## 7. Activities & UI

### `MainActivity` (`activity_main.xml`)
- Hides action bar on launch
- Calls `createNotificationChannel()` for `reminder_channel`
- Requests `SCHEDULE_EXACT_ALARM` and `POST_NOTIFICATIONS` permissions
- Hosts Quick Notes RecyclerView with drag-and-drop
- Navigation buttons to `MonthlyPaymentsActivity` and `TimedRemindersActivity`

### `TimedRemindersActivity` (`activity_timed_reminder.xml`)
- Date + Time picker flow to create a reminder
- Two RecyclerViews: Pending and Expired (labels hidden when empty)
- Registers a local `BroadcastReceiver` for `com.example.Reminder.REMINDER_EXPIRED` to live-refresh list
- Unregisters receiver in `onDestroy()`

### `MonthlyPaymentsActivity` (`activity_monthly_payments.xml`)
- Name input + Date picker to add a payment
- RecyclerView of all payments
- "Clear All" button cancels all alarms and stops `PaymentNotificationService`

### `ExpiredRemindersActivity` (`activity_expired_reminders.xml`)
- Read-only list of expired reminders from DB
- Finishes itself when all items are deleted

### `SnoozeOptionsActivity` (`activity_snooze_options.xml`)
- Launched from notification Snooze action or `SnoozeReceiver`
- Three buttons: Snooze 1 min, 5 min, 10 min
- Cancels the current notification and reschedules via `AlarmManager.setExactAndAllowWhileIdle()`

---

## 8. Background Processing

### `AlarmUtils.java`
Central utility class for all alarm and notification operations:
- `scheduleReminder(context, id, text, triggerMillis)` — uses `setExact()` or `setExactAndAllowWhileIdle()`
- `schedulePaymentReminder(context, id, name, dayOfMonth)` — calculates 9 AM trigger, same month or next
- `cancelReminder(context, id)` — cancels `PendingIntent` from `AlarmManager`
- `showMonthlyPaymentNotification(context, id, name)` — posts an ongoing notification directly
- `cancelNotification(context, id)` — cancels a notification by ID

### `ReminderReceiver` (BroadcastReceiver)
Fired when a timed reminder alarm triggers:
1. Marks reminder as expired in DB
2. Sends `com.example.Reminder.REMINDER_EXPIRED` broadcast
3. Builds a `HIGH` priority notification with a **Snooze** action (opens `SnoozeOptionsActivity`)
4. Notification taps open `TimedRemindersActivity`

### `Paymentalarmreceiver` (BroadcastReceiver)
Fired when a monthly payment alarm triggers:
- Reads `payment_id` and `payment_name` from intent extras
- Calls `context.startForegroundService(PaymentNotificationService)`

### `SnoozeReceiver` (BroadcastReceiver)
- Receives snooze action from notification
- Starts `SnoozeOptionsActivity` with `FLAG_ACTIVITY_NEW_TASK`

### `PaymentNotificationService` (ForegroundService)
- `foregroundServiceType = specialUse`
- Manages a `Map<Integer, String>` of active payment notifications
- Actions: show notification, remove specific payment notification, stop service entirely
- Uses `CHANNEL_ID = "payment_reminder_channel"` with `IMPORTANCE_LOW` (silent)
- Maintains foreground status by switching to another active payment if the foreground one is removed

---

## 9. Home Screen Widget

### `QuickNotesWidgetProvider`
- Extends `AppWidgetProvider`
- Reads all **incomplete** notes from `QuickNoteDatabaseHelper`
- Renders them as a bulleted list (`• note text`) in `widget_quick_notes.xml`
- Falls back to `"No quick notes"` if list is empty
- Clicking the widget launches `MainActivity`
- `updateWidget(context)` is a static helper called after any note change

### Widget Layout (`widget_quick_notes.xml`)
- `LinearLayout` with semi-transparent black background (`#CC000000`)
- `TextView` title: "Quick Notes" (24sp bold, white)
- `TextView` content: note list (18sp, white, scrollable via `layout_weight=1`)

### Widget Config (`quick_notes_widget_info.xml`)
```xml
android:minWidth="110dp"
android:minHeight="70dp"
android:updatePeriodMillis="1800000"   <!-- auto-refresh every 30 minutes -->
android:resizeMode="horizontal|vertical"
android:widgetCategory="home_screen"
```

---

## 10. Permissions

| Permission | Purpose |
|---|---|
| `SCHEDULE_EXACT_ALARM` | Schedule exact alarms on Android 12+ |
| `USE_EXACT_ALARM` | Alternative exact alarm permission |
| `POST_NOTIFICATIONS` | Show notifications on Android 13+ |
| `FOREGROUND_SERVICE` | Run `PaymentNotificationService` |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Required for `specialUse` foreground service type |

---

## 11. UI Resources & Theming

### Theme
`Theme.MaterialComponents.DayNight.DarkActionBar` — supports light and dark mode

### Color Palette
- **Accent Purple**: `#7F77DD`
- **Accent Red**: `#993C1D`
- **Dark background**: `#0F0F0F`, surface: `#1A1A1A`
- **Light background**: `#F5F5F5`, surface: `#FFFFFF`
- Theme-aware aliases: `colorBackground`, `colorTextPrimary`, `colorDivider`, etc.

### Drawable Assets
| File | Purpose |
|---|---|
| `rounded_edittext.xml` | Rounded input field shape |
| `rounded_primary_button.xml` | Primary action button shape |
| `rounded_secondary_button.xml` | Secondary action button shape |
| `rounded_item_background.xml` | Card-style list item background |
| `rounded_timepicker.xml` | Time picker dialog background |
| `edittext_background.xml` | Edit text state drawable |
| `ic_notification.xml` | Custom notification icon |
| `ic_notification_off.xml` | Muted notification icon |

Light/dark variants exist in `drawable/` and `drawable-night/`.

### Notification Channels
| Channel ID | Name | Importance | Purpose |
|---|---|---|---|
| `reminder_channel` | Reminders | HIGH | Timed reminder alerts |
| `payment_reminder_channel` | Payment Reminders | LOW (silent) | Persistent payment due alerts |

---

## 12. Connecting to a Windows Application

This section covers everything needed to sync data between this Android app and a Windows desktop application.

---

### 12.1 What Data Needs to Sync

| Data | Android DB File | Table | Key Fields |
|---|---|---|---|
| Quick Notes | `quick_notes.db` | `quick_notes` | `id, text, is_completed, position` |
| Timed Reminders | `reminders.db` | `reminders` | `id, text, time, is_expired, snoozed_time` |
| Monthly Payments | `payments.db` | `monthly_payments` | `id, name, due_date, completed` |

The Android SQLite databases are stored at:
```
/data/data/com.example.Reminder/databases/quick_notes.db
/data/data/com.example.Reminder/databases/reminders.db
/data/data/com.example.Reminder/databases/payments.db
```
These are only directly accessible on **rooted devices** or via **ADB** in debug builds.

---

### 12.2 Recommended Architecture: REST API Sync

The cleanest approach is to add a **REST API backend** (e.g., a lightweight Python/Node.js server or an AWS service) that both the Android app and the Windows app talk to.

```
Android App  ←→  REST API / Cloud Backend  ←→  Windows App
                        ↕
                   Database (e.g., SQLite / PostgreSQL / DynamoDB)
```

---

### 12.3 Android Side — Changes Required

#### A. Add Networking Permission to `AndroidManifest.xml`
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

#### B. Add Retrofit or OkHttp Dependency (`app/build.gradle`)
```groovy
implementation 'com.squareup.retrofit2:retrofit:2.9.0'
implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
```

#### C. Create a Sync API Interface
```java
public interface SyncApi {
    @GET("notes")       Call<List<QuickNote>>      getNotes();
    @POST("notes")      Call<QuickNote>            addNote(@Body QuickNote note);
    @DELETE("notes/{id}") Call<Void>               deleteNote(@Path("id") int id);

    @GET("reminders")   Call<List<Reminder>>       getReminders();
    @POST("reminders")  Call<Reminder>             addReminder(@Body Reminder reminder);

    @GET("payments")    Call<List<MonthlyPayment>> getPayments();
    @POST("payments")   Call<MonthlyPayment>       addPayment(@Body MonthlyPayment payment);
}
```

#### D. Sync Strategy Options
- **On-demand sync**: call API when user opens the app (`onResume`)
- **Background sync**: use `WorkManager` with a periodic task
- **Real-time**: use WebSockets or Firebase Realtime Database

---

### 12.4 Windows Application Side

#### Option A: Python Desktop App (Tkinter / PyQt)
```python
import requests

BASE_URL = "http://your-server-ip:8080"

def get_notes():
    return requests.get(f"{BASE_URL}/notes").json()

def add_note(text):
    return requests.post(f"{BASE_URL}/notes", json={"text": text, "is_completed": False}).json()
```

#### Option B: C# WPF / WinForms App
```csharp
var client = new HttpClient { BaseAddress = new Uri("http://your-server-ip:8080") };
var response = await client.GetAsync("/notes");
var notes = await response.Content.ReadFromJsonAsync<List<QuickNote>>();
```

#### Option C: Electron (JavaScript/TypeScript) Desktop App
```js
const notes = await fetch('http://your-server-ip:8080/notes').then(r => r.json());
```

---

### 12.5 Backend Server (Minimal Example — Python Flask)

```python
from flask import Flask, request, jsonify
import sqlite3, os

app = Flask(__name__)
DB_PATH = "shared_data.db"

def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

@app.route("/notes", methods=["GET"])
def get_notes():
    db = get_db()
    notes = db.execute("SELECT * FROM quick_notes ORDER BY position").fetchall()
    return jsonify([dict(n) for n in notes])

@app.route("/notes", methods=["POST"])
def add_note():
    data = request.json
    db = get_db()
    db.execute("INSERT INTO quick_notes (text, is_completed, position) VALUES (?,?,?)",
               (data["text"], data.get("is_completed", 0), data.get("position", 0)))
    db.commit()
    return jsonify({"status": "ok"}), 201

@app.route("/notes/<int:note_id>", methods=["DELETE"])
def delete_note(note_id):
    db = get_db()
    db.execute("DELETE FROM quick_notes WHERE id = ?", (note_id,))
    db.commit()
    return jsonify({"status": "ok"})

# Add similar routes for /reminders and /payments

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8080)
```

---

### 12.6 Alternative: Local Network Sync (No Cloud)

If both devices are on the **same Wi-Fi network**:

1. Run the Flask server on the Windows machine
2. Android app calls `http://<windows-ip>:8080`
3. Find Windows LAN IP: `ipconfig` → look for IPv4 address

This works great for a local-only setup with no internet requirement.

---

### 12.7 Alternative: Firebase (Easiest Cloud Option)

Use **Firebase Realtime Database** or **Firestore** — no custom server needed.

#### Android — Add to `build.gradle`
```groovy
implementation 'com.google.firebase:firebase-database:20.3.0'
// or
implementation 'com.google.firebase:firebase-firestore:24.11.0'
```

#### Android — Write a Note
```java
DatabaseReference db = FirebaseDatabase.getInstance().getReference("notes");
db.push().setValue(note);
```

#### Windows (Python) — Read Notes
```python
import pyrebase
firebase = pyrebase.initialize_app(config)
db = firebase.database()
notes = db.child("notes").get().val()
```

---

### 12.8 Data Serialization (JSON Schema)

Both sides must agree on a JSON format. Recommended schemas:

**Quick Note**
```json
{ "id": 1, "text": "Buy milk", "is_completed": false, "position": 1 }
```

**Timed Reminder**
```json
{ "id": 1, "text": "Doctor appointment", "time": 1720000000000, "is_expired": false, "snoozed_time": 0 }
```

**Monthly Payment**
```json
{ "id": 1, "name": "Netflix", "due_date": 1720000000000, "completed": false }
```

Note: `time` and `due_date` are **Unix timestamps in milliseconds**.

---

### 12.9 Security Considerations

- Use **HTTPS** (not HTTP) in production — get a free cert via Let's Encrypt
- Add **API key authentication** header: `X-API-Key: <your-secret-key>`
- Validate all inputs on the server side
- Never hardcode server IP in release builds — use a `BuildConfig` field or remote config
- If using Firebase, configure **Security Rules** to restrict read/write access

---

### 12.10 Summary of Files to Create/Modify for Windows Connectivity

| File | Change |
|---|---|
| `AndroidManifest.xml` | Add `INTERNET` permission |
| `app/build.gradle` | Add Retrofit + Gson dependencies |
| `SyncApi.java` (new) | Retrofit API interface |
| `SyncRepository.java` (new) | Handles sync calls, merges with local DB |
| `QuickNote.java` | Add `@SerializedName` Gson annotations |
| `Reminder.java` | Add `@SerializedName` Gson annotations |
| `MonthlyPayment.java` | Add `@SerializedName` Gson annotations |
| `MainActivity.java` | Trigger sync on `onResume` |
| Backend server | New Python/Node.js/etc. server |
| Windows app | New WPF / PyQt / Electron app consuming the API |
