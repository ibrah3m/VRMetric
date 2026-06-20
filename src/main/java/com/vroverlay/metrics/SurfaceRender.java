package com.vroverlay.metrics;

import android.graphics.Bitmap;
import android.util.Log;

public class SurfaceRender implements PerfDebugOverlay.TextureReceiver {
    private static final String TAG = "SurfaceRender";

    private Bitmap mBitmap;
    private boolean mBitmapDirty;
    private int mHeight;
    private PerfDebugOverlay mPerfDebugOverlay;
    private OverlayView mOverlayView;
    private int mWidth;
    private final Object mMutex = new Object();
    private volatile boolean mRunning = true;
    private int mLastUpdateIndex = -1;

    public SurfaceRender(OverlayView overlayView, PerfDebugOverlay perfDebugOverlay, int width, int height) {
        this.mOverlayView = overlayView;
        this.mWidth = width;
        this.mHeight = height;
        this.mPerfDebugOverlay = perfDebugOverlay;
    }

    public void stop() {
        synchronized (mMutex) {
            Log.d(TAG, "Stop called");
            mRunning = false;
        }
    }

    public boolean isRunning() {
        return mRunning;
    }

    private Bitmap getBitmap(int w, int h) {
        if (w <= 0 || h <= 0) {
            return null;
        }
        if (mBitmap == null || w != mBitmap.getWidth() || h != mBitmap.getHeight()) {
            mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        }
        mBitmapDirty = true;
        return mBitmap;
    }

    @Override
    public void updateTexture(int[] texture, int textureWidth, int textureHeight,
                              int displayedWidth, int displayedHeight, int textureUpdateCount) {
        Bitmap bitmap;
        if (mLastUpdateIndex != textureUpdateCount &&
            (bitmap = getBitmap(displayedWidth, displayedHeight)) != null) {
            bitmap.setPixels(texture, 0, textureWidth, 0, 0, displayedWidth, displayedHeight);
            mLastUpdateIndex = textureUpdateCount;
        }
    }

    public boolean renderFrame() {
        synchronized (mMutex) {
            if (!mRunning) {
                Log.d(TAG, "Not running, shutting down render thread");
                return false;
            }
            mPerfDebugOverlay.UpdateTexture(this);
            if (mBitmapDirty) {
                mBitmapDirty = false;
                if (mBitmap != null && mOverlayView != null) {
                    mOverlayView.updateBitmap(mBitmap);
                    return true;
                }
                Log.d(TAG, "Canvas dimensions changed");
                return false;
            }
            return true;
        }
    }
}
