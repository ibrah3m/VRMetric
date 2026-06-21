package com.vroverlay.metrics.rendering;

public interface OverlayRenderer {
    public static final short FRAME_RATE_DEFAULT = 10;
    public static final short FRAME_RATE_HIGH = 120;

    void hideOverlay();

    boolean isOverlayVisible();

    void setOverlayFrameRateHint(short s);

    void showOverlay();
    
    default void showOverlay(com.vroverlay.metrics.sdk.SimpleSettingsConfig settings) {
        showOverlay();
    }
}
