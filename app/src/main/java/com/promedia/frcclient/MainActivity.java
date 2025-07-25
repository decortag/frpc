// app/src/main/java/com/promedia/frcclient/MainActivity.java
package com.promedia.frcclient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FRPClient_MainActivity";
    private static final String PREFS_NAME = "FRPClientPrefs";
    private static final String KEY_CONFIG = "frp_config";
    private static final String KEY_AUTO_START = "frp_auto_start";

    private EditText configEditText;
    private Button toggleServiceButton;
    private Button autoStartToggleButton;
    private TextView statusTextView;
    private TextView logTextView;
    private ScrollView logScrollView;

    private boolean isServiceRunning = false;
    private boolean isAutoStartEnabled = false;

    // Receiver for status updates and logs from FRPService
    private BroadcastReceiver serviceUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                String status = intent.getStringExtra("status");
                String logMessage = intent.getStringExtra("log");

                if (status != null) {
                    statusTextView.setText("Status: " + status);
                    Log.d(TAG, "Received status: " + status);
                    if (status.contains("Running")) {
                        isServiceRunning = true;
                        toggleServiceButton.setText("Stop FRP Service");
                        toggleServiceButton.setBackgroundColor(Color.parseColor("#FF4CAF50")); // Green
                    } else {
                        isServiceRunning = false;
                        toggleServiceButton.setText("Start FRP Service");
                        toggleServiceButton.setBackgroundColor(Color.parseColor("#FF2196F3")); // Blue
                    }
                }
                if (logMessage != null) {
                    logTextView.append(logMessage + "\n");
                    Log.d(TAG, "Received log: " + logMessage);
                    // Scroll to the bottom
                    logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupUI();
        loadConfiguration();
        updateAutoStartButton();

        // Register receiver for service updates
        LocalBroadcastManager.getInstance(this).registerReceiver(
                serviceUpdateReceiver, new IntentFilter(FRPService.ACTION_SERVICE_STATUS_UPDATE));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Request current service status when activity resumes
        Intent intent = new Intent(this, FRPService.class);
        intent.setAction(FRPService.ACTION_REQUEST_STATUS);
        startService(intent); // Start service if not running, or just send intent if it is
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceUpdateReceiver);
    }

    private void setupUI() {
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(16, 16, 16, 16);
        mainLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        mainLayout.setBackgroundColor(Color.parseColor("#F5F5F5")); // Light grey background

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0, 8, 0, 8); // Add some vertical margin

        // Title TextView
        TextView titleTextView = new TextView(this);
        titleTextView.setText("FRP Android Client");
        titleTextView.setTextSize(24);
        titleTextView.setTextColor(Color.BLACK);
        titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleTextView.setPadding(0, 16, 0, 16);
        mainLayout.addView(titleTextView);

        // Configuration EditText
        configEditText = new EditText(this);
        configEditText.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 300)); // Fixed height for config
        configEditText.setHint("Paste frpc.ini configuration here...");
        configEditText.setGravity(Gravity.TOP | Gravity.START);
        configEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        configEditText.setBackgroundColor(Color.WHITE);
        configEditText.setPadding(16, 16, 16, 16);
        configEditText.setTextColor(Color.BLACK);
        configEditText.setHintTextColor(Color.GRAY);
        mainLayout.addView(configEditText, layoutParams);

        // Toggle Service Button
        toggleServiceButton = new Button(this);
        toggleServiceButton.setText("Start FRP Service");
        toggleServiceButton.setTextColor(Color.WHITE);
        toggleServiceButton.setBackgroundColor(Color.parseColor("#FF2196F3")); // Blue
        toggleServiceButton.setPadding(16, 16, 16, 16);
        // Rounded corners for buttons (programmatic)
        toggleServiceButton.setClipToOutline(true);
        toggleServiceButton.setBackgroundResource(android.R.drawable.btn_default); // Default button style for rounded corners
        mainLayout.addView(toggleServiceButton, layoutParams);

        // Auto-start Toggle Button
        autoStartToggleButton = new Button(this);
        autoStartToggleButton.setText("Enable Auto-start");
        autoStartToggleButton.setTextColor(Color.WHITE);
        autoStartToggleButton.setBackgroundColor(Color.parseColor("#FF9C27B0")); // Purple
        autoStartToggleButton.setPadding(16, 16, 16, 16);
        autoStartToggleButton.setClipToOutline(true);
        autoStartToggleButton.setBackgroundResource(android.R.drawable.btn_default);
        mainLayout.addView(autoStartToggleButton, layoutParams);

        // Status TextView
        statusTextView = new TextView(this);
        statusTextView.setText("Status: Idle");
        statusTextView.setTextSize(16);
        statusTextView.setTextColor(Color.BLACK);
        statusTextView.setPadding(0, 16, 0, 8);
        mainLayout.addView(statusTextView, layoutParams);

        // Log TextView (inside ScrollView)
        logScrollView = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f); // Takes remaining space
        logScrollView.setLayoutParams(scrollParams);
        logScrollView.setBackgroundColor(Color.BLACK); // Dark background for logs
        logScrollView.setPadding(8, 8, 8, 8);

        logTextView = new TextView(this);
        logTextView.setTextSize(12);
        logTextView.setTextColor(Color.WHITE);
        logTextView.setText("FRP Client Logs:\n");
        logTextView.setGravity(Gravity.BOTTOM); // New logs appear at bottom
        logScrollView.addView(logTextView);
        mainLayout.addView(logScrollView);

        setContentView(mainLayout);

        // Button Listeners
        toggleServiceButton.setOnClickListener(v -> {
            String config = configEditText.getText().toString();
            saveConfiguration(config); // Save config always when toggling

            if (isServiceRunning) {
                stopFRPService();
            } else {
                startFRPService(config);
            }
        });

        autoStartToggleButton.setOnClickListener(v -> {
            isAutoStartEnabled = !isAutoStartEnabled;
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
            editor.putBoolean(KEY_AUTO_START, isAutoStartEnabled);
            editor.apply();
            updateAutoStartButton();
            logTextView.append("Auto-start " + (isAutoStartEnabled ? "enabled" : "disabled") + "\n");
        });
    }

    private void loadConfiguration() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedConfig = prefs.getString(KEY_CONFIG, "");
        isAutoStartEnabled = prefs.getBoolean(KEY_AUTO_START, false);
        configEditText.setText(savedConfig);
    }

    private void saveConfiguration(String config) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putString(KEY_CONFIG, config);
        editor.apply();
        logTextView.append("Configuration saved.\n");
    }

    private void updateAutoStartButton() {
        if (isAutoStartEnabled) {
            autoStartToggleButton.setText("Disable Auto-start");
            autoStartToggleButton.setBackgroundColor(Color.parseColor("#FFD32F2F")); // Red
        } else {
            autoStartToggleButton.setText("Enable Auto-start");
            autoStartToggleButton.setBackgroundColor(Color.parseColor("#FF9C27B0")); // Purple
        }
    }

    private void startFRPService(String config) {
        if (config.trim().isEmpty()) {
            statusTextView.setText("Status: Error - Configuration is empty.");
            logTextView.append("Error: Configuration is empty. Please paste frpc.ini content.\n");
            return;
        }

        Intent serviceIntent = new Intent(this, FRPService.class);
        serviceIntent.setAction(FRPService.ACTION_START_FRP);
        serviceIntent.putExtra("config", config);
        // For Android O (API 26) and above, use startForegroundService
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        statusTextView.setText("Status: Starting FRP Service...");
        logTextView.append("Attempting to start FRP Service...\n");
    }

    private void stopFRPService() {
        Intent serviceIntent = new Intent(this, FRPService.class);
        serviceIntent.setAction(FRPService.ACTION_STOP_FRP);
        startService(serviceIntent);
        statusTextView.setText("Status: Stopping FRP Service...");
        logTextView.append("Attempting to stop FRP Service...\n");
    }
}
