package com.vroverlay.metrics.rendering;

import com.vroverlay.metrics.sdk.SimpleSettingsConfig;

public interface NativeOverlayRenderer extends OverlayRenderer {

    public enum Backend {
        VRAPI_GLES,
        OPENXR_VULKAN
    }
    
    void showOverlay(SimpleSettingsConfig settings);
}
