package com.vroverlay.metrics.sdk;

import android.content.Context;
import com.oculus.ovrmonitormetricsservice.PerfDebugOverlay;
import com.oculus.ovrmonitormetricsservice.device.DeviceProperties;
import com.oculus.ovrmonitormetricsservice.device.DeviceType;
import com.oculus.ovrmonitormetricsservice.device.SettingsConfig;
import com.vroverlay.metrics.rendering.NativeOverlayRenderer;
import com.vroverlay.metrics.settings.UsageSettings;

public class SimpleSettingsConfig extends SettingsConfig {
    private boolean statsEnabled = true;
    private boolean graphsEnabled = true;
    private boolean debugDataEnabled = true;
    private boolean appNameEnabled = false;
    private boolean playTimeEnabled = false;
    private boolean overlayEnabled = true;
    private boolean customStatOrderEnabled = false;
    private boolean customGraphOrderEnabled = false;
    private boolean appStatsAndGraphsHidden = false;
    private float overlayScale = 1.0f;
    private float overlayDistance = 1.0f;
    private float overlayPitch = 0.0f;
    private float overlayYaw = 0.0f;
    private boolean overlayHeadLocked = false;
    private NativeOverlayRenderer.Backend preferredBackend = NativeOverlayRenderer.Backend.OPENXR_VULKAN;
    private Context context;
    private PerfDebugOverlay perfDebugOverlay;
    private UsageSettings usageSettings;

    public SimpleSettingsConfig() {
        this.perfDebugOverlay = new PerfDebugOverlay(this);
    }

    public SimpleSettingsConfig(Context context) {
        this.context = context;
        this.usageSettings = new UsageSettings(context);
        this.perfDebugOverlay = new PerfDebugOverlay(this);

        loadFromStorage();
    }

    private void loadFromStorage() {
        overlayEnabled = usageSettings.isOverlayEnabled();
        overlayScale = usageSettings.getOverlayScale();
        overlayDistance = usageSettings.getOverlayDistance();
        overlayPitch = usageSettings.getOverlayPitch();
        overlayYaw = usageSettings.getOverlayYaw();
        overlayHeadLocked = usageSettings.isOverlayHeadLocked();

        int backend = usageSettings.getOverlayBackend();
        preferredBackend = backend == 0 ? NativeOverlayRenderer.Backend.VRAPI_GLES : NativeOverlayRenderer.Backend.OPENXR_VULKAN;
    }

    private void saveToStorage() {
        if (usageSettings != null) {
            usageSettings.setOverlayEnabled(overlayEnabled);
            usageSettings.setOverlayScale(overlayScale);
            usageSettings.setOverlayDistance(overlayDistance);
            usageSettings.setOverlayPitch(overlayPitch);
            usageSettings.setOverlayYaw(overlayYaw);
            usageSettings.setOverlayHeadLocked(overlayHeadLocked);
            usageSettings.setOverlayBackend(preferredBackend.ordinal());
        }
    }

    @Override public void enableOverlay(boolean z) {
        overlayEnabled = z;
        saveToStorage();
    }

    @Override public boolean isOverlayEnabled() {
        return overlayEnabled;
    }

    @Override public void setOverlayScale(float f) {
        overlayScale = f;
        saveToStorage();
    }

    @Override public void setOverlayDistance(float f) {
        overlayDistance = f;
        saveToStorage();
    }

    @Override public void setOverlayPitch(float f) {
        overlayPitch = f;
        saveToStorage();
    }

    @Override public void setOverlayYaw(float f) {
        overlayYaw = f;
        saveToStorage();
    }

    @Override public void setOverlayHeadLocked(boolean z) {
        overlayHeadLocked = z;
        saveToStorage();
    }

    public Context getContext() {
        return context;
    }

    public NativeOverlayRenderer.Backend getPreferredNativeRendererBackend() {
        return preferredBackend;
    }

    public void setPreferredNativeRendererBackend(NativeOverlayRenderer.Backend backend) {
        this.preferredBackend = backend;
        saveToStorage();
    }

    public void setOverlayTransform(float pitch, float yaw, float scale, float distance, boolean headLocked) {
        this.overlayPitch = pitch;
        this.overlayYaw = yaw;
        this.overlayScale = scale;
        this.overlayDistance = distance;
        this.overlayHeadLocked = headLocked;
        saveToStorage();
    }

    @Override public PerfDebugOverlay getPerfDebugOverlay() {
        return perfDebugOverlay;
    }

    @Override public void allowOverlayCapture(boolean z) {}
    @Override public boolean areAppStatsAndGraphsHidden() { return appStatsAndGraphsHidden; }
    @Override public boolean canRequestPermissions() { return false; }
    @Override public void enableAppName(boolean z) { appNameEnabled = z; }
    @Override public void enableCustomGraphOrder(boolean z) { customGraphOrderEnabled = z; }
    @Override public void enableCustomStatOrder(boolean z) { customStatOrderEnabled = z; }
    @Override public void enableDebugData(boolean z) { debugDataEnabled = z; }
    @Override public void enableDroppedFrameScreenshot(boolean z) {}
    @Override public void enableGraph(int i, boolean z) {}
    @Override public void enableGraph(String str, String str2, boolean z) {}
    @Override public void enableGraphs(boolean z) { graphsEnabled = z; }
    @Override public void enableHardwareRender(boolean z) {}
    @Override public void enableLowFpsScreenshot(boolean z) {}
    @Override public void enablePlayTime(boolean z) { playTimeEnabled = z; }
    @Override public void enableRecordingMetrics(boolean z) {}
    @Override public void enableStat(int i, boolean z) {}
    @Override public void enableStat(String str, String str2, boolean z) {}
    @Override public void enableStats(boolean z) { statsEnabled = z; }
    @Override public DeviceType getDeviceType() { return DeviceProperties.DEVICE_TYPE; }
    @Override public int getDroppedFrameCountLimit() { return 5; }
    @Override public int getDroppedFrameTimeLimit() { return 10; }
    @Override public int getGraphSortPriority(int i) { return 0; }
    @Override public int getGraphSortPriority(String str, String str2) { return 0; }
    @Override public int getLowFpsScreenshotCooldown() { return 5; }
    @Override public int getLowFpsScreenshotThreshold() { return 45; }
    @Override public float getOverlayDistance() { return overlayDistance; }
    @Override public float getOverlayPitch() { return overlayPitch; }
    @Override public float getOverlayScale() { return overlayScale; }
    @Override public float getOverlayYaw() { return overlayYaw; }
    @Override public int getStatSortPriority(int i) { return 0; }
    @Override public int getStatSortPriority(String str, String str2) { return 0; }
    @Override public boolean hasPermission(String str) { return false; }
    @Override public void hideAppStatsAndGraphs(boolean z) { appStatsAndGraphsHidden = z; }
    @Override public boolean isAppNameEnabled() { return appNameEnabled; }
    @Override public boolean isCustomGraphOrderEnabled() { return customGraphOrderEnabled; }
    @Override public boolean isCustomStatOrderEnabled() { return customStatOrderEnabled; }
    @Override public boolean isDebugDataEnabled() { return debugDataEnabled; }
    @Override public boolean isDroppedFrameScreenshotEnabled() { return false; }
    @Override public boolean isGraphEnabled(int i) { return true; }
    @Override public boolean isGraphEnabled(String str, String str2) { return true; }
    @Override public boolean isGraphsEnabled() { return graphsEnabled; }
    @Override public boolean isHardwareRenderEnabled() { return false; }
    @Override public boolean isInternalAppMetricsRecordingEnabled() { return false; }
    @Override public boolean isLowFpsScreenshotEnabled() { return false; }
    @Override public boolean isOverlayCaptureAllowed() { return true; }
    @Override public boolean isOverlayHeadLocked() { return overlayHeadLocked; }
    @Override public boolean isPlayTimeEnabled() { return playTimeEnabled; }
    @Override public boolean isRecordingMetricsEnabled() { return false; }
    @Override public boolean isStatEnabled(int i) { return true; }
    @Override public boolean isStatEnabled(String str, String str2) { return true; }
    @Override public boolean isStatsEnabled() { return statsEnabled; }
    @Override public void setDroppedFrameCountLimit(int i) {}
    @Override public void setDroppedFrameTimeLimit(int i) {}
    @Override public void setGraphSortPriority(int i, int i2) {}
    @Override public void setGraphSortPriority(String str, String str2, int i) {}
    @Override public void setLowFpsScreenshotCooldown(int i) {}
    @Override public void setLowFpsScreenshotThreshold(int i) {}
    @Override public void setStatSortPriority(int i, int i2) {}
    @Override public void setStatSortPriority(String str, String str2, int i) {}
}
