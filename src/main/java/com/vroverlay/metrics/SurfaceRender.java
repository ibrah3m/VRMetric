package com.vroverlay.metrics;

import android.graphics.Bitmap;
import android.util.Log;

public class SurfaceRender {
    private static final String TAG = "SurfaceRender";

    private Bitmap mBitmap;
    private boolean mBitmapDirty;
    private int mHeight;
    private OverlayView mOverlayView;
    private int mWidth;
    private final Object mMutex = new Object();
    private volatile boolean mRunning = true;

    public SurfaceRender(OverlayView overlayView, int width, int height) {
        this.mOverlayView = overlayView;
        this.mWidth = width;
        this.mHeight = height;
    }

    public void stop() {
        synchronized (mMutex) {
            mRunning = false;
        }
    }

    public boolean isRunning() {
        return mRunning;
    }

    public void submitBitmap(Bitmap bitmap) {
        synchronized (mMutex) {
            if (!mRunning) return;
            mBitmap = bitmap;
            mBitmapDirty = true;
        }
    }

    public boolean renderFrame() {
        synchronized (mMutex) {
            if (!mRunning) return false;
            if (mBitmapDirty && mBitmap != null && !mBitmap.isRecycled()) {
                mBitmapDirty = false;
                if (mOverlayView != null) {
                    mOverlayView.updateBitmap(mBitmap);
                    return true;
                }
            }
            return true;
        }
    }
}
