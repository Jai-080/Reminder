# Reminder — Android Application

The Android client for the Reminder Ecosystem, featuring Quick Notes, Timed Reminders, and Monthly Payments.

## Key Features
* **Quick Notes**: A fast checklist directly on your main dashboard, synced with a home screen app widget.
* **Timed Reminders**: One-shot time-sensitive alerts with configurable snooze periods (1, 5, or 10 minutes).
* **Monthly Payments**: Payment tracking that displays persistent foreground service alerts on their due dates.
* **Bi-directional Synchronization**: Syncs automatically with the Spring Boot Server backend, using "Last-Write-Wins" (LWW) rules.

## Getting Started

### Prerequisites
* Android Studio (Koala or newer)
* Android SDK 33+ (Build tools 34+)
* Java Development Kit (JDK) 17+

### 1. Configuration
Before compiling or packaging the app, you need to point the app to your server address. 
Please refer to [CONFIGURATION.md](CONFIGURATION.md) for detailed configuration guide options.

### 2. Compilation and Build
You can build the app via the command line or open it inside Android Studio.
To compile a debug build from terminal:
```bash
./gradlew assembleDebug
```
The compiled APK will be output at:
`app/build/outputs/apk/debug/reminder.apk`
