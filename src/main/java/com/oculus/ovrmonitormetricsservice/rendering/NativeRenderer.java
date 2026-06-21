package com.oculus.ovrmonitormetricsservice.rendering;

import android.content.Context;
import android.graphics.Point;
import android.util.Log;
import com.oculus.ovrmonitormetricsservice.PerfDebugOverlay;
import com.vroverlay.metrics.OverlayApplication;
import com.vroverlay.metrics.sdk.CrashGuard;
import com.vroverlay.metrics.sdk.SimpleSettingsConfig;

public class NativeRenderer implements NativeOverlayRenderer {
    private static final String TAG = "NativeRenderer";
    private static final Object _mutex = new Object();
    private static final long FRAME_INTERVAL_MS = 33;
    private volatile NativeThread mNativeThread;
    private volatile boolean mInitInProgress = false;

    private static native void destroy();
    private static native boolean init(Context context, int textureWidth, int textureHeight, int backend);
    private static native void nativeSetOverlayFrameRateHint(short frameRate);
    private static native void update(float pitch, float yaw, float scale, float distance, boolean headLocked, boolean captureAllowed);
    private static native void updateTexture(int[] texture, int textureWidth, int textureHeight, int displayedWidth, int displayedHeight, int textureUpdateCount);

    static {
        System.loadLibrary("native-lib");
    }

    private boolean canShowOverlay(SimpleSettingsConfig settings) {
        if (settings == null || !settings.isOverlayEnabled()) {
            return false;
        }
        Point dims = settings.getPerfDebugOverlay().GetTextureDimensions();
        return (dims.x != 0 && dims.y != 0);
    }

    @Override
    public boolean isOverlayVisible() {
        synchronized (_mutex) {
            return mNativeThread != null && mNativeThread.isRunning();
        }
    }

    public void showOverlay(SimpleSettingsConfig settings) {
        synchronized (_mutex) {
            if (settings == null || !canShowOverlay(settings)) {
                hideOverlay();
            } else if (!isOverlayVisible() && !mInitInProgress) {
                Log.i(TAG, "Starting NativeRenderer thread");
                mInitInProgress = true;
                mNativeThread = new NativeThread(mNativeThread, settings);
                mNativeThread.start();
            }
        }
    }

    @Override
    public void showOverlay() {
        SimpleSettingsConfig settings = OverlayApplication.settingsConfig;
        showOverlay(settings);
    }

    @Override
    public void hideOverlay() {
        synchronized (_mutex) {
            if (mNativeThread != null && mNativeThread.isRunning()) {
                Log.i(TAG, "Shutting down NativeRenderer thread");
                mNativeThread.shutdown();
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
        private volatile boolean mInitialized = false;
        private SimpleSettingsConfig mSettings;
        private int mFrameCount = 0;

        NativeThread(NativeThread previousNativeThread, SimpleSettingsConfig settings) {
            mPreviousNativeThread = previousNativeThread;
            mSettings = settings;
        }

        @Override
        public void updateTexture(int[] texture, int textureWidth, int textureHeight, int displayedWidth, int displayedHeight, int textureUpdateCount) {
            NativeRenderer.updateTexture(texture, textureWidth, textureHeight, displayedWidth, displayedHeight, textureUpdateCount);
        }

        @Override
        public void run() {
            if (mPreviousNativeThread != null) {
                try { mPreviousNativeThread.join(); } catch (InterruptedException e) { }
                mPreviousNativeThread = null;
            }
            setName("OverlayRender");

            if (mSettings == null) {
                Log.e(TAG, "No settings provided");
                synchronized (_mutex) { mInitInProgress = false; }
                return;
            }

            Backend nativeBackend = mSettings.getPreferredNativeRendererBackend();
            Log.i(TAG, "Initializing native renderer with backend: " + nativeBackend.name());

            CrashGuard crashGuard = CrashGuard.getInstance(mSettings.getContext());

            if (!crashGuard.shouldAttemptInit()) {
                Log.e(TAG, "CrashGuard blocked native init after " + crashGuard.getCrashCount() + " crashes. Aborting render thread.");
                synchronized (_mutex) { mInitInProgress = false; }
                return;
            }

            crashGuard.markInitStarting();
            boolean initOk = init(mSettings.getContext(), PerfDebugOverlay.TextureWidth, PerfDebugOverlay.TextureHeight, nativeBackend.ordinal());

            if (!initOk) {
                crashGuard.markInitFailedGracefully();
                Log.e(TAG, "Native renderer init failed — vrapi_Initialize likely failed. Aborting render thread.");
                synchronized (_mutex) { mInitInProgress = false; }
                return;
            }

            crashGuard.markInitSucceeded();

            mInitialized = true;

            Log.i(TAG, "Entering render loop");
            while (!mShutdownRequested) {
                long frameStart = System.currentTimeMillis();

                PerfDebugOverlay perfDebugOverlay = mSettings.getPerfDebugOverlay();
                if (perfDebugOverlay != null) {
                    perfDebugOverlay.UpdateTexture(this);
                    update(
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
                }

                long sleepTime = FRAME_INTERVAL_MS - (System.currentTimeMillis() - frameStart);
                if (sleepTime > 0) {
                    try { Thread.sleep(sleepTime); } catch (InterruptedException e) { break; }
                }
            }

            destroy();
            synchronized (_mutex) { mInitInProgress = false; }
            Log.i(TAG, "Native renderer destroyed after " + mFrameCount + " frames");
        }

        void shutdown() {
            mShutdownRequested = true;
        }

        boolean isRunning() {
            return mInitialized && !mShutdownRequested;
        }
    }
}
