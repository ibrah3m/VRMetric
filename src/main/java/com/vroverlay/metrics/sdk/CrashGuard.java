package com.vroverlay.metrics.sdk;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class CrashGuard {
    private static final String TAG = "CrashGuard";
    private static final String PREFS_NAME = "vr_overlay_crash_guard";
    private static final String KEY_INIT_IN_PROGRESS = "init_in_progress";
    private static final String KEY_CRASH_COUNT = "crash_count";
    private static final String KEY_LAST_CRASH_TIME = "last_crash_time";
    private static final int MAX_CRASHES = 1;
    private static final long COOLDOWN_MS = 300_000;
    private static final long STALE_FLAG_THRESHOLD_MS = 30_000;

    private static volatile CrashGuard sInstance;
    private final SharedPreferences prefs;

    private CrashGuard(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static CrashGuard getInstance(Context context) {
        if (sInstance == null) {
            synchronized (CrashGuard.class) {
                if (sInstance == null) {
                    CrashGuard guard = new CrashGuard(context);
                    guard.detectPreviousCrash();
                    sInstance = guard;
                }
            }
        }
        return sInstance;
    }

    private void detectPreviousCrash() {
        boolean inProgress = prefs.getBoolean(KEY_INIT_IN_PROGRESS, false);
        if (!inProgress) return;

        long lastTime = prefs.getLong(KEY_LAST_CRASH_TIME, 0);
        long elapsed = System.currentTimeMillis() - lastTime;

        if (elapsed > STALE_FLAG_THRESHOLD_MS) {
            prefs.edit().remove(KEY_INIT_IN_PROGRESS).commit();
            Log.i(TAG, "Stale init flag cleared (age=" + (elapsed / 1000) + "s). Crash count preserved at " + prefs.getInt(KEY_CRASH_COUNT, 0));
            return;
        }

        int count = prefs.getInt(KEY_CRASH_COUNT, 0) + 1;
        prefs.edit()
            .putInt(KEY_CRASH_COUNT, count)
            .putLong(KEY_LAST_CRASH_TIME, System.currentTimeMillis())
            .remove(KEY_INIT_IN_PROGRESS)
            .commit();

        Log.w(TAG, "Detected crash during native init (count=" + count + ")");

        if (count >= MAX_CRASHES) {
            Log.e(TAG, "Max crash count reached. Native init blocked for " + (COOLDOWN_MS / 1000) + "s");
        }
    }

    public boolean shouldAttemptInit() {
        int count = prefs.getInt(KEY_CRASH_COUNT, 0);
        if (count >= MAX_CRASHES) {
            long lastTime = prefs.getLong(KEY_LAST_CRASH_TIME, 0);
            long remaining = COOLDOWN_MS - (System.currentTimeMillis() - lastTime);
            if (remaining > 0) {
                Log.w(TAG, "Blocking native init: " + count + " recent crashes. Cooldown remaining: " + (remaining / 1000) + "s");
                return false;
            } else {
                Log.i(TAG, "Cooldown expired after " + count + " crashes. Resetting and allowing attempt.");
                reset();
                return true;
            }
        }
        return true;
    }

    public void markInitStarting() {
        prefs.edit()
            .putBoolean(KEY_INIT_IN_PROGRESS, true)
            .putLong(KEY_LAST_CRASH_TIME, System.currentTimeMillis())
            .commit();
    }

    public void markInitSucceeded() {
        prefs.edit().clear().commit();
        Log.i(TAG, "Native init succeeded. Crash counter reset.");
    }

    public void markInitFailedGracefully() {
        prefs.edit()
            .remove(KEY_INIT_IN_PROGRESS)
            .commit();
        Log.i(TAG, "Native init returned false (no crash). Flag cleared.");
    }

    public void reset() {
        prefs.edit().clear().commit();
    }

    public int getCrashCount() {
        return prefs.getInt(KEY_CRASH_COUNT, 0);
    }
}
