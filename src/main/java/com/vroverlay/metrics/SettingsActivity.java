package com.vroverlay.metrics;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private static final String TAG = "SettingsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Settings activity created");

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission not granted");
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            finish();
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(40, 40, 40, 40);

        TextView title = new TextView(this);
        title.setText("VR Overlay Settings");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, 30);
        layout.addView(title);

        setContentView(layout);
        startOverlayService();
    }

    private void startOverlayService() {
        try {
            Intent serviceIntent = new Intent(this, OverlayServiceWeb.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Log.i(TAG, "OverlayServiceWeb started from SettingsActivity");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start OverlayServiceWeb", e);
        }
    }
}
