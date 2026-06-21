package com.vroverlay.metrics;

import android.app.Application;
import android.content.Intent;
import android.util.Log;
import com.vroverlay.metrics.sdk.VROverlayManager;
import com.vroverlay.metrics.sdk.SimpleSettingsConfig;

public class OverlayApplication extends Application {
    private static final String TAG = "OverlayApplication";

    public static VROverlayManager overlayManager;
    public static SimpleSettingsConfig settingsConfig;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "Creating VR Overlay Application");

        settingsConfig = new SimpleSettingsConfig(getApplicationContext());
        overlayManager = new VROverlayManager(settingsConfig);
        Log.i(TAG, "VR Overlay Manager created with native rendering");

        startOverlayService();
    }

    private void startOverlayService() {
        try {
            Intent serviceIntent = new Intent(this, OverlayService.class);
            startForegroundService(serviceIntent);
            Log.i(TAG, "OverlayService started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start OverlayService", e);
        }
    }

    public static void updateMetrics(String metricsJson) {
        if (overlayManager != null) {
            overlayManager.updateDebugString(metricsJson);
        }
    }
}
