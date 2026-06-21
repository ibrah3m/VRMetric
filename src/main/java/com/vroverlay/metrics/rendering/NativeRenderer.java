package com.vroverlay.metrics.rendering;

import android.content.Context;
import android.graphics.Point;
import android.util.Log;
import com.oculus.ovrmonitormetricsservice.PerfDebugOverlay;
import com.vroverlay.metrics.OverlayApplication;
import com.vroverlay.metrics.sdk.SimpleSettingsConfig;

public class NativeRenderer implements NativeOverlayRenderer {
    private static final String TAG = "NativeRenderer";
    private static final Object _mutex = new Object();
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
            return false;
        }
        Point dims = settings.getPerfDebugOverlay().GetTextureDimensions();
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
                Log.d(TAG, "Starting NativeRenderer thread");
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
                Log.d(TAG, "Shutting down NativeRenderer thread");
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
        private boolean mShutdownRequested = false;
        private SimpleSettingsConfig mSettings;

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

            while (!this.mShutdownRequested) {
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
                } else {
                    try {
                        Thread.sleep(16);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }

            NativeRenderer.destroy();
            Log.i(TAG, "Native renderer destroyed");
        }

        public void shutdown() {
            this.mShutdownRequested = true;
        }

        public boolean isRunning() {
            return !this.mShutdownRequested;
        }
    }
}
