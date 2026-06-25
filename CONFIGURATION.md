# Configuration Guide — Android Application

This guide explains how to set up, configure, and secure the Android client before deploying.

---

## 1. Server URL Setup

By default, the client is configured with a placeholder server address inside `app/src/main/java/com/example/reminder/config/ServerConfig.java`:

* **Default Placeholder**: `http://your-server-address:50000/`

To set your actual backend server IP or hostname:
1. Open [ServerConfig.java](app/src/main/java/com/example/reminder/config/ServerConfig.java).
2. Modify the `BASE_URL` constant:
   ```java
   public static final String BASE_URL = "http://your-server-domain-or-ip:port/";
   ```
3. Alternatively, you can override the base server URL in-app via the **Base Server URL** input field on the Login/Registration screens. This value is persisted in secure local storage.

---

## 2. Secure Local Storage
Session tokens (Access Token, Refresh Token) and user parameters are saved locally inside **EncryptedSharedPreferences**.
* The keys and values are encrypted using AES256-GCM.
* The encryption master key is dynamically managed by the Android Keystore system.

---

## 3. Network Security Configuration

The app uses `network_security_config.xml` to control allowed cleartext traffic:
```xml
<base-config cleartextTrafficPermitted="true" />
```
* **Cleartext Permitted**: True (required if connecting to a local development server or non-SSL HTTP endpoint on port 50000).
* **Production HTTPS Setup**: If you configure a production reverse proxy with SSL (e.g. Nginx on HTTPS port 443), you should update the config to block plain HTTP:
  ```xml
  <network-security-config>
      <base-config cleartextTrafficPermitted="false" />
      <!-- If you still need a local cleartext bypass for dev runs: -->
      <domain-config cleartextTrafficPermitted="true">
          <domain includeSubdomains="true">10.0.2.2</domain> <!-- Android Emulator host address -->
      </domain-config>
  </network-security-config>
  ```
