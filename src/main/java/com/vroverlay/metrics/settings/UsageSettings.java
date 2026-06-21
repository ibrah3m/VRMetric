package com.vroverlay.metrics.settings;

import android.content.Context;
import android.content.SharedPreferences;

public class UsageSettings {
    private static final String TAG = "UsageSettings";
    private static final String PREFS_NAME = "overlay_settings";

    private static final String KEY_OVERLAY_ENABLED = "overlay_enabled";
    private static final String KEY_OVERLAY_SCALE = "overlay_scale";
    private static final String KEY_OVERLAY_DISTANCE = "overlay_distance";
    private static final String KEY_OVERLAY_PITCH = "overlay_pitch";
    private static final String KEY_OVERLAY_YAW = "overlay_yaw";
    private static final String KEY_OVERLAY_HEAD_LOCKED = "overlay_head_locked";
    private static final String KEY_OVERLAY_BACKEND = "overlay_backend";

    private final SharedPreferences prefs;

    public UsageSettings(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isOverlayEnabled() {
        return prefs.getBoolean(KEY_OVERLAY_ENABLED, true);
    }

    public void setOverlayEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply();
    }

    public float getOverlayScale() {
        return prefs.getFloat(KEY_OVERLAY_SCALE, 1.0f);
    }

    public void setOverlayScale(float scale) {
        prefs.edit().putFloat(KEY_OVERLAY_SCALE, scale).apply();
    }

    public float getOverlayDistance() {
        return prefs.getFloat(KEY_OVERLAY_DISTANCE, 1.0f);
    }

    public void setOverlayDistance(float distance) {
        prefs.edit().putFloat(KEY_OVERLAY_DISTANCE, distance).apply();
    }

    public float getOverlayPitch() {
        return prefs.getFloat(KEY_OVERLAY_PITCH, 0.0f);
    }

    public void setOverlayPitch(float pitch) {
        prefs.edit().putFloat(KEY_OVERLAY_PITCH, pitch).apply();
    }

    public float getOverlayYaw() {
        return prefs.getFloat(KEY_OVERLAY_YAW, 0.0f);
    }

    public void setOverlayYaw(float yaw) {
        prefs.edit().putFloat(KEY_OVERLAY_YAW, yaw).apply();
    }

    public boolean isOverlayHeadLocked() {
        return prefs.getBoolean(KEY_OVERLAY_HEAD_LOCKED, false);
    }

    public void setOverlayHeadLocked(boolean headLocked) {
        prefs.edit().putBoolean(KEY_OVERLAY_HEAD_LOCKED, headLocked).apply();
    }

    public int getOverlayBackend() {
        return prefs.getInt(KEY_OVERLAY_BACKEND, 1);
    }

    public void setOverlayBackend(int backend) {
        prefs.edit().putInt(KEY_OVERLAY_BACKEND, backend).apply();
    }

    public void clearAll() {
        prefs.edit().clear().apply();
    }
}
