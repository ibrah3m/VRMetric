package com.vroverlay.metrics;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.WindowManager;
import android.util.Log;

public class OverlayWindow {
    private static final String TAG = "OverlayWindow";
    private static final int OVERLAY_WIDTH = 512;
    private static final int OVERLAY_HEIGHT = 272;

    public interface ReadyCallback {
        void onReady();
        void onDestroyed();
    }

    private final Context context;
    private final ReadyCallback callback;
    private WindowManager windowManager;
    private OverlayView overlayView;

    public OverlayWindow(Context context, ReadyCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    public void show() {
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        overlayView = new OverlayView(context);
        overlayView.setClickable(false);
        overlayView.setFocusable(false);
        overlayView.setFocusableInTouchMode(false);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            OVERLAY_WIDTH, OVERLAY_HEIGHT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 100;

        try {
            windowManager.addView(overlayView, params);
            Log.i(TAG, "Overlay window added");
            if (callback != null) {
                callback.onReady();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to add overlay window", e);
        }
    }

    public void hide() {
        if (windowManager != null && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception e) {
                Log.e(TAG, "Failed to remove overlay window", e);
            }
            overlayView = null;
        }
    }

    public OverlayView getOverlayView() {
        return overlayView;
    }
}
