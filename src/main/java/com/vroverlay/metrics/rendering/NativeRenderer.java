package com.vroverlay.metrics.rendering;

import android.content.Context;
import android.graphics.Point;
import android.util.Log;
import com.oculus.ovrmonitormetricsservice.PerfDebugOverlay;
import com.vroverlay.metrics.sdk.SimpleSettingsConfig;

public class NativeRenderer implements NativeOverlayRenderer {
    private static final String TAG = "NativeRenderer";
    private static final Object _mutex = new Object();
    private static final long FRAME_INTERVAL_MS = 33; // ~30fps target

    private NativeThread mNativeThread;

    private static native void destroy();

    private static native void init(Context context, int textureWidth, int textureHeight, int backend);

    private static native void nativeSetOverlayFrameRateHint(short frameRate);

    private static native void update(float pitch, float yaw, float scale, float distance, boolean headLocked, boolean captureAllowed);

    private static native void updateTexture(int[] texture, int textureWidth, int textureHeight, int displayedWidth, int displayedHeight, int textureUpdateCount);

    static {
        System.loadLibrary("native-lib");
    }

    private boolean canShowOverlay(SimpleSettingsConfig settings) {
        if (settings == null) {
            Log.e(TAG, "Settings config is null, cannot show overlay");
            return false;
        }
        if (!settings.isOverlayEnabled()) {
            Log.w(TAG, "Overlay is disabled in settings");
            return false;
        }
        Point dims = settings.getPerfDebugOverlay().GetTextureDimensions();
        Log.i(TAG, "Overlay dimensions: " + dims.x + "x" + dims.y);
        return (dims.x != 0 && dims.y != 0);
    }

    @Override
    public boolean isOverlayVisible() {
        boolean z;
        synchronized (_mutex) {
            z = this.mNativeThread != null && this.mNativeThread.isRunning();
        }
        return z;
    }

    public void showOverlay(SimpleSettingsConfig settings) {
        synchronized (_mutex) {
            if (settings == null) {
                Log.e(TAG, "Cannot show overlay: settings config is null");
                return;
            }
            if (!canShowOverlay(settings)) {
                hideOverlay();
            } else {
                if (isOverlayVisible()) {
                    return;
                }
                Log.i(TAG, "Starting NativeRenderer thread");
                this.mNativeThread = new NativeThread(this.mNativeThread, settings);
                this.mNativeThread.start();
            }
        }
    }

    @Override
    public void showOverlay() {
        Log.w(TAG, "showOverlay() called without settings config - overlay will not start");
        showOverlay(null);
    }

    @Override
    public void hideOverlay() {
        synchronized (_mutex) {
            if (this.mNativeThread != null && this.mNativeThread.isRunning()) {
                Log.i(TAG, "Shutting down NativeRenderer thread");
                this.mNativeThread.shutdown();
            }
        }
    }

    @Override
    public void setOverlayFrameRateHint(short frameRate) {
        nativeSetOverlayFrameRateHint(frameRate);
    }

    private class NativeThread extends Thread implements PerfDebugOverlay.TextureReceiver {
        private NativeThread mPreviousNativeThread;
        private volatile boolean mShutdownRequested = false;
        private SimpleSettingsConfig mSettings;
        private int mFrameCount = 0;

        public NativeThread(NativeThread previousNativeThread, SimpleSettingsConfig settings) {
            this.mPreviousNativeThread = previousNativeThread;
            this.mSettings = settings;
        }

        @Override
        public void updateTexture(int[] texture, int textureWidth, int textureHeight, int displayedWidth, int displayedHeight, int textureUpdateCount) {
            NativeRenderer.updateTexture(texture, textureWidth, textureHeight, displayedWidth, displayedHeight, textureUpdateCount);
        }

        @Override
        public void run() {
            if (this.mPreviousNativeThread != null) {
                try {
                    this.mPreviousNativeThread.join();
                } catch (InterruptedException e) {
                }
                this.mPreviousNativeThread = null;
            }
            setName("OverlayRender");

            if (mSettings == null) {
                Log.e(TAG, "No settings provided, cannot start native renderer");
                return;
            }

            NativeOverlayRenderer.Backend nativeBackend = mSettings.getPreferredNativeRendererBackend();
            Log.i(TAG, "Initializing native renderer with backend: " + nativeBackend.name());
            NativeRenderer.init(mSettings.getContext(), PerfDebugOverlay.TextureWidth, PerfDebugOverlay.TextureHeight, nativeBackend.ordinal());

            Log.i(TAG, "Entering render loop");
            while (!this.mShutdownRequested) {
                long frameStart = System.currentTimeMillis();

                PerfDebugOverlay perfDebugOverlay = mSettings.getPerfDebugOverlay();
                if (perfDebugOverlay != null) {
                    perfDebugOverlay.UpdateTexture(this);
                    NativeRenderer.update(
                        mSettings.getOverlayPitch(),
                        mSettings.getOverlayYaw(),
                        mSettings.getOverlayScale(),
                        mSettings.getOverlayDistance(),
                        mSettings.isOverlayHeadLocked(),
                        mSettings.isOverlayCaptureAllowed()
                    );

                    mFrameCount++;
                    if (mFrameCount % 100 == 0) {
                        Log.d(TAG, "Rendered " + mFrameCount + " frames");
                    }
                } else {
                    Log.w(TAG, "PerfDebugOverlay is null in render loop");
                }

                long frameDuration = System.currentTimeMillis() - frameStart;
                long sleepTime = FRAME_INTERVAL_MS - frameDuration;
                if (sleepTime > 0) {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }

            NativeRenderer.destroy();
            Log.i(TAG, "Native renderer destroyed after " + mFrameCount + " frames");
        }

        public void shutdown() {
            this.mShutdownRequested = true;
        }

        public boolean isRunning() {
            return !this.mShutdownRequested;
        }
    }
}
