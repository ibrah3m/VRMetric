package com.vroverlay.metrics;

import android.app.Application;
import android.content.Intent;
import android.util.Log;
import com.vroverlay.metrics.sdk.VROverlayManager;

public class OverlayApplication extends Application {
    private static final String TAG = "OverlayApplication";

    public static VROverlayManager overlayManager;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "Creating VR Overlay Application");

        overlayManager = new VROverlayManager(this);
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
