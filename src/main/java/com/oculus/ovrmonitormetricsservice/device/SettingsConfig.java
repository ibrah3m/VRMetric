package com.oculus.ovrmonitormetricsservice.device;

import android.content.Context;
import android.util.JsonWriter;
import com.oculus.ovrmonitormetricsservice.PerfDebugOverlay;
import java.io.IOException;
import java.io.StringWriter;

public abstract class SettingsConfig {
    public static final int DROPPED_FRAME_COUNT_LIMIT_INCREMENT = 1;
    public static final int DROPPED_FRAME_COUNT_LIMIT_MAX = 20;
    public static final int DROPPED_FRAME_COUNT_LIMIT_MIN = 1;
    public static final int DROPPED_FRAME_TIME_LIMIT_INCREMENT = 1;
    public static final int DROPPED_FRAME_TIME_LIMIT_MAX = 120;
    public static final int DROPPED_FRAME_TIME_LIMIT_MIN = 5;
    public static final int LOW_FPS_SCREENSHOT_COOLDOWN_INCREMENT = 1;
    public static final int LOW_FPS_SCREENSHOT_COOLDOWN_MAX = 30;
    public static final int LOW_FPS_SCREENSHOT_COOLDOWN_MIN = 1;
    public static final int LOW_FPS_SCREENSHOT_THRESHOLD_INCREMENT = 1;
    public static final int LOW_FPS_SCREENSHOT_THRESHOLD_MAX = 90;
    public static final int LOW_FPS_SCREENSHOT_THRESHOLD_MIN = 1;
    public static final float OVERLAY_DISTANCE_INCREMENT = 0.1f;
    public static final float OVERLAY_DISTANCE_MAX = 2.0f;
    public static final float OVERLAY_DISTANCE_MIN = 0.1f;
    public static final float OVERLAY_HEADLOCKED_PITCH_MAX = 35.0f;
    public static final float OVERLAY_HEADLOCKED_PITCH_MIN = -35.0f;
    public static final float OVERLAY_HEADLOCKED_YAW_MAX = 35.0f;
    public static final float OVERLAY_HEADLOCKED_YAW_MIN = -35.0f;
    public static final float OVERLAY_PITCH_INCREMENT = 1.0f;
    public static final float OVERLAY_PITCH_MAX = 90.0f;
    public static final float OVERLAY_PITCH_MIN = -90.0f;
    public static final float OVERLAY_SCALE_INCREMENT = 0.1f;
    public static final float OVERLAY_SCALE_MAX = 3.0f;
    public static final float OVERLAY_SCALE_MIN = 1.0f;
    public static final float OVERLAY_YAW_INCREMENT = 1.0f;
    public static final float OVERLAY_YAW_MAX = 180.0f;
    public static final float OVERLAY_YAW_MIN = -180.0f;

    public abstract void allowOverlayCapture(boolean z);
    public abstract boolean areAppStatsAndGraphsHidden();
    public abstract boolean canRequestPermissions();
    public abstract void enableAppName(boolean z);
    public abstract void enableCustomGraphOrder(boolean z);
    public abstract void enableCustomStatOrder(boolean z);
    public abstract void enableDebugData(boolean z);
    public abstract void enableDroppedFrameScreenshot(boolean z);
    public abstract void enableGraph(int i, boolean z);
    public abstract void enableGraph(String str, String str2, boolean z);
    public abstract void enableGraphs(boolean z);
    public abstract void enableHardwareRender(boolean z);
    public abstract void enableLowFpsScreenshot(boolean z);
    public abstract void enableOverlay(boolean z);
    public abstract void enablePlayTime(boolean z);
    public abstract void enableRecordingMetrics(boolean z);
    public abstract void enableStat(int i, boolean z);
    public abstract void enableStat(String str, String str2, boolean z);
    public abstract void enableStats(boolean z);
    public abstract DeviceType getDeviceType();
    public abstract int getDroppedFrameCountLimit();
    public abstract int getDroppedFrameTimeLimit();
    public abstract int getGraphSortPriority(int i);
    public abstract int getGraphSortPriority(String str, String str2);
    public abstract int getLowFpsScreenshotCooldown();
    public abstract int getLowFpsScreenshotThreshold();
    public abstract float getOverlayDistance();
    public abstract float getOverlayPitch();
    public abstract float getOverlayScale();
    public abstract float getOverlayYaw();
    public abstract PerfDebugOverlay getPerfDebugOverlay();
    public abstract int getStatSortPriority(int i);
    public abstract int getStatSortPriority(String str, String str2);
    public abstract boolean hasPermission(String str);
    public abstract void hideAppStatsAndGraphs(boolean z);
    public abstract boolean isAppNameEnabled();
    public abstract boolean isCustomGraphOrderEnabled();
    public abstract boolean isCustomStatOrderEnabled();
    public abstract boolean isDebugDataEnabled();
    public abstract boolean isDroppedFrameScreenshotEnabled();
    public abstract boolean isGraphEnabled(int i);
    public abstract boolean isGraphEnabled(String str, String str2);
    public abstract boolean isGraphsEnabled();
    public abstract boolean isHardwareRenderEnabled();
    public abstract boolean isInternalAppMetricsRecordingEnabled();
    public abstract boolean isLowFpsScreenshotEnabled();
    public abstract boolean isOverlayCaptureAllowed();
    public abstract boolean isOverlayEnabled();
    public abstract boolean isOverlayHeadLocked();
    public abstract boolean isPlayTimeEnabled();
    public abstract boolean isRecordingMetricsEnabled();
    public abstract boolean isStatEnabled(int i);
    public abstract boolean isStatEnabled(String str, String str2);
    public abstract boolean isStatsEnabled();
    public abstract void setDroppedFrameCountLimit(int i);
    public abstract void setDroppedFrameTimeLimit(int i);
    public abstract void setGraphSortPriority(int i, int i2);
    public abstract void setGraphSortPriority(String str, String str2, int i);
    public abstract void setLowFpsScreenshotCooldown(int i);
    public abstract void setLowFpsScreenshotThreshold(int i);
    public abstract void setOverlayDistance(float f);
    public abstract void setOverlayHeadLocked(boolean z);
    public abstract void setOverlayPitch(float f);
    public abstract void setOverlayScale(float f);
    public abstract void setOverlayYaw(float f);
    public abstract void setStatSortPriority(int i, int i2);
    public abstract void setStatSortPriority(String str, String str2, int i);

    public String asJSONString(Context context) {
        StringWriter stringWriter = new StringWriter();
        JsonWriter writer = new JsonWriter(stringWriter);
        try {
            writer.beginObject();
            writer.name("overlayEnabled").value(isOverlayEnabled());
            writer.name("graphEnabled").value(isGraphsEnabled());
            writer.name("statsEnabled").value(isStatsEnabled());
            writer.name("appNameEnabled").value(isAppNameEnabled());
            writer.name("playTimeEnabled").value(isPlayTimeEnabled());
            writer.name("overlayScale").value(getOverlayScale());
            writer.name("overlayDistance").value(getOverlayDistance());
            writer.name("overlayPitch").value(getOverlayPitch());
            writer.name("overlayYaw").value(getOverlayYaw());
            writer.name("overlayHeadLocked").value(isOverlayHeadLocked());
            writer.name("overlayCaptureAllowed").value(isOverlayCaptureAllowed());
            writer.name("recordingMetricsEnabled").value(isRecordingMetricsEnabled());
            writer.name("hardwareRenderEnabled").value(isHardwareRenderEnabled());
            writer.name("droppedFrameScreenshotEnabled").value(isDroppedFrameScreenshotEnabled());
            writer.name("droppedFrameCountLimit").value(getDroppedFrameCountLimit());
            writer.name("droppedFrameTimeLimit").value(getDroppedFrameTimeLimit());
            writer.name("lowFpsScreenshotEnabled").value(isLowFpsScreenshotEnabled());
            writer.name("lowFpsScreenshotThreshold").value(getLowFpsScreenshotThreshold());
            writer.name("lowFpsScreenshotCooldown").value(getLowFpsScreenshotCooldown());
            writer.endObject();
            String string = stringWriter.toString();
            try {
                writer.close();
            } catch (IOException e) {
            }
            return string;
        } catch (IOException e2) {
            try {
                writer.close();
            } catch (IOException e3) {
            }
            return "";
        } catch (Throwable th) {
            try {
                writer.close();
            } catch (IOException e4) {
            }
            throw th;
        }
    }

    public boolean isStatAvailable(int stat) {
        return true;
    }

    public boolean isStatAvailable(String appName, String statName) {
        if (areAppStatsAndGraphsHidden()) {
            return false;
        }
        return true;
    }

    public boolean isStatHidden(int stat) {
        return false;
    }

    public boolean isStatHidden(String appName, String statName) {
        return false;
    }
}