package com.oculus.ovrmonitormetricsservice.rendering;

public interface OverlayRenderer {
    short FRAME_RATE_DEFAULT = 10;
    short FRAME_RATE_HIGH = 120;

    void hideOverlay();
    boolean isOverlayVisible();
    void setOverlayFrameRateHint(short s);
    void showOverlay();
}
