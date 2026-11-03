package com.example.reminder;

import android.content.Intent;
import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.reminder.auth.AuthManager;
import com.example.reminder.auth.LoginActivity;
import com.example.reminder.auth.TokenManager;
import com.example.reminder.sync.SyncManager;
import com.example.reminder.ui.ThemeToggleView;
import com.example.reminder.utils.ThemeManager;
import com.example.reminder.utils.UIUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity implements ThemeManager.ThemeChangeListener {

    private ThemeToggleView settingsThemeToggle;
    private TextView txtCurrentThemeLabel;
    private RadioButton rbThemeSystem, rbThemeLight, rbThemeDark;
    private MaterialCardView cardThemeSystem, cardThemeLight, cardThemeDark;

    private TextView txtSettingsUsername;
    private TextView txtSettingsLastSync;
    private MaterialButton btnSettingsSync;
    private MaterialButton btnSettingsLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        settingsThemeToggle = findViewById(R.id.settingsThemeToggle);
        txtCurrentThemeLabel = findViewById(R.id.txtCurrentThemeLabel);
        rbThemeSystem = findViewById(R.id.rbThemeSystem);
        rbThemeLight = findViewById(R.id.rbThemeLight);
        rbThemeDark = findViewById(R.id.rbThemeDark);
        cardThemeSystem = findViewById(R.id.cardThemeSystem);
        cardThemeLight = findViewById(R.id.cardThemeLight);
        cardThemeDark = findViewById(R.id.cardThemeDark);

        txtSettingsUsername = findViewById(R.id.txtSettingsUsername);
        txtSettingsLastSync = findViewById(R.id.txtSettingsLastSync);
        btnSettingsSync = findViewById(R.id.btnSettingsSync);
        btnSettingsLogout = findViewById(R.id.btnSettingsLogout);

        updateThemeRadioSelection();

        cardThemeSystem.setOnClickListener(v -> selectTheme(ThemeManager.MODE_SYSTEM));
        cardThemeLight.setOnClickListener(v -> selectTheme(ThemeManager.MODE_LIGHT));
        cardThemeDark.setOnClickListener(v -> selectTheme(ThemeManager.MODE_DARK));

        setupAccountDetails();

        ThemeManager.registerListener(this);
    }

    private void selectTheme(int mode) {
        ThemeManager.setThemeMode(this, mode);
        updateThemeRadioSelection();
    }

    private void updateThemeRadioSelection() {
        int currentMode = ThemeManager.getThemeMode(this);
        boolean isDarkActive = ThemeManager.isDarkActive(this);

        rbThemeSystem.setChecked(currentMode == ThemeManager.MODE_SYSTEM);
        rbThemeLight.setChecked(currentMode == ThemeManager.MODE_LIGHT);
        rbThemeDark.setChecked(currentMode == ThemeManager.MODE_DARK);

        if (currentMode == ThemeManager.MODE_SYSTEM) {
            txtCurrentThemeLabel.setText("System Default (" + (isDarkActive ? "Dark" : "Light") + " active)");
        } else if (currentMode == ThemeManager.MODE_DARK) {
            txtCurrentThemeLabel.setText("Dark Mode 🌙");
        } else {
            txtCurrentThemeLabel.setText("Light Mode ☀️");
        }
    }

    private void setupAccountDetails() {
        TokenManager tokenManager = TokenManager.getInstance(this);
        String username = tokenManager.getUsername();
        if (username != null && !username.isEmpty()) {
            txtSettingsUsername.setText(username);
        } else {
            txtSettingsUsername.setText("Active User");
        }

        updateLastSyncText();

        btnSettingsSync.setOnClickListener(v -> {
            btnSettingsSync.setEnabled(false);
            Toast.makeText(this, "Syncing...", Toast.LENGTH_SHORT).show();
            SyncManager.getInstance(this).performFullSync(new SyncManager.SyncCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    runOnUiThread(() -> {
                        btnSettingsSync.setEnabled(true);
                        updateLastSyncText();
                        Toast.makeText(SettingsActivity.this, "Sync completed successfully!", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        btnSettingsSync.setEnabled(true);
                        Toast.makeText(SettingsActivity.this, "Sync failed: " + UIUtils.sanitizeError(SettingsActivity.this, error), Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

        btnSettingsLogout.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Log Out")
                    .setMessage("Are you sure you want to log out? Local data will remain safely cached.")
                    .setPositiveButton("Log Out", (dialog, which) -> {
                        AuthManager.getInstance(this).logout(new AuthManager.AuthCallback() {
                            @Override
                            public void onSuccess() {
                                Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            }

                            @Override
                            public void onError(String message) {
                                Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            }
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void updateLastSyncText() {
        long lastSync = TokenManager.getInstance(this).getLastSyncTimestamp();
        if (lastSync == 0) {
            txtSettingsLastSync.setText("Never");
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.getDefault());
            txtSettingsLastSync.setText(sdf.format(new Date(lastSync)));
        }
    }

    @Override
    public void onThemeChanged(int mode, boolean isDarkActive) {
        runOnUiThread(this::updateThemeRadioSelection);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ThemeManager.unregisterListener(this);
    }
}

