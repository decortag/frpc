// app/src/main/java/com/promedia/frcclient/BootReceiver.java
package com.promedia.frcclient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "FRPClient_BootReceiver";
    private static final String PREFS_NAME = "FRPClientPrefs";
    private static final String KEY_CONFIG = "frp_config";
    private static final String KEY_AUTO_START = "frp_auto_start";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Boot completed received. Checking auto-start preference.");

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean isAutoStartEnabled = prefs.getBoolean(KEY_AUTO_START, false);
            String savedConfig = prefs.getString(KEY_CONFIG, "");

            if (isAutoStartEnabled && !savedConfig.isEmpty()) {
                Log.d(TAG, "Auto-start enabled and configuration found. Starting FRPService.");
                Intent serviceIntent = new Intent(context, FRPService.class);
                serviceIntent.setAction(FRPService.ACTION_START_FRP);
                serviceIntent.putExtra("config", savedConfig);

                // For Android O (API 26) and above, use startForegroundService
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } else {
                Log.d(TAG, "Auto-start is disabled or no configuration found. Not starting FRPService.");
            }
        }
    }
}
