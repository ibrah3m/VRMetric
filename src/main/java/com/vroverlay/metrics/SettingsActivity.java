package com.vroverlay.metrics;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class SettingsActivity extends Activity {
    private static final String TAG = "SettingsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Launching native overlay service");

        startOverlayService();

        finish();
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
