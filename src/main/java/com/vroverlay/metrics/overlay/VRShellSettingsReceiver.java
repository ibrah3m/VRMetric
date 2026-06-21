package com.vroverlay.metrics.overlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.oculus.ovrmonitormetricsservice.rendering.NativeOverlayRenderer;
import com.vroverlay.metrics.OverlayApplication;
import com.vroverlay.metrics.rendering.OverlayRenderingManager;
import com.vroverlay.metrics.sdk.SimpleSettingsConfig;

public class VRShellSettingsReceiver extends BroadcastReceiver {
    private static final String TAG = "VRShellSettingsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "VR shell settings changed, refreshing overlay");

        var renderer = OverlayRenderingManager.get();
        if (renderer != null && renderer.isOverlayVisible()) {
            renderer.hideOverlay();

            SimpleSettingsConfig settings = OverlayApplication.settingsConfig;
            if (settings != null && renderer instanceof NativeOverlayRenderer) {
                ((NativeOverlayRenderer) renderer).showOverlay(settings);
            }
        }
    }
}
