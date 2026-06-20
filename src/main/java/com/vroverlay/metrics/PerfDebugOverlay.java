package com.vroverlay.metrics;

import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Point;
import java.util.Random;

public class PerfDebugOverlay {
    private static final String TAG = "PerfDebugOverlay";

    public static final int TEXTURE_WIDTH = 256;
    public static final int TEXTURE_HEIGHT = 136;

    static final int CLEAR_COLOR = 0;
    static final int WHITE_COLOR = -1;
    static final int RED_COLOR = -65536;
    static final int GREEN_COLOR = -16711936;
    static final int CYAN_COLOR = -16711681;
    static final int MAGENTA_COLOR = -65281;
    static final int YELLOW_COLOR = -256;

    static final int PixelColorChannelOffsetR = 0;
    static final int PixelColorChannelOffsetG = 8;
    static final int PixelColorChannelOffsetB = 16;
    static final int PixelColorChannelOffsetA = 24;

    public interface TextureReceiver {
        void updateTexture(int[] texture, int textureWidth, int textureHeight,
                          int displayedWidth, int displayedHeight, int textureUpdateCount);
    }

    private static final int[] AsciiMask = new int[128];

    static {
        String fontData =
            "                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 #    #    #    #         #   # #  # #                     # # ##### # # ##### # #        #   ### # #   ###   # # ###      ##  ### #   #   # ###  ## ##  #  #  ##  #  ###  #  ## #   #    #                         #   #    #    #    #     #   #     #    #    #    #   #   # #   #   # #                 #    #  #####  #    #                               #   #            ####                                           #     #   #    #   #    #   #     ##  #  # # ## ## # #  #  ##    #   ##    #    #    #   ###  ##  #  #    #   #   #   #### ###     #  ##     #    # ###  # #  # #  ####   #    #    #  #### #    ###     #    # ###   ##  #    ###  #  # #  #  ##  ####    #   #    #   #    #    ##  #  #  ##  #  # #  #  ##   ##  #  # #  #  ###    #  ##         #              #               #              #   #         ## ##  #     ##     ##           ####      ####          ##     ##     #  ## ##     ##  #  #    #   #         #       ### # # ### ### ### ###   ##  #  # #### #    #   ##     ## #   #   #   # # #   #   #   # #   #   # #   #   # #   # # # #   # # # #   # # #   # # #   #  #  #  #  #  #   #  #  #  #   #  #   #   #  #   #    ##   ##   ##  ##    ##   ##   ##  ##    ##   ##   ##  ##     #    #    #    #    #   #    #   #    #   #     #   #     #  #     #  #      #  #       #        #   #   #   #  #    #   #   #  #     #  #      #  #       #        #   #    #  # #    #  # #   # #     # #      ##       #   #    #  # #     # # #   # #     # #      ##        #  # # #  #  # #  #  #  # #  #  # #  #   # #   #  #  #  #   #  #   #   #  #   #    ##   ##   ##  ##    ##   ##   ##  ##    ##   ##   ##  ##   #   #   #   #   #   #  #  #  #  #  #  #  #  #  #  #  #   #  #  #  #  #   #  #   #   #  #   #    #   #    #   #    #  #  #  #  #  #  #  #  #  #  #  #   #  #  #  #  #   #  #   #   #  #   #    #   #    #   #    #  #  #  #  #  #  #  #  #  #  #  #   #  #  #  #  #   #  #   #   #  #   #    #    #    #    #   #     #   #    #   #     #   #    #   #     #   #    #   #    #   #      #  #      #  #       # #       #        #   #   #   #  #    #   #   #  #     #  #      #  #       #        #   #    #  # #    #  # #   # #     # #      ##       #   #    #  # #     # # #   # #     # #      ##        ";
        StringBuilder fontDataBuilder = new StringBuilder(fontData);
        while (fontDataBuilder.length() < 3840) {
            fontDataBuilder.append(' ');
        }
        fontData = fontDataBuilder.toString();
        int i = 0;
        while (i < 128) {
            int mask = 0;
            for (int x = 0; x < 5; x++) {
                for (int y = 0; y < 6; y++) {
                    int idx = ((i * 5) + x) * 6 + y;
                    int c = (idx < fontData.length()) ? fontData.charAt(idx) : ' ';
                    mask |= c == 35 ? 1 << (((4 - x) * 6) + (5 - y)) : 0;
                }
            }
            int width;
            if (i == 49) {
                width = 2;
            } else if (mask == 0) {
                width = 1;
            } else if ((1056964608 & mask) == 0) {
                width = (16515072 & mask) != 0 ? 2 : (258048 & mask) != 0 ? 1 : 0;
            } else {
                width = 3;
            }
            AsciiMask[i] = (width << 30) | mask;
            i++;
        }
    }

    private int[] Texture = new int[TEXTURE_WIDTH * TEXTURE_HEIGHT];
    private final Object Mutex = new Object();
    private int DisplayedWidth = 0;
    private int DisplayedHeight = 0;
    private boolean DirtyTexture = true;
    private boolean DirtyLayout = true;
    private int TextureUpdateCount = 0;

    private boolean GraphsEnabled = true;
    private boolean StatsEnabled = true;
    private boolean AppNameEnabled = true;
    private boolean DebugDataEnabled = false;
    private boolean PlayTimeEnabled = false;

    private int[] StatDrawOrder;
    private int[] GraphDrawOrder;
    private int VisibleStatCount = 0;
    private int VisibleGraphCount = 0;

    private int[] mStats = new int[128];
    private float[] mGraphValues = new float[128];
    private int[] mGraphIntValues = new int[128];
    private float[] mGraphColors = new float[128];
    private int mGraphIndex = 0;
    private int mGraphCount = 0;
    private long mLastUpdateTime = 0;
    private String mAppName = "VR Overlay";
    private String mDebugData = null;

    private static final int[] DefaultEnabledStats = {25, 17, 18, 60, 69, 36, 53, 0, 1};
    private static final int[] DefaultEnabledGraphs = {25, 53};

    public static int clamp(int a, int b, int t) {
        return t > b ? b : t < a ? a : t;
    }

    public static int min(int a, int b) {
        return a < b ? a : b;
    }

    public static int max(int a, int b) {
        return a < b ? b : a;
    }

    public static int getDigitCount(int number) {
        int count = 1;
        while (number >= 10) {
            number /= 10;
            count++;
        }
        return count;
    }

    public static ColorMatrixColorFilter GetBgraColorFilter() {
        return new ColorMatrixColorFilter(new ColorMatrix(new float[]{
            0.0f, 0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f, 0.0f,
            1.0f, 0.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        }));
    }

    public static int GetColor(boolean infoOnly, float redPercent, float greenPercent,
                                float statPercent, int drawIndex, float alpha) {
        float green = 255.0f;
        float red = 0.0f;

        if (!infoOnly && redPercent != greenPercent) {
            if (statPercent <= redPercent) {
                green = 0.0f;
                red = 255.0f;
            } else if (statPercent >= greenPercent) {
                green = 255.0f;
                red = 0.0f;
            } else {
                float ratio = (statPercent - redPercent) / (greenPercent - redPercent);
                green = ratio * 255.0f;
                red = (1.0f - ratio) * 255.0f;
            }
        }

        int a = (int) (alpha * 255.0f);
        int r = (int) (alpha * red);
        int g = (int) (alpha * green);
        return (a << 24) | (r << 16) | (g << 8);
    }

    private void initStats() {
        for (int i = 0; i < 128; i++) {
            mStats[i] = 0;
        }
    }

    public void UpdateStats(int[] stats) {
        if (stats != null) {
            for (int i = 0; i < min(stats.length, mStats.length); i++) {
                mStats[i] = stats[i];
            }
        }
        updateGraphData();
    }

    private void updateGraphData() {
        int fps = mStats[25];
        if (fps > 0) {
            float graphColor = ((fps - 0) * 100) / 72.0f;
            if (mGraphCount == 0) {
                int prev = (mGraphIndex + 128 - 1) % 128;
                mGraphIntValues[prev] = fps;
                mGraphColors[prev] = graphColor;
            }
            if (mGraphCount < 128) {
                mGraphCount++;
            }
            mGraphIntValues[mGraphIndex] = fps;
            mGraphColors[mGraphIndex] = graphColor;
            mGraphIndex = (mGraphIndex + 1) % 128;
        }
    }

    public void UpdateTexture(TextureReceiver receiver) {
        synchronized (Mutex) {
            doUpdateTexture();
            if (DirtyTexture) {
                DirtyTexture = false;
                TextureUpdateCount++;
            }
            if (receiver != null) {
                receiver.updateTexture(Texture, TEXTURE_WIDTH, TEXTURE_HEIGHT,
                    DisplayedWidth, DisplayedHeight, TextureUpdateCount);
            }
        }
    }

    public Point GetTextureDimensions() {
        synchronized (Mutex) {
            UpdateTexture(null);
            return new Point(DisplayedWidth, DisplayedHeight);
        }
    }

    private void doUpdateTexture() {
        if (DirtyLayout) {
            DirtyLayout = false;
            calcLayout();
        }

        System.arraycopy(Texture, 0, Texture, TEXTURE_WIDTH, TEXTURE_WIDTH);
        for (int i = 1; i < TEXTURE_HEIGHT && (i << 1) <= TEXTURE_HEIGHT; i <<= 1) {
            int copyRows = (i << 1) < TEXTURE_HEIGHT ? i : TEXTURE_HEIGHT - i;
            System.arraycopy(Texture, TEXTURE_WIDTH, Texture,
                (i * TEXTURE_WIDTH), min(copyRows, TEXTURE_HEIGHT - i) * TEXTURE_WIDTH);
        }

        renderStats();
        renderGraphs();
        renderAppName();
        renderDebugData();
        renderPlayTime();

        DirtyTexture = true;
    }

    private void calcLayout() {
        int y = 0;

        if (AppNameEnabled) {
            y += 8;
        }
        if (PlayTimeEnabled) {
            y += 8;
        }

        VisibleStatCount = 0;
        if (StatsEnabled) {
            for (int stat : DefaultEnabledStats) {
                if (VisibleStatCount < 16) {
                    VisibleStatCount++;
                }
            }
            y += VisibleStatCount * 7;
        }

        VisibleGraphCount = 0;
        if (GraphsEnabled) {
            for (int graph : DefaultEnabledGraphs) {
                if (VisibleGraphCount < 4) {
                    VisibleGraphCount++;
                }
            }
            y += VisibleGraphCount * 50;
        }

        if (DebugDataEnabled && mDebugData != null) {
            y += 10;
        }

        DisplayedWidth = TEXTURE_WIDTH;
        DisplayedHeight = min(y, TEXTURE_HEIGHT);
        if (DisplayedHeight <= 0) DisplayedHeight = 10;
    }

    private void renderStats() {
        if (!StatsEnabled) return;

        int y = 0;
        if (AppNameEnabled) y += 8;
        if (PlayTimeEnabled) y += 8;

        Object[][] statDefs = {
            {25, "FPS", 0, 72, 0, 72, 20.0f, 50.0f, false},
            {17, "CPU", 0, 8, 0, 8, 3.0f, 6.0f, false},
            {18, "GPU", 0, 7, 0, 7, 3.0f, 5.0f, false},
            {60, "CPU%", 0, 100, 0, 100, 50.0f, 80.0f, false},
            {69, "GPU%", 0, 100, 0, 100, 50.0f, 80.0f, false},
            {36, "STALE", 0, 400, 0, 400, 25.0f, 50.0f, false},
            {53, "GPU T", 0, 262143, 0, 262143, 15.0f, 50.0f, false},
            {0, "MEM", 0, 9999, 0, 9999, 25.0f, 15.0f, false},
            {1, "PSS", 0, 9999, 0, 9999, 75.0f, 85.0f, false},
        };

        for (int i = 0; i < min(statDefs.length, VisibleStatCount); i++) {
            Object[] def = statDefs[i];
            int stat = ((Number) def[0]).intValue();
            String label = (String) def[1];
            int value = mStats[stat];
            int graphMin = ((Number) def[4]).intValue();
            int graphMax = ((Number) def[5]).intValue();
            float redP = ((Number) def[6]).floatValue();
            float greenP = ((Number) def[7]).floatValue();
            boolean infoOnly = (Boolean) def[8];

            float statPercent = graphMax > graphMin ?
                ((value - graphMin) * 100.0f) / (graphMax - graphMin) : 0.0f;
            int color = GetColor(infoOnly, redP, greenP, statPercent, 0, 1.0f);

            drawWord(2, y, label, WHITE_COLOR);
            drawWord(2 + (label.length() + 1) * 6, y, String.valueOf(value), color);
            y += 7;
        }
    }

    private void renderGraphs() {
        if (!GraphsEnabled) return;

        int y = 0;
        if (AppNameEnabled) y += 8;
        if (PlayTimeEnabled) y += 8;
        y += VisibleStatCount * 7;

        Object[][] graphDefs = {
            {25, "FPS", 0, 72, 20.0f, 50.0f},
            {53, "GPU TIME", 0, 20000, 15.0f, 50.0f},
        };

        for (int i = 0; i < min(graphDefs.length, VisibleGraphCount); i++) {
            Object[] def = graphDefs[i];
            String label = (String) def[1];
            int graphMin = ((Number) def[2]).intValue();
            int graphMax = ((Number) def[3]).intValue();
            float redP = ((Number) def[4]).floatValue();
            float greenP = ((Number) def[5]).floatValue();

            drawWord(2, y, label, CYAN_COLOR);
            y += 2;

            int graphWidth = TEXTURE_WIDTH - 4;
            int graphHeight = 40;

            for (int gx = 0; gx < graphWidth; gx++) {
                int dataIdx = (mGraphIndex + gx) % 128;
                int value = mGraphIntValues[dataIdx];
                float graphColor = mGraphColors[dataIdx];

                int pixelH = (value * (graphHeight - 2)) / max(1, graphMax - graphMin);
                pixelH = clamp(0, graphHeight - 2, pixelH);

                int color = GetColor(false, redP, greenP, graphColor, gx, 1.0f);

                drawPixel(2 + gx, y + (graphHeight - 2) - pixelH, color);
            }

            for (int gx = 0; gx < graphWidth; gx += 18) {
                drawPixel(2 + gx, y + graphHeight - 2, 0x40FFFFFF);
            }

            y += graphHeight + 8;
        }
    }

    private void renderAppName() {
        if (!AppNameEnabled) return;
        if (mAppName != null && mAppName.length() > 0) {
            drawWord(2, 0, mAppName, CYAN_COLOR);
        }
    }

    private void renderDebugData() {
    }

    private void renderPlayTime() {
        if (!PlayTimeEnabled) return;

        long elapsedMs = System.currentTimeMillis() - mLastUpdateTime;
        if (elapsedMs < 0) elapsedMs = 0;
        long totalSeconds = elapsedMs / 1000;
        int hours = (int) (totalSeconds / 3600);
        int minutes = (int) ((totalSeconds % 3600) / 60);
        int seconds = (int) (totalSeconds % 60);

        int y = AppNameEnabled ? 8 : 0;
        String timeStr = pad2(hours) + ":" + pad2(minutes) + ":" + pad2(seconds);
        drawWord(2, y, timeStr, WHITE_COLOR);
    }

    private static String pad2(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    private void drawPixel(int i, int j, int color) {
        if (j < 0 || j >= TEXTURE_HEIGHT || i < 0 || i >= TEXTURE_WIDTH) return;
        Texture[(TEXTURE_HEIGHT - 1 - j) * TEXTURE_WIDTH + i] = color;
    }

    private int drawMask(int pixelOffsetX, int pixelOffsetY, int mask, int color) {
        int width = ((mask >> 30) & 3) + 2;
        for (int i = 0; i < width * 6; i++) {
            drawPixel(((pixelOffsetX + width) - (i / 6)) - 1,
                (i % 6) + pixelOffsetY,
                ((mask >> i) & 1) != 0 ? color : 0);
        }
        return width;
    }

    private int drawWord(int pixelOffsetX, int pixelOffsetY, String str, int color) {
        int size = 0;
        for (int i = 0; i < str.length() && pixelOffsetX + size < TEXTURE_WIDTH - 10; i++) {
            char c = str.charAt(i);
            int mask;
            if (c >= 0 && c < 128) {
                mask = AsciiMask[c];
            } else {
                mask = AsciiMask[(int)'?'];
            }
            size += drawMask(pixelOffsetX + size, pixelOffsetY, mask, color) + 1;
        }
        return size;
    }

    public void EnableGraphs(boolean enable) {
        synchronized (Mutex) {
            if (GraphsEnabled != enable) {
                GraphsEnabled = enable;
                DirtyLayout = true;
                DirtyTexture = true;
            }
        }
    }

    public void EnableStats(boolean enable) {
        synchronized (Mutex) {
            if (StatsEnabled != enable) {
                StatsEnabled = enable;
                DirtyLayout = true;
                DirtyTexture = true;
            }
        }
    }

    public void EnableAppName(boolean enable) {
        synchronized (Mutex) {
            if (AppNameEnabled != enable) {
                AppNameEnabled = enable;
                DirtyLayout = true;
                DirtyTexture = true;
            }
        }
    }

    public void EnablePlayTime(boolean enable) {
        synchronized (Mutex) {
            if (PlayTimeEnabled != enable) {
                PlayTimeEnabled = enable;
                DirtyLayout = true;
                DirtyTexture = true;
            }
        }
    }

    public void SetDebugData(String debugText) {
        synchronized (Mutex) {
            if (debugText != null && !debugText.equals(mDebugData)) {
                mDebugData = debugText;
                DirtyTexture = true;
            }
        }
    }

    public void SetAppName(String appName) {
        synchronized (Mutex) {
            if (appName != null && !appName.equals(mAppName)) {
                mAppName = appName;
                DirtyTexture = true;
            }
        }
    }

    public String GetAppName() {
        return mAppName;
    }

    public boolean IsGraphEnabled() { return GraphsEnabled; }
    public boolean IsStatsEnabled() { return StatsEnabled; }
    public boolean IsAppNameEnabled() { return AppNameEnabled; }
    public boolean IsDebugDataEnabled() { return DebugDataEnabled; }

    private native void nativeSetPosition(float pitch, float yaw, float distance, float scale);
    private native void nativeSetHeadLocked(boolean locked);

    static {
        try {
            System.loadLibrary("perfoverlay");
            android.util.Log.i(TAG, "Native library loaded");
        } catch (UnsatisfiedLinkError e) {
            android.util.Log.e(TAG, "Failed to load native library", e);
        }
    }

    public void SetPosition(float pitch, float yaw, float distance, float scale) {
        nativeSetPosition(pitch, yaw, distance, scale);
    }

    public void SetHeadLocked(boolean locked) {
        nativeSetHeadLocked(locked);
    }
}
