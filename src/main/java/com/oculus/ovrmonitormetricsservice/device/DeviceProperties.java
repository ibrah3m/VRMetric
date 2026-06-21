package com.oculus.ovrmonitormetricsservice.device;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import com.oculus.ovrmonitormetricsservice.log.Log;
import java.lang.reflect.Method;

public class DeviceProperties {
    public static native void getDeviceProperties();
    public static final int DEVICE_RECOMMENDED_EYEBUFFER_HEIGHT = 1464;
    public static final int DEVICE_RECOMMENDED_EYEBUFFER_WIDTH = 1832;
    public static final int DEVICE_SCREEN_HEIGHT = 1920;
    public static final int DEVICE_SCREEN_WIDTH = 1832;
    public static final long DEVICE_TOTAL_MEMORY_BYTES = 6442450944L;
    public static final DeviceType DEVICE_TYPE = DeviceType.Quest2;
    public static final int DEVICE_USABLE_MEMORY_MB = 5750;
    private static final String TAG = "DeviceProperties";
    private static Method mGetPropertyMethod;

    public static boolean isUserdebugBuild() {
        String propValue = getSystemProperty("ro.build.type");
        return propValue != null && propValue.equals("userdebug");
    }

    public static String getSystemProperty(String name) {
        try {
            if (mGetPropertyMethod == null) {
                initSystemPropertyMethod();
            }
            return (String) mGetPropertyMethod.invoke(null, name);
        } catch (Exception e) {
            Log.e(TAG, "Failed to getSystemProperty", e);
            return null;
        }
    }

    @SuppressLint({"PrivateApi"})
    private static void initSystemPropertyMethod() {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            mGetPropertyMethod = clazz.getDeclaredMethod("get", String.class);
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Failed to find class android.os.SystemProperties", e);
        } catch (NoSuchMethodException e2) {
            Log.e(TAG, "Failed to find get method", e2);
        } catch (SecurityException e3) {
            Log.e(TAG, "Security exception thrown trying to find get system property method", e3);
        }
    }

    static {
        initSystemPropertyMethod();
        Log.d(TAG, "Device type: " + DEVICE_TYPE + "   SCREEN W X H: " + DEVICE_SCREEN_WIDTH + " X " + DEVICE_SCREEN_HEIGHT + "   RECOMMENDED_EYEBUFFER W X H: " + DEVICE_RECOMMENDED_EYEBUFFER_WIDTH + " X " + DEVICE_RECOMMENDED_EYEBUFFER_HEIGHT);
    }
}