package com.vroverlay.metrics.rendering;

public class OverlayRenderingManager {
    private static final Object _mutex = new Object();
    private static OverlayRenderer _renderer;

    public static OverlayRenderer get() {
        if (_renderer == null) {
            synchronized (_mutex) {
                if (_renderer == null) {
                    _renderer = new NativeRenderer();
                }
            }
        }
        return _renderer;
    }

    public static void reset() {
        synchronized (_mutex) {
            _renderer = null;
        }
    }
}
