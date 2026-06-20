package com.vroverlay.metrics;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
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

        Switch statsSwitch = new Switch(this);
        statsSwitch.setText("Show Stats");
        statsSwitch.setChecked(true);
        statsSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (OverlayApplication.overlay != null) {
                OverlayApplication.overlay.EnableStats(checked);
            }
        });
        layout.addView(statsSwitch);

        Switch graphSwitch = new Switch(this);
        graphSwitch.setText("Show Graphs");
        graphSwitch.setChecked(true);
        graphSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (OverlayApplication.overlay != null) {
                OverlayApplication.overlay.EnableGraphs(checked);
            }
        });
        layout.addView(graphSwitch);

        Switch nameSwitch = new Switch(this);
        nameSwitch.setText("Show App Name");
        nameSwitch.setChecked(true);
        nameSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (OverlayApplication.overlay != null) {
                OverlayApplication.overlay.EnableAppName(checked);
            }
        });
        layout.addView(nameSwitch);

        Switch playtimeSwitch = new Switch(this);
        playtimeSwitch.setText("Show Play Time");
        playtimeSwitch.setChecked(true);
        playtimeSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (OverlayApplication.overlay != null) {
                OverlayApplication.overlay.EnablePlayTime(checked);
            }
        });
        layout.addView(playtimeSwitch);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.CENTER;

        setContentView(layout);

        startOverlayService();
    }

    private void startOverlayService() {
        try {
            Intent serviceIntent = new Intent(this, OverlayService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Log.i(TAG, "OverlayService started from SettingsActivity");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start OverlayService", e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
