package com.vroverlay.metrics.sdk;

import android.graphics.Point;
import android.util.Log;
import androidx.annotation.NonNull;
import com.oculus.ovrmonitormetricsservice.PerfDebugOverlay;
import com.oculus.ovrmonitormetricsservice.rendering.NativeOverlayRenderer;

public class VROverlayManager {
    private static final String TAG = "VROverlayManager";

    public static final int OVERLAY_WIDTH = 256;
    public static final int OVERLAY_HEIGHT = 136;

    private SimpleSettingsConfig settingsConfig;

    public VROverlayManager(@NonNull SimpleSettingsConfig config) {
        this.settingsConfig = config;
        showHelloWorld();
    }

    private void showHelloWorld() {
        try {
            PerfDebugOverlay overlay = settingsConfig.getPerfDebugOverlay();
            if (overlay != null) {
                overlay.SetDebugData("##  !!  ##");
                Log.i(TAG, "Hello World debug data set on overlay");
            } else {
                Log.e(TAG, "PerfDebugOverlay is null in showHelloWorld");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to set Hello World", e);
        }
    }

    public void updateDebugString(String debugString) {
        try {
            PerfDebugOverlay overlay = settingsConfig.getPerfDebugOverlay();
            if (overlay != null) {
                overlay.SetDebugData(debugString);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to set debug data", e);
        }
    }

    @NonNull
    public Point getOverlayDimensions() {
        PerfDebugOverlay overlay = settingsConfig.getPerfDebugOverlay();
        if (overlay != null) {
            return overlay.GetTextureDimensions();
        }
        return new Point(OVERLAY_WIDTH, OVERLAY_HEIGHT);
    }

    public boolean isOverlayEnabled() {
        return settingsConfig.isOverlayEnabled();
    }

    public void setOverlayEnabled(boolean enabled) {
        settingsConfig.enableOverlay(enabled);
    }

    public void setOverlayTransform(float pitch, float yaw, float scale, float distance, boolean headLocked) {
        settingsConfig.setOverlayPitch(pitch);
        settingsConfig.setOverlayYaw(yaw);
        settingsConfig.setOverlayScale(scale);
        settingsConfig.setOverlayDistance(distance);
        settingsConfig.setOverlayHeadLocked(headLocked);
    }

    public void setNativeBackend(NativeOverlayRenderer.Backend backend) {
        settingsConfig.setPreferredNativeRendererBackend(backend);
    }

    @NonNull
    public SimpleSettingsConfig getSettingsConfig() {
        return settingsConfig;
    }
}
