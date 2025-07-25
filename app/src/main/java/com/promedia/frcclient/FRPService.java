// app/src/main/java/com/promedia/frcclient/FRPService.java
package com.promedia.frcclient;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FRPService extends Service {

    private static final String TAG = "FRPClient_FRPService";
    private static final String CHANNEL_ID = "FRPClientServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_START_FRP = "com.promedia.frcclient.ACTION_START_FRP";
    public static final String ACTION_STOP_FRP = "com.promedia.frcclient.ACTION_STOP_FRP";
    public static final String ACTION_REQUEST_STATUS = "com.promedia.frcclient.ACTION_REQUEST_STATUS";
    public static final String ACTION_SERVICE_STATUS_UPDATE = "com.promedia.frcclient.ACTION_SERVICE_STATUS_UPDATE";

    private FRPClient frpClient;
    private ScheduledExecutorService scheduler;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private String currentConfig = "";
    private boolean isAttemptingConnection = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");
        createNotificationChannel();
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand: " + (intent != null ? intent.getAction() : "null"));

        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_FRP.equals(action)) {
                String config = intent.getStringExtra("config");
                if (config != null && !config.isEmpty()) {
                    currentConfig = config;
                    startFRP(currentConfig);
                } else {
                    logAndBroadcast("Error: Configuration missing from intent. Cannot start FRP.");
                    stopSelf(); // Stop service if no config
                }
            } else if (ACTION_STOP_FRP.equals(action)) {
                stopFRP();
            } else if (ACTION_REQUEST_STATUS.equals(action)) {
                // Main Activity is requesting status, send current status
                sendServiceStatusUpdate();
            }
        } else {
            // Service restarted by system (e.g., after being killed)
            // If currentConfig is not empty, try to restart FRP
            if (!currentConfig.isEmpty()) {
                logAndBroadcast("Service restarted by system. Attempting to resume FRP connection.");
                startFRP(currentConfig);
            } else {
                logAndBroadcast("Service restarted by system, but no previous configuration found. Stopping.");
                stopSelf();
            }
        }

        // START_STICKY means the service will be recreated if it's killed by the system
        // but the last intent will not be re-delivered.
        return START_STICKY;
    }

    private void startFRP(String config) {
        if (frpClient != null && frpClient.isConnected()) {
            logAndBroadcast("FRP Client already running and connected.");
            sendServiceStatusUpdate();
            return;
        }
        if (isAttemptingConnection) {
            logAndBroadcast("Already attempting connection. Please wait.");
            return;
        }

        logAndBroadcast("Starting FRP Client with provided configuration...");
        isAttemptingConnection = true;
        sendServiceStatusUpdate();

        // Start as foreground service
        startForeground(NOTIFICATION_ID, buildNotification("FRP Client: Connecting..."));

        // Initialize and start FRPClient in a separate thread
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow(); // Stop any previous retry attempts
        }
        scheduler = Executors.newSingleThreadScheduledExecutor();

        // Initial connection attempt
        scheduler.execute(() -> connectFRP(config));

        // Set up network callback for automatic reconnection
        registerNetworkCallback();
    }

    private void connectFRP(String config) {
        mainHandler.post(() -> logAndBroadcast("Attempting to connect to FRP server..."));
        try {
            if (frpClient == null) {
                frpClient = new FRPClient(config, new FRPClient.FRPClientListener() {
                    @Override
                    public void onConnected() {
                        mainHandler.post(() -> {
                            isAttemptingConnection = false;
                            logAndBroadcast("FRP Client Connected!");
                            updateNotification("FRP Client: Connected");
                            sendServiceStatusUpdate();
                        });
                    }

                    @Override
                    public void onDisconnected(String reason) {
                        mainHandler.post(() -> {
                            logAndBroadcast("FRP Client Disconnected: " + reason);
                            updateNotification("FRP Client: Disconnected. Retrying...");
                            isAttemptingConnection = true; // Re-enable retry logic
                            sendServiceStatusUpdate();
                            scheduleReconnect();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        mainHandler.post(() -> {
                            logAndBroadcast("FRP Client Error: " + error);
                            updateNotification("FRP Client: Error. Retrying...");
                            isAttemptingConnection = true; // Re-enable retry logic
                            sendServiceStatusUpdate();
                            scheduleReconnect();
                        });
                    }

                    @Override
                    public void onLog(String message) {
                        mainHandler.post(() -> logAndBroadcast(message));
                    }
                });
            }

            // Attempt connection
            frpClient.connect();

        } catch (Exception e) {
            mainHandler.post(() -> {
                logAndBroadcast("FRP Client initialization error: " + e.getMessage());
                isAttemptingConnection = true; // Re-enable retry logic
                sendServiceStatusUpdate();
                scheduleReconnect();
            });
        }
    }

    private void scheduleReconnect() {
        if (!isAttemptingConnection) {
            // Only schedule reconnect if not already trying
            isAttemptingConnection = true;
            logAndBroadcast("Scheduling reconnect in 5 seconds...");
            scheduler.schedule(() -> {
                if (isNetworkAvailable()) {
                    connectFRP(currentConfig);
                } else {
                    logAndBroadcast("Network not available, postponing reconnect.");
                    scheduleReconnect(); // Reschedule if network is still down
                }
            }, 5, TimeUnit.SECONDS);
        }
    }

    private void stopFRP() {
        logAndBroadcast("Stopping FRP Client...");
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (frpClient != null) {
            frpClient.disconnect("User stopped service.");
            frpClient = null;
        }
        isAttemptingConnection = false;
        unregisterNetworkCallback();
        stopForeground(true);
        stopSelf(); // Stop the service itself
        logAndBroadcast("FRP Client Stopped.");
        sendServiceStatusUpdate();
    }

    private void registerNetworkCallback() {
        if (networkCallback == null) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    super.onAvailable(network);
                    mainHandler.post(() -> {
                        logAndBroadcast("Network became available. Attempting reconnection...");
                        if (!isAttemptingConnection && !currentConfig.isEmpty()) {
                            // If not already trying to connect and config exists, try to connect
                            isAttemptingConnection = true;
                            scheduler.execute(() -> connectFRP(currentConfig));
                        }
                    });
                }

                @Override
                public void onLost(Network network) {
                    super.onLost(network);
                    mainHandler.post(() -> {
                        logAndBroadcast("Network lost. Disconnecting FRP client.");
                        if (frpClient != null) {
                            frpClient.disconnect("Network lost.");
                        }
                        isAttemptingConnection = true; // Ensure retry logic kicks in
                        updateNotification("FRP Client: Network lost.");
                        sendServiceStatusUpdate();
                    });
                }
            };

            NetworkRequest.Builder builder = new NetworkRequest.Builder();
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                connectivityManager.registerNetworkCallback(builder.build(), networkCallback);
            } else {
                // For older APIs, this might be less granular.
                // We rely more on the infinite retry logic for network availability.
                connectivityManager.registerNetworkCallback(builder.build(), networkCallback);
            }
            logAndBroadcast("Network callback registered.");
        }
    }

    private void unregisterNetworkCallback() {
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
                networkCallback = null;
                logAndBroadcast("Network callback unregistered.");
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Network callback was not registered: " + e.getMessage());
            }
        }
    }

    private boolean isNetworkAvailable() {
        if (connectivityManager == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = connectivityManager.getActiveNetwork();
            if (activeNetwork == null) return false;
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
            return capabilities != null &&
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            android.net.NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "FRP Client Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification buildNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE); // Use FLAG_IMMUTABLE for API 23+

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("FRP Client Running")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_lock_lock) // Simple lock icon
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String contentText) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(contentText));
        }
    }

    private void logAndBroadcast(String message) {
        Log.d(TAG, message);
        Intent intent = new Intent(ACTION_SERVICE_STATUS_UPDATE);
        intent.putExtra("log", message);
        // Also update status if it's a critical message
        if (message.contains("Connected") || message.contains("Disconnected") || message.contains("Error") || message.contains("Stopping")) {
            intent.putExtra("status", message);
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void sendServiceStatusUpdate() {
        Intent intent = new Intent(ACTION_SERVICE_STATUS_UPDATE);
        String status = "Idle";
        if (frpClient != null && frpClient.isConnected()) {
            status = "Running and Connected";
        } else if (isAttemptingConnection) {
            status = "Running (Attempting Connection)";
        } else if (frpClient != null && !frpClient.isConnected()) {
            status = "Running (Disconnected)";
        }
        intent.putExtra("status", status);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not using bound service
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service onDestroy");
        stopFRP(); // Ensure everything is cleaned up
    }
}
