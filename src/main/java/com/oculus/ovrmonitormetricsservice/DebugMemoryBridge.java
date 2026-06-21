package com.oculus.ovrmonitormetricsservice;

public class DebugMemoryBridge {
    public static native boolean canReadSharedMemory();
    public static native void closeSharedMemory();
    public static native String getDebugText();
    public static native void openSharedMemory();
    public static native void setClientConnected();
}
