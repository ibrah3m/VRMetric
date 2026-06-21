package com.oculus.ovrmonitormetricsservice;

public class OVRGpuProfiler {
    private static native void destroy();
    private static native void disableTrackingMetric(String metric);
    private static native void enableTrackingMetric(String metric);
    private static native double getMetric(String metric);
    private static native boolean init();
    private static native boolean startTracking();
    private static native void stopTracking();
    private static native boolean updateMetrics();
}
