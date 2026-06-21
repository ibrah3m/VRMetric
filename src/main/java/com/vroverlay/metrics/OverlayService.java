package com.vroverlay.metrics;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

public class OverlayService extends Service {
    private static final String TAG = "OverlayService";
    private static final String CHANNEL_ID = "overlay_channel";
    private static final int NOTIFICATION_ID = 1;

    private HandlerThread renderThread;
    private Handler renderHandler;
    private volatile boolean isRunning = false;
    private OverlayWindow overlayWindow;
    private SurfaceRender surfaceRender;

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
        overlayWindow = new OverlayWindow(this, new OverlayWindow.ReadyCallback() {
            @Override
            public void onReady() {
                Log.i(TAG, "Overlay ready, starting render thread");
                startRenderLoop();
            }

            @Override
            public void onDestroyed() {
                Log.i(TAG, "Overlay destroyed");
                if (surfaceRender != null) {
                    surfaceRender.stop();
                }
                if (renderHandler != null) {
                    renderHandler.removeCallbacksAndMessages(null);
                }
            }
        });
        overlayWindow.show();
    }

    private void startRenderLoop() {
        renderThread = new HandlerThread("OverlayRender");
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());

        renderHandler.post(new Runnable() {
            @Override
            public void run() {
                if (OverlayApplication.overlay != null && overlayWindow.getOverlayView() != null) {
                    surfaceRender = new SurfaceRender(
                        overlayWindow.getOverlayView(),
                        PerfDebugOverlayWeb.OVERLAY_WIDTH,
                        PerfDebugOverlayWeb.OVERLAY_HEIGHT
                    );
                    Log.i(TAG, "SurfaceRender initialized, starting frame loop");
                    renderHandler.post(renderLoop);
                } else {
                    Log.e(TAG, "Overlay not available, retrying");
                    renderHandler.postDelayed(this, 100);
                }
            }
        });
    }

    private final Runnable renderLoop = new Runnable() {
        @Override
        public void run() {
            if (!isRunning || surfaceRender == null || !surfaceRender.isRunning()) return;

            try {
                if (OverlayApplication.overlay != null && OverlayApplication.overlay.isPageReady()) {
                    OverlayApplication.overlay.captureBitmap(new PerfDebugOverlayWeb.BitmapCallback() {
                        @Override
                        public void onBitmapReady(android.graphics.Bitmap bitmap) {
                            surfaceRender.submitBitmap(bitmap);
                        }
                    });
                }
                surfaceRender.renderFrame();
            } catch (Exception e) {
                Log.e(TAG, "Render error", e);
            }

            renderHandler.postDelayed(this, 33);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Stopping VR Overlay Service");
        isRunning = false;

        if (surfaceRender != null) {
            surfaceRender.stop();
        }
        if (renderHandler != null) {
            renderHandler.removeCallbacksAndMessages(null);
        }
        if (renderThread != null) {
            renderThread.quitSafely();
        }
        if (overlayWindow != null) {
            overlayWindow.hide();
        }
    }
}
