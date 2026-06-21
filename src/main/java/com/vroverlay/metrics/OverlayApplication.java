package com.vroverlay.metrics;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

public class OverlayApplication extends Application {
    private static final String TAG = "OverlayApplication";

    public static PerfDebugOverlayWeb overlay;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "Creating VR Overlay Application");

        overlay = new PerfDebugOverlayWeb(this);
        Log.i(TAG, "Overlay WebView created");

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
}
