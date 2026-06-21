package com.vroverlay.metrics;

import android.app.Application;
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
    }

    public static void updateDebugString(String text) {
        if (overlayManager != null) {
            overlayManager.updateDebugString(text);
        }
    }
}
