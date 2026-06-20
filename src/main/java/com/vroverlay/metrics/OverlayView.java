package com.vroverlay.metrics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

public class OverlayView extends View {
    private Bitmap mBitmap;
    private final Paint mPaint = new Paint();
    private final Rect mSrcRect = new Rect();
    private final Rect mDstRect = new Rect();

    public OverlayView(Context context) {
        super(context);
        mPaint.setAntiAlias(false);
        mPaint.setFilterBitmap(false);
    }

    public void updateBitmap(Bitmap bitmap) {
        mBitmap = bitmap;
        postInvalidate();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBitmap != null && !mBitmap.isRecycled()) {
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            mSrcRect.set(0, 0, mBitmap.getWidth(), mBitmap.getHeight());
            mDstRect.set(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(mBitmap, mSrcRect, mDstRect, mPaint);
        }
    }
}
