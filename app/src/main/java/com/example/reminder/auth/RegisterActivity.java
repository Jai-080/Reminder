package com.example.reminder.auth;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
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

public class RegisterActivity extends AppCompatActivity {

    private EditText usernameInput;
    private EditText emailInput;
    private EditText passwordInput;
    private EditText deviceNameInput;
    private EditText baseUrlInput;
    private Button registerButton;
    private TextView backToLoginButton;
    private Button bypassButton;
    private ProgressBar progressBar;

    private TokenManager tokenManager;
    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        tokenManager = TokenManager.getInstance(this);
        authManager = AuthManager.getInstance(this);

        usernameInput = findViewById(R.id.usernameInput);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        deviceNameInput = findViewById(R.id.deviceNameInput);
        baseUrlInput = findViewById(R.id.baseUrlInput);
        registerButton = findViewById(R.id.btnRegister);
        backToLoginButton = findViewById(R.id.btnBackToLogin);
        bypassButton = findViewById(R.id.btnBypass);
        progressBar = findViewById(R.id.progressBar);

        // Pre-populate fields
        baseUrlInput.setText(tokenManager.getBaseUrl());
        deviceNameInput.setText(Build.MANUFACTURER + " " + Build.MODEL);

        registerButton.setOnClickListener(v -> {
            hideKeyboard();
            attemptRegister();
        });

        backToLoginButton.setOnClickListener(v -> {
            finish();
        });

        bypassButton.setOnClickListener(v -> {
            tokenManager.saveSession("developer_bypass", "developer_bypass", -1L, "Developer");
            tokenManager.setBaseUrl(baseUrlInput.getText().toString().trim());
            Toast.makeText(this, "Logged in via Developer Bypass (Offline Mode)", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void attemptRegister() {
        String username = usernameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String deviceName = deviceNameInput.getText().toString().trim();
        String baseUrl = baseUrlInput.getText().toString().trim();

        if (TextUtils.isEmpty(username)) {
            usernameInput.setError("Username is required");
            return;
        }
        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Email is required");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required");
            return;
        }
        if (password.length() < 8) {
            passwordInput.setError("Password must be at least 8 characters");
            return;
        }
        if (TextUtils.isEmpty(deviceName)) {
            deviceNameInput.setError("Device Name is required");
            return;
        }
        if (TextUtils.isEmpty(baseUrl)) {
            baseUrlInput.setError("Base Server URL is required");
            return;
        }

        // Save server URL configuration
        tokenManager.setBaseUrl(baseUrl);

        setLoading(true);

        String platform = "android";

        authManager.register(username, email, password, deviceName, platform, new AuthManager.AuthCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(RegisterActivity.this, "Registration successful. Performing initial sync...", Toast.LENGTH_SHORT).show();

                SyncManager.getInstance(RegisterActivity.this).performInitialSync(new SyncManager.SyncCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        setLoading(false);
                        Toast.makeText(RegisterActivity.this, "Sync complete!", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onError(String error) {
                        setLoading(false);
                        Toast.makeText(RegisterActivity.this, "Sync failure: " + com.example.reminder.utils.UIUtils.sanitizeError(RegisterActivity.this, error) + ". Using local DB offline.", Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                });
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(RegisterActivity.this, com.example.reminder.utils.UIUtils.sanitizeError(RegisterActivity.this, message), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        registerButton.setEnabled(!isLoading);
        backToLoginButton.setEnabled(!isLoading);
        bypassButton.setEnabled(!isLoading);
        usernameInput.setEnabled(!isLoading);
        emailInput.setEnabled(!isLoading);
        passwordInput.setEnabled(!isLoading);
        deviceNameInput.setEnabled(!isLoading);
        baseUrlInput.setEnabled(!isLoading);
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
