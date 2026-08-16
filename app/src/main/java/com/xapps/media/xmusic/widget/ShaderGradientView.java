package com.xapps.media.xmusic.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RuntimeShader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

import com.xapps.media.xmusic.R;
import com.xapps.media.xmusic.utils.MaterialColorUtils;
import com.xapps.media.xmusic.utils.XUtils;

import java.io.InputStream;
import java.util.Scanner;

public class ShaderGradientView extends View {

    private static final boolean SUPPORTED = Build.VERSION.SDK_INT >= 32;

    private Paint paint;
    private RuntimeShader shader;

    private boolean visible;

    private float phase = 0f;
    private float speed = 1f;
    private long lastFrameNs = 0;

    private float[] currentColors;
    private float[] startColors;
    private float[] targetColors;

    private float colorT = 1f;
    private float colorSpeed = 0.8f;

    private Choreographer.FrameCallback frameCallback;

    private static final float TOP_Y = 0.32f;
private static final float BOTTOM_Y = 0.78f;

private final float[] uPoints = {
        0.25f, TOP_Y,    0.55f,
        0.75f, TOP_Y,    0.65f,

        0.35f, BOTTOM_Y, 0.60f,
        0.70f, BOTTOM_Y, 0.70f
};

    private final float[] uBound = {0f, 0f, 1f, 1f};

    public ShaderGradientView(Context context) {
        super(context);
        init();
    }

    public ShaderGradientView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ShaderGradientView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (!SUPPORTED) return;
        setupShader();
    }

    private void setupShader() {
      //  shader = new RuntimeShader(loadShader(R.raw.bg_frag));
      //  paint.setShader(shader);

        currentColors = colorsFromInts(
                XUtils.interpolateColor(MaterialColorUtils.colorOnPrimary, MaterialColorUtils.colorSurface, 0.5f),
                XUtils.interpolateColor(MaterialColorUtils.colorSecondaryContainer, MaterialColorUtils.colorSurface, 0.5f),
                XUtils.interpolateColor(MaterialColorUtils.colorPrimaryContainer, MaterialColorUtils.colorSurface, 0.5f),
                XUtils.interpolateColor(MaterialColorUtils.colorTertiaryContainer, MaterialColorUtils.colorSurface, 0.5f)
        );

        shader.setFloatUniform("uPoints", uPoints);
        shader.setFloatUniform("uBound", uBound);
        shader.setFloatUniform("uNoiseScale", 1.6f);
        shader.setFloatUniform("uPointOffset", 0.18f);
        shader.setFloatUniform("uPointRadiusMulti", 1.3f);
        shader.setFloatUniform("uAlphaMulti", 1f);
        shader.setFloatUniform("uColors", currentColors);
        shader.setFloatUniform("uSaturateOffset", 0.3f);
        shader.setFloatUniform("uLightOffset", 0.08f);
        shader.setFloatUniform("uTranslateY", 0f);

        frameCallback = frameTimeNanos -> {

            if (!visible) {
                lastFrameNs = frameTimeNanos;
                Choreographer.getInstance().postFrameCallback(frameCallback);
                return;
            }

            if (shader == null) return;

            if (lastFrameNs != 0) {
                float dt = (frameTimeNanos - lastFrameNs) * 1e-9f;
                dt = Math.min(dt, 1f / 30f);

                phase += dt * speed;
                shader.setFloatUniform("uAnimTime", phase);

                if (colorT < 1f && startColors != null && targetColors != null) {
                    colorT += dt * colorSpeed;
                    float t = Math.min(1f, colorT);
                    float e = 1f - (1f - t) * (1f - t);

                    for (int i = 0; i < currentColors.length; i++) {
                        currentColors[i] = srgb(
                                lin(startColors[i]) +
                                (lin(targetColors[i]) - lin(startColors[i])) * e
                        );
                    }

                    shader.setFloatUniform("uColors", currentColors);

                    if (t >= 1f) {
                        startColors = null;
                        targetColors = null;
                    }
                }
            }

            lastFrameNs = frameTimeNanos;
            invalidate();
            Choreographer.getInstance().postFrameCallback(frameCallback);
        };
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!SUPPORTED) return;
        visible = true;
        lastFrameNs = 0;
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (SUPPORTED) {
            visible = false;
            Choreographer.getInstance().removeFrameCallback(frameCallback);
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (!SUPPORTED) return;
        visible = visibility == VISIBLE;
        lastFrameNs = 0;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (!SUPPORTED) return;
        if (shader != null && w > 0 && h > 0) {
            shader.setFloatUniform("uResolution", w/1.15f, h/1.15f);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!SUPPORTED) return;
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    }

    public void setAnimationSpeed(float s) {
        if (!SUPPORTED) return;
        speed = Math.max(0f, s);
    }

    public void setColors(int c1, int c2, int c3, int c4) {
        if (!SUPPORTED) return;
        startColors = currentColors.clone();
        targetColors = colorsFromInts(c1, c2, c3, c4);
        colorT = 0f;
    }

    private static float lin(float c) {
        return (float) Math.pow(c, 2.2);
    }

    private static float srgb(float c) {
        return (float) Math.pow(c, 1.0 / 2.2);
    }

    private static float[] colorsFromInts(int c1, int c2, int c3, int c4) {
        return new float[]{
                r(c1), g(c1), b(c1), a(c1),
                r(c2), g(c2), b(c2), a(c2),
                r(c3), g(c3), b(c3), a(c3),
                r(c4), g(c4), b(c4), a(c4)
        };
    }

    private static float r(int c) { return ((c >> 16) & 0xFF) / 255f; }
    private static float g(int c) { return ((c >> 8) & 0xFF) / 255f; }
    private static float b(int c) { return (c & 0xFF) / 255f; }
    private static float a(int c) { return ((c >> 24) & 0xFF) / 255f; }

    private String loadShader(int resId) {
        try (InputStream is = getResources().openRawResource(resId);
             Scanner sc = new Scanner(is)) {
            StringBuilder sb = new StringBuilder();
            while (sc.hasNextLine()) sb.append(sc.nextLine()).append('\n');
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}