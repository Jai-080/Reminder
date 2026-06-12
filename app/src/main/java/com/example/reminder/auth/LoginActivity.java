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

public class LoginActivity extends AppCompatActivity {

    private EditText emailInput;
    private EditText passwordInput;
    private EditText baseUrlInput;
    private Button loginButton;
    private TextView registerButton;
    private Button bypassButton;
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

        tokenManager = TokenManager.getInstance(this);
        authManager = AuthManager.getInstance(this);

        // If already logged in, redirect straight to MainActivity
        if (tokenManager.isLoggedIn()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        baseUrlInput = findViewById(R.id.baseUrlInput);
        loginButton = findViewById(R.id.btnLogin);
        registerButton = findViewById(R.id.btnGoToRegister);
        bypassButton = findViewById(R.id.btnBypass);
        progressBar = findViewById(R.id.progressBar);

        // Pre-populate Base URL from token manager
        baseUrlInput.setText(tokenManager.getBaseUrl());

        loginButton.setOnClickListener(v -> {
            hideKeyboard();
            attemptLogin();
        });

        registerButton.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });

        bypassButton.setOnClickListener(v -> {
            // Save dummy session tokens to bypass auth check and use local DB offline
            tokenManager.saveSession("developer_bypass", "developer_bypass", -1L, "Developer");
            tokenManager.setBaseUrl(baseUrlInput.getText().toString().trim());
            Toast.makeText(this, "Logged in via Developer Bypass (Offline Mode)", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        });
    }

    private void attemptLogin() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String baseUrl = baseUrlInput.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Email is required");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required");
            return;
        }
        if (TextUtils.isEmpty(baseUrl)) {
            baseUrlInput.setError("Base Server URL is required");
            return;
        }

        // Save server URL configuration
        tokenManager.setBaseUrl(baseUrl);

        setLoading(true);

        String deviceName = Build.MANUFACTURER + " " + Build.MODEL;
        String platform = "android";

        authManager.login(email, password, deviceName, platform, new AuthManager.AuthCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(LoginActivity.this, "Authentication successful. Performing initial sync...", Toast.LENGTH_SHORT).show();
                
                // Trigger initial download sync
                SyncManager.getInstance(LoginActivity.this).performInitialSync(new SyncManager.SyncCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        setLoading(false);
                        Toast.makeText(LoginActivity.this, "Initial synchronization complete!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    }

                    @Override
                    public void onError(String error) {
                        setLoading(false);
                        // Per constraints: If backend sync fails (e.g. network issue), the local app must continue working
                        Toast.makeText(LoginActivity.this, "Sync failure: " + error + ". Using local DB offline.", Toast.LENGTH_LONG).show();
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    }
                });
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!isLoading);
        registerButton.setEnabled(!isLoading);
        bypassButton.setEnabled(!isLoading);
        emailInput.setEnabled(!isLoading);
        passwordInput.setEnabled(!isLoading);
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
