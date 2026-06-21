package com.vroverlay.metrics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class PerfDebugOverlayWeb {
    private static final String TAG = "PerfDebugOverlayWeb";

    public static final int OVERLAY_WIDTH = 512;
    public static final int OVERLAY_HEIGHT = 272;

    private final WebView webView;
    private final Context context;
    private volatile boolean pageReady = false;
    private Bitmap lastBitmap;

    public interface BitmapCallback {
        void onBitmapReady(Bitmap bitmap);
    }

    public PerfDebugOverlayWeb(Context context) {
        this.context = context;
        webView = new WebView(context);
        webView.getSettings().setJavaScriptEnabled(false);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setInitialScale(100);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.i(TAG, "WebView page finished loading");
                pageReady = true;
            }
        });

        String htmlContent = generateHtmlContent();
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null);

        webView.measure(
            View.MeasureSpec.makeMeasureSpec(OVERLAY_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(OVERLAY_HEIGHT, View.MeasureSpec.EXACTLY)
        );
        webView.layout(0, 0, OVERLAY_WIDTH, OVERLAY_HEIGHT);
    }

    private String generateHtmlContent() {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=512, initial-scale=1.0'>" +
                "<style>" +
                "* { margin: 0; padding: 0; box-sizing: border-box; }" +
                "body { display: flex; justify-content: center; align-items: center; min-height: 272px; background: transparent; }" +
                ".hello-box { background-color: #00ff00; padding: 20px 40px; border-radius: 8px; }" +
                ".hello-text { color: #000000; font-family: Arial, sans-serif; font-size: 18px; font-weight: bold; text-align: center; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='hello-box'>" +
                "<div class='hello-text'>Hello World</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }

    public void captureBitmap(BitmapCallback callback) {
        if (!pageReady) {
            return;
        }

        try {
            Bitmap bitmap = Bitmap.createBitmap(OVERLAY_WIDTH, OVERLAY_HEIGHT, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.TRANSPARENT);
            webView.draw(canvas);

            lastBitmap = bitmap;
            if (callback != null) {
                callback.onBitmapReady(bitmap);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to capture WebView bitmap", e);
        }
    }

    public Bitmap getLastBitmap() {
        return lastBitmap;
    }

    public boolean isPageReady() {
        return pageReady;
    }

    public void setContent(String html) {
        pageReady = false;
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }
}
