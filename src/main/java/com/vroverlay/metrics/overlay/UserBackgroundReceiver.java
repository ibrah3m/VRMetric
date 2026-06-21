package com.vroverlay.metrics.overlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class UserBackgroundReceiver extends BroadcastReceiver {
    private static final String TAG = "UserBackgroundReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "User background received, hiding overlay");
        com.vroverlay.metrics.rendering.OverlayRenderingManager.get().hideOverlay();
    }
}
