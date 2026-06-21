package com.oculus.ovrmonitormetricsservice;

import android.graphics.Point;
import com.oculus.ovrmonitormetricsservice.device.DeviceProperties;
import com.oculus.ovrmonitormetricsservice.device.DeviceType;
import com.oculus.ovrmonitormetricsservice.device.SettingsConfig;
import com.oculus.ovrmonitormetricsservice.log.Log;
import java.util.regex.Pattern;

public class PerfDebugOverlay {
    static final int ClearColor = 0;
    static final int WhiteColor = -1;
    static final int CyanColor = -16711681;
    static final int GreenColor = -16711936;
    static final int RedColor = -65536;
    static final int MagentaColor = -65281;
    static final int PixelColorChannelOffsetA = 24;
    static final int PixelColorChannelOffsetB = 16;
    static final int PixelColorChannelOffsetG = 8;
    static final int PixelColorChannelOffsetR = 0;
    static final int PixelColorMaxValueA = 255;
    static final int PixelColorMaxValueB = 255;
    static final int PixelColorMaxValueG = 255;
    static final int PixelColorMaxValueR = 255;
    static final String TAG = "PerfDebugOverlay";
    public static final int TextureHeight = 136;
    public static final int TextureWidth = 256;

    String DebugData;
    boolean DebugDataEnabled;
    int DebugDataEndOffsetX;
    int DebugDataEndOffsetY;
    int DebugDataStartOffsetX;
    int LastDebugDataHeight;
    int LastDebugDataWidth;
    SettingsConfig SettingsConfig;
    int TextureUpdateCount;
    static final Pattern ColorOpenPattern = Pattern.compile("<color\\s*=\\s*#?[A-Za-z0-9]+>");
    static final Pattern ColorClosePattern = Pattern.compile("</\\s*color>");
    static final int[] AsciiMask = new int[128];
    public int[] Texture = new int[34816];
    Object Mutex = new Object();
    int DisplayedHeight = 0;
    int DisplayedWidth = 0;
    boolean DirtyTexture = true;
    boolean DirtyLayout = true;
    boolean DirtyDebugData = true;
    int DebugDataStartOffsetY = 0;

    public interface TextureReceiver {
        void updateTexture(int[] iArr, int i, int i2, int i3, int i4, int i5);
    }

    static {
        String bitmap = "                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 #    #    #    #         #   # #  # #                     # # ##### # # ##### # #        #   ### # #   ###   # # ###      ##  ### #   #   # ###  ## ##  #  #  ##  #  ###  #  ## #   #    #                         #   #    #    #    #     #   #     #    #    #    #   #   # #   #   # #                 #    #  #####  #    #                               #   #            ####                                           #     #   #    #   #    #   #     ##  #  # # ## ## # #  #  ##    #   ##    #    #    #   ###  ##  #  #    #   #   #   #### ###     #  ##     #    # ###  # #  # #  ####   #    #    #  #### #    ###     #    # ###   ##  #    ###  #  # #  #  ##  ####    #   #    #   #    #    ##  #  #  ##  #  # #  #  ##   ##  #  # #  #  ###    #  ##         #              #               #              #   #         ## ##  #     ##     ##           ####      ####          ##     ##     #  ## ##     ##  #  #    #   #         #       ### # # ### ### ### ###   ##  #  # #### #";
        int i = 0;
        while (true) {
            int i2 = 3;
            if (i < 128) {
                int mask = 0;
                for (int x = 0; x < 5; x++) {
                    for (int y = 0; y < 6; y++) {
                        int idx = (i * 30) + (x * 6) + y;
                        int c = idx < bitmap.length() ? bitmap.charAt(idx) : 32;
                        mask |= c == 35 ? 1 << (((4 - x) * 6) + (5 - y)) : 0;
                    }
                }
                if (i == 49) {
                    i2 = 2;
                } else if (mask == 0) {
                    i2 = 1;
                } else if ((1056964608 & mask) == 0) {
                    i2 = (16515072 & mask) != 0 ? 2 : (258048 & mask) != 0 ? 1 : 0;
                }
                int width = i2;
                AsciiMask[i] = (width << 30) | mask;
                i++;
            } else {
                break;
            }
        }
    }

    public static int clamp(int a, int b, int t) {
        return t > b ? b : t < a ? a : t;
    }

    public static byte clamp(byte a, byte b, byte t) {
        return t > b ? b : t < a ? a : t;
    }

    public static float clamp(float a, float b, float t) {
        return t > b ? b : t < a ? a : t;
    }

    public static long clamp(long a, long b, long t) {
        return t > b ? b : t < a ? a : t;
    }

    public static byte min(byte a, byte b) {
        return a < b ? a : b;
    }

    public static int min(int a, int b) {
        return a < b ? a : b;
    }

    public static float min(float a, float b) {
        return a < b ? a : b;
    }

    public static long min(long a, long b) {
        return a < b ? a : b;
    }

    public static byte max(byte a, byte b) {
        return a < b ? b : a;
    }

    public static int max(int a, int b) {
        return a < b ? b : a;
    }

    public static float max(float a, float b) {
        return a < b ? b : a;
    }

    public static long max(long a, long b) {
        return a < b ? b : a;
    }

    public static int round(float v) {
        return (int) (0.5f + v);
    }

    public static int getDigitCount(int number) {
        int count = 1;
        while (number >= 10) {
            number /= 10;
            count++;
        }
        return count;
    }

    public PerfDebugOverlay(SettingsConfig settingsConfig) {
        this.DebugDataEnabled = settingsConfig.isDebugDataEnabled();
        this.SettingsConfig = settingsConfig;
    }

    public Point GetTextureDimensions() {
        synchronized (this.Mutex) {
            UpdateTexture(null);
            return new Point(this.DisplayedWidth, this.DisplayedHeight);
        }
    }

    public void SetDebugData(String debugText) {
        synchronized (this.Mutex) {
            if (debugText != null) {
                if (!debugText.equals(this.DebugData)) {
                    this.DebugData = debugText;
                    this.DirtyDebugData = true;
                    this.DirtyTexture = true;
                    Log.i(TAG, "Debug data set: " + debugText);
                }
            }
        }
    }

    public void EnableDebugData(boolean enable) {
        synchronized (this.Mutex) {
            if (this.DebugDataEnabled != enable) {
                this.DebugDataEnabled = enable;
                this.DirtyLayout = true;
                this.DirtyTexture = true;
            }
        }
    }

    public void EnableStats(boolean enable) {
    }

    public void EnableGraphs(boolean enable) {
    }

    public void EnableStat(int stat, boolean enable) {
    }

    public void EnableGraph(int stat, boolean enable) {
    }

    void DrawPixel(int i, int j, int color) {
        if (j < 0 || j >= 136 || i < 0 || i >= 256) {
            return;
        }
        this.Texture[((135 - j) * 256) + i] = color;
    }

    int DrawMask(int pixel_offset_x, int pixel_offset_y, int mask, int color) {
        int width = ((mask >> 30) & 3) + 2;
        int count = width * 6;
        for (int i = 0; i < count; i++) {
            DrawPixel(((pixel_offset_x + width) - (i / 6)) - 1, (i % 6) + pixel_offset_y, ((mask >> i) & 1) != 0 ? color : 0);
        }
        return width;
    }

    int DrawNumber(int pixel_offset_x, int pixel_offset_y, int number, int digits, int color) {
        int i = digits - 1;
        while (i >= 0) {
            int digit = number % 10;
            DrawMask((i * 5) + pixel_offset_x, pixel_offset_y, GetLetterMask(digit + 48), (number != 0 || i >= digits + (-1)) ? color : 0);
            number /= 10;
            i--;
        }
        return digits * 5;
    }

    int DrawLetter(int pixel_offset_x, int pixel_offset_y, int letter, int color) {
        int mask = GetLetterMask(letter);
        return DrawMask(pixel_offset_x, pixel_offset_y, mask, color);
    }

    static int GetLetterMask(int letter) {
        if (letter >= 0 && letter <= 127) {
            return AsciiMask[letter];
        }
        return -7989121;
    }

    int MeasureWord(String str) {
        int size = 0;
        int len = str.length();
        for (int i = 0; i < len; i++) {
            int mask = GetLetterMask(str.charAt(i));
            size += ((mask >> 30) & 3) + 2 + 1;
        }
        return size;
    }

    int DrawWord(int pixel_offset_x, int pixel_offset_y, String str, int color) {
        int size = 0;
        int len = str.length();
        for (int i = 0; i < len; i++) {
            size += DrawLetter(pixel_offset_x + size, pixel_offset_y, str.charAt(i), color) + 1;
        }
        return size;
    }

    static int GetLetterWidth(int letter) {
        int mask = GetLetterMask(letter);
        return ((mask >> 30) & 3) + 2;
    }

    static int GetWordWidth(String str) {
        int size = 0;
        int len = str.length();
        for (int i = 0; i < len; i++) {
            size += GetLetterWidth(str.charAt(i)) + 1;
        }
        return size;
    }

    static int GetColor(boolean infoOnly, float redPercent, float greenPercent, float statPercent, int drawIndex, float alpha) {
        float green;
        float range = greenPercent - redPercent;
        float red = 0.0f;
        float blue = 0.0f;
        if (!infoOnly && redPercent != greenPercent) {
            green = clamp(0.0f, 1.0f, ((statPercent - redPercent) * 2.0f) / range) * 255.0f;
            float green2 = greenPercent - statPercent;
            red = clamp(0.0f, 1.0f, (green2 * 2.0f) / range) * 255.0f;
        } else {
            green = 255.0f;
        }
        switch (drawIndex) {
            case 1:
                blue = 255.0f;
                break;
            case 2:
                green = 255.0f - green;
                red = 255.0f - red;
                blue = 255.0f;
                break;
            case 3:
                blue = 127.0f;
                green = (3.0f * green) / 4.0f;
                red /= 2.0f;
                break;
            case 4:
                blue = 127.0f;
                red = 255.0f - (red / 2.0f);
                green = 255.0f - ((3.0f * green) / 4.0f);
                break;
        }
        return (((int) (alpha * red)) << 0) | (((int) (alpha * green)) << 8) | (((int) (alpha * blue)) << 16) | (((int) (255.0f * alpha)) << 24);
    }

    private void ClearRegion(int x, int y, int w, int h) {
        if (w == 0 || h == 0 || y >= 136) {
            return;
        }
        if (x <= 1 && w >= 254) {
            if (y != 0) {
                System.arraycopy(this.Texture, 0, this.Texture, y * 256, 256);
            }
            for (int i = 1; i < h && y + i < 136; i <<= 1) {
                int copyRows = (i << 1) < h ? i : h - i;
                System.arraycopy(this.Texture, y * 256, this.Texture, (y + i) * 256, Math.min(copyRows, 136 - (y + i)) * 256);
            }
            return;
        }
        int w2 = Math.min(w, 256 - x);
        for (int i2 = 0; i2 < h && y + i2 < 136; i2++) {
            System.arraycopy(this.Texture, 0, this.Texture, ((y + i2) * 256) + x, w2);
        }
    }

    private void ClearDebugData() {
        int clearHeight = this.LastDebugDataHeight - 1;
        if (clearHeight <= 0 || this.LastDebugDataWidth <= 0) {
            return;
        }
        if (this.DebugDataEndOffsetX - this.DebugDataStartOffsetX >= this.DisplayedWidth - 2) {
            ClearRegion(0, (136 - this.DebugDataEndOffsetY) + 1, 256, clearHeight);
        } else {
            ClearRegion(this.DebugDataStartOffsetX, (136 - this.DebugDataEndOffsetY) + 1, this.LastDebugDataWidth, clearHeight);
        }
    }

    private void DrawDebugData(String debugText) {
        if (debugText == null || debugText.length() == 0) {
            return;
        }

        ClearDebugData();

        int offsetY = TextureHeight - 8;
        int offsetX = 4;

        String[] lines = debugText.split("\n");
        for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
            String line = lines[lineIdx];
            if (line.length() > 0) {
                int wordWidth = GetWordWidth(line);
                if (wordWidth > TextureWidth - 8) {
                    offsetX = 2;
                } else {
                    offsetX = 4;
                }
                DrawWord(offsetX, offsetY - (lineIdx * 8), line, WhiteColor);
            }
        }

        this.DebugDataStartOffsetX = 0;
        this.DebugDataStartOffsetY = offsetY - ((lines.length - 1) * 8);
        this.DebugDataEndOffsetX = TextureWidth;
        this.DebugDataEndOffsetY = 8;
        this.LastDebugDataWidth = TextureWidth;
        this.LastDebugDataHeight = lines.length * 8 + 2;
    }

    public void UpdateTexture(TextureReceiver receiver) {
        synchronized (this.Mutex) {
            if (this.DirtyTexture) {
                this.DirtyTexture = false;

                if (this.DirtyDebugData) {
                    this.DirtyDebugData = false;
                    Log.i(TAG, "Drawing debug data: " + (this.DebugData != null ? this.DebugData : "null"));
                    DrawDebugData(this.DebugData);
                }

                this.DisplayedWidth = TextureWidth;
                this.DisplayedHeight = TextureHeight;
                this.TextureUpdateCount++;
            }

            if (receiver != null) {
                receiver.updateTexture(this.Texture, TextureWidth, TextureHeight, this.TextureUpdateCount, 0, 0);
            }
        }
    }
}