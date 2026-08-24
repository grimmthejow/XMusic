package com.xapps.media.xmusic.widget;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class GradientView extends View {
    private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

    private Bitmap bitmap;
    private Canvas bitmapCanvas;

    private long startTime;

    private int dominantColor = Color.parseColor("#FF2D55");
    private int accentColor1 = Color.parseColor("#5856D6");
    private int accentColor2 = Color.parseColor("#AF52DE");

    private int bitmapWidth;
    private int bitmapHeight;

    public GradientView(Context context, AttributeSet attrs) {
        super(context, attrs);
        startTime = System.currentTimeMillis();
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public void setColors(int newDominant, int newAccent1, int newAccent2) {
        animateColor(dominantColor, newDominant, val -> dominantColor = val);
        animateColor(accentColor1, newAccent1, val -> accentColor1 = val);
        animateColor(accentColor2, newAccent2, val -> accentColor2 = val);
    }

    private void animateColor(int from, int to, ColorUpdateListener listener) {
        if (from == to) {
            return;
        }

        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        animator.setDuration(1000);
        animator.addUpdateListener(animation -> {
            listener.onUpdate((int) animation.getAnimatedValue());
        });
        animator.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        int scale = 6;

        bitmapWidth = Math.max(1, (w + scale - 1) / scale);
        bitmapHeight = Math.max(1, (h + scale - 1) / scale);

        if (bitmap != null) {
            bitmap.recycle();
        }

        bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        bitmapCanvas = new Canvas(bitmap);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bitmap == null || bitmapCanvas == null) {
            return;
        }

        float time = (System.currentTimeMillis() - startTime) / 2500f;

        float scaleX = (float) bitmapWidth / getWidth();
        float scaleY = (float) bitmapHeight / getHeight();

        bitmapCanvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);

        drawBlob(
                bitmapCanvas,
                bitmapWidth * 0.5f + (float) Math.sin(time * 0.3f) * bitmapWidth * 0.1f,
                bitmapHeight * 0.5f + (float) Math.cos(time * 0.3f) * bitmapHeight * 0.1f,
                bitmapWidth * 2.5f,
                dominantColor
        );

        drawBlob(
                bitmapCanvas,
                bitmapWidth * 0.2f + (float) Math.cos(time * 0.8f) * bitmapWidth * 0.4f,
                bitmapHeight * 0.8f + (float) Math.sin(time * 0.6f) * bitmapHeight * 0.3f,
                bitmapWidth * 1.1f,
                accentColor1
        );

        drawBlob(
                bitmapCanvas,
                bitmapWidth * 0.8f + (float) Math.sin(time * 0.5f) * bitmapWidth * 0.3f,
                bitmapHeight * 0.2f + (float) Math.cos(time * 0.9f) * bitmapHeight * 0.4f,
                bitmapWidth * 1.0f,
                accentColor2
        );

        canvas.drawBitmap(
                bitmap,
                null,
                new android.graphics.Rect(0, 0, getWidth(), getHeight()),
                bitmapPaint
        );

        postInvalidateOnAnimation();
    }

    private void drawBlob(Canvas canvas, float x, float y, float radius, int color) {
        RadialGradient gradient = new RadialGradient(
                x,
                y,
                radius,
                color,
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
        );

        gradientPaint.setShader(gradient);
        gradientPaint.setAlpha(255);
        canvas.drawCircle(x, y, radius, gradientPaint);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (bitmap == null && getWidth() > 0 && getHeight() > 0) {
            onSizeChanged(getWidth(), getHeight(), 0, 0);
        }
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
            bitmapCanvas = null;
        }
    }

    private interface ColorUpdateListener {
        void onUpdate(int value);
    }
}