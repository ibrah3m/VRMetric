package com.vroverlay.metrics;

import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class OverlayApplication extends Application {
    private static final String TAG = "OverlayApplication";

    public static PerfDebugOverlay overlay;
    public static boolean isSystemApp = false;
    private Handler metricsHandler;
    private long startTime;
    private int simulatedFrameCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "Creating VR Overlay Application");

        isSystemApp = checkSystemApp();
        Log.i(TAG, "Is system app: " + isSystemApp);

        overlay = new PerfDebugOverlay();
        overlay.SetAppName("VR OVERLAY");
        overlay.EnableStats(true);
        overlay.EnableGraphs(true);
        overlay.EnableAppName(true);
        overlay.EnablePlayTime(true);
        Log.i(TAG, "Overlay created");

        startTime = System.currentTimeMillis();

        startMetricsSimulation();
        startOverlayService();
    }

    private void startMetricsSimulation() {
        metricsHandler = new Handler(Looper.getMainLooper());
        metricsHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (overlay != null) {
                    simulatedFrameCount++;
                    long elapsed = System.currentTimeMillis() - startTime;

                    int[] stats = new int[128];

                    int fps = 55 + (int)(Math.sin(simulatedFrameCount * 0.1) * 10 + Math.random() * 5);
                    stats[25] = clamp(fps, 0, 90);

                    stats[17] = 3 + (int)(Math.sin(simulatedFrameCount * 0.05) * 2);
                    stats[18] = 4 + (int)(Math.sin(simulatedFrameCount * 0.07) * 1.5);

                    stats[60] = 35 + (int)(Math.sin(simulatedFrameCount * 0.08) * 20 + Math.random() * 5);
                    stats[69] = 55 + (int)(Math.cos(simulatedFrameCount * 0.06) * 15 + Math.random() * 5);

                    stats[36] = (int)(Math.random() * 3);
                    stats[53] = 8000 + (int)(Math.sin(simulatedFrameCount * 0.12) * 3000);

                    int memBase = 2048 + (int)(elapsed / 60000.0) * 10;
                    stats[0] = memBase + (int)(Math.random() * 100);
                    stats[1] = 1200 + (int)(Math.random() * 200);

                    stats[26] = 72;
                    stats[47] = 1024;
                    stats[48] = 1024;
                    stats[22] = 1;

                    overlay.UpdateStats(stats);
                }

                metricsHandler.postDelayed(this, 100);
            }
        }, 100);
    }

    private void startOverlayService() {
        try {
            Intent serviceIntent = new Intent(this, OverlayService.class);
            startForegroundService(serviceIntent);
            Log.i(TAG, "OverlayService started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start OverlayService", e);
        }
    }

    private boolean checkSystemApp() {
        return (getApplicationInfo().flags &
            android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }
}
