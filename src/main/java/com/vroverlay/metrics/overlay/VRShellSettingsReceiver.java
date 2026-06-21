package com.vroverlay.metrics.overlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.vroverlay.metrics.OverlayApplication;
import com.vroverlay.metrics.sdk.SimpleSettingsConfig;
import com.vroverlay.metrics.rendering.NativeOverlayRenderer;

public class VRShellSettingsReceiver extends BroadcastReceiver {
    private static final String TAG = "VRShellSettingsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "VR shell settings changed, refreshing overlay");

        var renderer = com.vroverlay.metrics.rendering.OverlayRenderingManager.get();
        if (renderer != null && renderer.isOverlayVisible()) {
            renderer.hideOverlay();
            
            // Get settings config from the overlay manager
            if (OverlayApplication.overlayManager != null) {
                SimpleSettingsConfig settings = OverlayApplication.overlayManager.getSettingsConfig();
                if (renderer instanceof NativeOverlayRenderer) {
                    ((NativeOverlayRenderer) renderer).showOverlay(settings);
                } else {
                    Log.w(TAG, "Renderer is not NativeOverlayRenderer, using default showOverlay()");
                    renderer.showOverlay();
                }
            } else {
                Log.e(TAG, "OverlayApplication.overlayManager is null, cannot restart overlay");
            }
        }
    }
}
