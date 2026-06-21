package com.vroverlay.metrics;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

public class OverlayApplication extends Application {
    private static final String TAG = "OverlayApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "Creating VR Overlay Application");

        startOverlayService();
    }

    private void startOverlayService() {
        try {
            Intent serviceIntent = new Intent(this, OverlayServiceWeb.class);
            startForegroundService(serviceIntent);
            Log.i(TAG, "OverlayServiceWeb started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start OverlayServiceWeb", e);
        }
    }
}
