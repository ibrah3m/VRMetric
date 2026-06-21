package com.oculus.ovrmonitormetricsservice.rendering;

import com.vroverlay.metrics.sdk.SimpleSettingsConfig;

public interface NativeOverlayRenderer extends OverlayRenderer {
    enum Backend {
        VRAPI_GLES,
        OPENXR_VULKAN
    }

    void showOverlay(SimpleSettingsConfig settings);
}
