package com.example.reminder.auth;

import android.content.Context;
import android.content.Intent;
import com.example.reminder.utils.DeviceUtils;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.reminder.MainActivity;
import com.example.reminder.R;
import com.example.reminder.sync.SyncManager;

public class LoginActivity extends AppCompatActivity {

    private EditText emailInput;
    private EditText passwordInput;

    private Button loginButton;
    private TextView registerButton;
    private ProgressBar progressBar;

    private TokenManager tokenManager;
    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        com.example.reminder.utils.FileLogger.checkAndRequestStoragePermission(this);

        tokenManager = TokenManager.getInstance(this);
        authManager = AuthManager.getInstance(this);

        com.example.reminder.utils.FileLogger.log("LOGIN_ACTIVITY", "LoginActivity.onCreate()");
        String launchReason = getIntent().getStringExtra("launch_reason");
        if (launchReason == null) {
            launchReason = "Initial app launch / unauthenticated navigation";
        }
        com.example.reminder.utils.FileLogger.log("LOGIN_ACTIVITY", "Reason LoginActivity was launched: " + launchReason);
        com.example.reminder.utils.FileLogger.log("LOGIN_ACTIVITY", "TokenManager.isLoggedIn() = " + tokenManager.isLoggedIn());

        // If already logged in, redirect straight to MainActivity
        if (tokenManager.isLoggedIn()) {
            com.example.reminder.utils.FileLogger.log("LOGIN_ACTIVITY", "User already logged in according to TokenManager. Redirecting to MainActivity.");
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);

        loginButton = findViewById(R.id.btnLogin);
        registerButton = findViewById(R.id.btnGoToRegister);
        progressBar = findViewById(R.id.progressBar);

        com.example.reminder.utils.FileLogger.log("LOGIN_ACTIVITY", "Login screen displayed");

        loginButton.setOnClickListener(v -> {
            hideKeyboard();
            attemptLogin();
        });

        registerButton.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });
    }

    private void attemptLogin() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Email is required");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required");
            return;
        }

        setLoading(true);

        String deviceName = DeviceUtils.getDeviceName();
        String platform = "android";

        com.example.reminder.utils.FileLogger.log("LOGIN_ACTIVITY", "Login started: email=" + email + ", device=" + deviceName);

        authManager.login(email, password, deviceName, platform, new AuthManager.AuthCallback() {
            @Override
            public void onSuccess() {
                com.example.reminder.utils.FileLogger.log("LOGIN_ACTIVITY", "Login succeeded");
                com.example.reminder.utils.FileLogger.log("LOGIN_ACTIVITY", "Starting initial sync...");
                Toast.makeText(LoginActivity.this, "Authentication successful. Performing initial sync...", Toast.LENGTH_SHORT).show();
                
                // Trigger initial download sync
                SyncManager.getInstance(LoginActivity.this).performInitialSync(new SyncManager.SyncCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        setLoading(false);
                        com.example.reminder.utils.FileLogger.log("LOGIN_ACTIVITY", "Initial sync completed successfully. Launching MainActivity.");
                        Toast.makeText(LoginActivity.this, "Initial synchronization complete!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    }

                    @Override
                    public void onError(String error) {
                        setLoading(false);
                        com.example.reminder.utils.FileLogger.log("LOGIN_ACTIVITY", "Initial sync failed: " + error + ". Launching MainActivity offline.");
                        // Per constraints: If backend sync fails (e.g. network issue), the local app must continue working
                        Toast.makeText(LoginActivity.this, "Sync failure: " + com.example.reminder.utils.UIUtils.sanitizeError(LoginActivity.this, error) + ". Using local DB offline.", Toast.LENGTH_LONG).show();
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    }
                });
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                com.example.reminder.utils.FileLogger.log("LOGIN_ACTIVITY", "Login failed: " + message);
                Toast.makeText(LoginActivity.this, com.example.reminder.utils.UIUtils.sanitizeError(LoginActivity.this, message), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!isLoading);
        registerButton.setEnabled(!isLoading);
        emailInput.setEnabled(!isLoading);
        passwordInput.setEnabled(!isLoading);

    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
