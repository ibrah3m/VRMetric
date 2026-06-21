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
import com.oculus.ovrmonitormetricsservice.rendering.NativeOverlayRenderer;
import com.oculus.ovrmonitormetricsservice.rendering.OverlayRenderer;
import com.vroverlay.metrics.rendering.OverlayRenderingManager;
import com.vroverlay.metrics.sdk.SimpleSettingsConfig;

public class OverlayService extends Service {
    private static final String TAG = "OverlayService";
    private static final String CHANNEL_ID = "overlay_channel";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "VR Overlay Service creating");
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service started");

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();

        startForeground(NOTIFICATION_ID, notification);

        SimpleSettingsConfig settings = OverlayApplication.settingsConfig;
        if (settings != null && settings.isOverlayEnabled()) {
            OverlayRenderer renderer = OverlayRenderingManager.get();
            if (renderer instanceof NativeOverlayRenderer) {
                ((NativeOverlayRenderer) renderer).showOverlay(settings);
                Log.i(TAG, "Native overlay renderer started");
            }
        } else {
            Log.w(TAG, "Overlay disabled or settings null");
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Stopping VR Overlay Service");
        OverlayRenderingManager.get().hideOverlay();
        OverlayRenderingManager.reset();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_name),
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setSound(null, null);
            channel.enableVibration(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
