package com.vroverlay.metrics;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

public class OverlayServiceWeb extends Service {
    private static final String TAG = "OverlayServiceWeb";
    private static final String CHANNEL_ID = "overlay_channel";
    private static final int NOTIFICATION_ID = 1;

    private volatile boolean isRunning = false;
    private WindowManager windowManager;
    private FrameLayout overlayLayout;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "VR Overlay Service creating");
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service started");

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("VR Overlay")
            .setContentText("Performance overlay active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build();

        startForeground(NOTIFICATION_ID, notification);

        if (!isRunning) {
            startOverlay();
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
            channel.setDescription("Performance overlay rendering");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void startOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayLayout = new FrameLayout(this);
        overlayLayout.setBackgroundColor(Color.TRANSPARENT);

        TextView overlayText = new TextView(this);
        overlayText.setText("Hello World");
        overlayText.setTextColor(Color.BLACK);
        overlayText.setTextSize(18);
        overlayText.setBackgroundColor(Color.parseColor("#00ff00"));
        overlayText.setPadding(40, 20, 40, 20);
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        );
        overlayLayout.addView(overlayText, textParams);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 100;

        windowManager.addView(overlayLayout, params);
        Log.i(TAG, "Overlay view added to window");
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

        if (overlayLayout != null && windowManager != null) {
            windowManager.removeView(overlayLayout);
        }
    }
}
