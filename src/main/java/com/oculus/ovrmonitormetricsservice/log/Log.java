package com.oculus.ovrmonitormetricsservice.log;

import android.annotation.SuppressLint;

/* JADX INFO: loaded from: classes.dex */
public class Log {
    @SuppressLint({"BadMethodUse-android.util.Log.d"})
    public static void d(String tag, String msg) {
        android.util.Log.d(tag, msg);
    }

    @SuppressLint({"BadMethodUse-android.util.Log.i"})
    public static void i(String tag, String msg) {
        android.util.Log.i(tag, msg);
    }

    @SuppressLint({"BadMethodUse-android.util.Log.w"})
    public static void w(String tag, String msg) {
        android.util.Log.w(tag, msg);
    }

    @SuppressLint({"BadMethodUse-android.util.Log.e"})
    public static void e(String tag, String msg) {
        android.util.Log.e(tag, msg);
    }

    @SuppressLint({"BadMethodUse-android.util.Log.e"})
    public static void e(String tag, String msg, Exception e) {
        android.util.Log.e(tag, msg, e);
    }
}
