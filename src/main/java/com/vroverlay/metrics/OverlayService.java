package com.vroverlay.metrics;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.vroverlay.metrics.rendering.NativeOverlayRenderer;
import com.vroverlay.metrics.rendering.OverlayRenderingManager;
import com.vroverlay.metrics.sdk.SimpleSettingsConfig;

public class OverlayService extends Service {
    private static final String TAG = "OverlayService";
    private static final String CHANNEL_ID = "overlay_channel";
    private static final int NOTIFICATION_ID = 1;

    private volatile boolean isRunning = false;
    private SimpleSettingsConfig settingsConfig;
    private NativeOverlayRenderer nativeRenderer;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "VR Overlay Service creating");
        createNotificationChannel();

        settingsConfig = new SimpleSettingsConfig(getApplicationContext());

        OverlayApplication.overlayManager.setSettingsConfig(settingsConfig);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service started");

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VR Overlay")
            .setContentText("Native overlay rendering active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();

        startForeground(NOTIFICATION_ID, notification);

        if (!isRunning) {
            startNativeRenderer(settingsConfig);
            isRunning = true;
        }
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "VR Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Native overlay rendering");
            channel.setSound(null, null);
            channel.enableVibration(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void startNativeRenderer(SimpleSettingsConfig settings) {
        if (nativeRenderer == null) {
            nativeRenderer = (NativeOverlayRenderer) OverlayRenderingManager.get();
        }

        if (nativeRenderer != null && settings != null) {
            nativeRenderer.showOverlay(settings);
            Log.i(TAG, "Native overlay renderer started");
        } else {
            Log.e(TAG, "Cannot start native renderer: renderer=" + nativeRenderer + ", settings=" + settings);
        }
    }

    private void stopNativeRenderer() {
        if (nativeRenderer != null) {
            nativeRenderer.hideOverlay();
            Log.i(TAG, "Native overlay renderer stopped");
        }
        OverlayRenderingManager.reset();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Stopping VR Overlay Service");
        isRunning = false;
        stopNativeRenderer();
    }
}
