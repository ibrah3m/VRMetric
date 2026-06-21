package com.vroverlay.metrics;

interface IMetricsService {
    void updateOverlayData(String data);
    void setOverlayEnabled(boolean enabled);
    boolean isOverlayVisible();
    void setOverlayTransform(float pitch, float yaw, float scale, float distance);
}
