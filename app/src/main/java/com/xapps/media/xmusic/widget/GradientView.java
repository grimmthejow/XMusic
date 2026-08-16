package com.xapps.media.xmusic.widget;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class GradientView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private long startTime;
    
    private int dominantColor = Color.parseColor("#FF2D55");
    private int accentColor1 = Color.parseColor("#5856D6");
    private int accentColor2 = Color.parseColor("#AF52DE");

    public GradientView(Context context, AttributeSet attrs) {
        super(context, attrs);
        startTime = System.currentTimeMillis();
    }

    public void setColors(int newDominant, int newAccent1, int newAccent2) {
        animateColor(dominantColor, newDominant, val -> dominantColor = val);
        animateColor(accentColor1, newAccent1, val -> accentColor1 = val);
        animateColor(accentColor2, newAccent2, val -> accentColor2 = val);
    }

    private void animateColor(int from, int to, ColorUpdateListener listener) {
        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        animator.setDuration(1000);
        animator.addUpdateListener(animation -> {
            listener.onUpdate((int) animation.getAnimatedValue());
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float time = (System.currentTimeMillis() - startTime) / 2500f;
        int w = getWidth();
        int h = getHeight();

        drawBlob(canvas, 
            w * 0.5f + (float) Math.sin(time * 0.3f) * (w * 0.1f), 
            h * 0.5f + (float) Math.cos(time * 0.3f) * (h * 0.1f), 
            w * 2.5f, dominantColor, 200);

        drawBlob(canvas, 
            w * 0.2f + (float) Math.cos(time * 0.8f) * (w * 0.4f), 
            h * 0.8f + (float) Math.sin(time * 0.6f) * (h * 0.3f), 
            w * 1.1f, accentColor1, 200);

        drawBlob(canvas, 
            w * 0.8f + (float) Math.sin(time * 0.5f) * (w * 0.3f), 
            h * 0.2f + (float) Math.cos(time * 0.9f) * (h * 0.4f), 
            w * 1.0f, accentColor2, 200);

        postInvalidateOnAnimation();
    }

    private void drawBlob(Canvas canvas, float x, float y, float radius, int color, int alpha) {
        RadialGradient gradient = new RadialGradient(x, y, radius, 
                color, Color.TRANSPARENT, Shader.TileMode.CLAMP);
        paint.setShader(gradient);
        paint.setAlpha(alpha);
        canvas.drawCircle(x, y, radius, paint);
    }

    private interface ColorUpdateListener {
        void onUpdate(int value);
    }
}
