package com.xapps.media.xmusic.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class XLyricsBidiLineView extends XLyricsLineView {

    private final List<BidiRun> bidiRuns = new ArrayList<>();
    private final Matrix shaderMatrix = new Matrix();

    private LinearGradient ltrShader;
    private LinearGradient rtlShader;
    private int lastActiveColor = -1;
    private int lastFutureColor = -1;

    private final Path selectionPath = new Path();
    private final RectF bounds = new RectF();

    private static class BidiRun {
        int start, end;
        float visualLeft, visualRight, width;
        boolean isRtl;
        float logicalStart, logicalEnd;
        int lineIdx;
    }

    protected static class BidiLayoutCache extends LayoutCache {
        List<BidiRun> bidiRuns = new ArrayList<>();
    }

    public XLyricsBidiLineView(Context context) { super(context); }
    public XLyricsBidiLineView(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override
    public void precomputeLayoutData(String text, int width) {
        super.precomputeLayoutData(text, width);
        LayoutCache baseCache = this.layoutCache;
        if (baseCache == null) return;

        BidiLayoutCache bidiCache = new BidiLayoutCache();
        bidiCache.staticLayout = baseCache.staticLayout;
        bidiCache.alignment = baseCache.alignment;
        bidiCache.lineStarts = baseCache.lineStarts;
        bidiCache.lineWidths = baseCache.lineWidths;
        bidiCache.charXMap = baseCache.charXMap;
        bidiCache.totalWidth = baseCache.totalWidth;
        bidiCache.clusters = baseCache.clusters;
        bidiCache.text = baseCache.text;
        bidiCache.width = baseCache.width;

        String content = getLyricLine().line.toString();
        int len = content.length();
        if (len == 0) {
            this.layoutCache = bidiCache;
            return;
        }

        float[] tempCharXMap = new float[len + 1];
        float currentLogicalX = 0f;
        int start = 0;
        Path tempSelectionPath = new Path();
        RectF tempBounds = new RectF();

        while (start < len) {
            boolean isRtl = bidiCache.staticLayout.isRtlCharAt(start);
            int lineIdx = bidiCache.staticLayout.getLineForOffset(start);

            int end = start + 1;
            while (end < len && bidiCache.staticLayout.isRtlCharAt(end) == isRtl && bidiCache.staticLayout.getLineForOffset(end) == lineIdx) {
                end++;
            }

            BidiRun run = new BidiRun();
            run.start = start;
            run.end = end;
            run.isRtl = isRtl;
            run.lineIdx = lineIdx;

            tempSelectionPath.reset();
            bidiCache.staticLayout.getSelectionPath(run.start, run.end, tempSelectionPath);
            tempSelectionPath.computeBounds(tempBounds, true);

            run.visualLeft = tempBounds.left;
            run.visualRight = tempBounds.right;
            run.width = Math.max(1f, tempBounds.width());

            run.logicalStart = currentLogicalX;
            run.logicalEnd = currentLogicalX + run.width;

            float widthPerChar = run.width / Math.max(1, run.end - run.start);
            for (int c = run.start; c < run.end; c++) {
                tempCharXMap[c] = currentLogicalX + (c - run.start) * widthPerChar;
            }

            currentLogicalX += run.width;
            bidiCache.bidiRuns.add(run);
            start = end;
        }
        tempCharXMap[len] = currentLogicalX;
        bidiCache.charXMap = tempCharXMap;
        bidiCache.totalWidth = currentLogicalX;

        this.layoutCache = bidiCache;
    }

    @Override
    public void setText(String text, int width) {
        LayoutCache baseCache = this.layoutCache;
        if (baseCache instanceof BidiLayoutCache && text.equals(baseCache.text) && width == baseCache.width && this.alignment == baseCache.alignment) {
            super.setText(text, width);

            BidiLayoutCache cache = (BidiLayoutCache) baseCache;
            this.bidiRuns.clear();
            this.bidiRuns.addAll(cache.bidiRuns);
            this.charXMap = cache.charXMap;
            this.totalWidth = cache.totalWidth;
            return;
        }

        super.setText(text, width);
        textPaint.setFontFeatureSettings("'liga' 1, 'clig' 1");
        if (getLyricLine() == null || staticLayout == null) return;

        bidiRuns.clear();
        String content = getLyricLine().line.toString();
        int len = content.length();
        if (len == 0) return;

        textPaint.setTextAlign(Paint.Align.LEFT);

        charXMap = new float[len + 1];
        float currentLogicalX = 0f;
        int start = 0;

        while (start < len) {
            boolean isRtl = staticLayout.isRtlCharAt(start);
            int lineIdx = staticLayout.getLineForOffset(start);

            int end = start + 1;
            while (end < len && staticLayout.isRtlCharAt(end) == isRtl && staticLayout.getLineForOffset(end) == lineIdx) {
                end++;
            }

            BidiRun run = new BidiRun();
            run.start = start;
            run.end = end;
            run.isRtl = isRtl;
            run.lineIdx = lineIdx;

            selectionPath.reset();
            staticLayout.getSelectionPath(run.start, run.end, selectionPath);
            selectionPath.computeBounds(bounds, true);

            run.visualLeft = bounds.left;
            run.visualRight = bounds.right;
            run.width = Math.max(1f, bounds.width());

            run.logicalStart = currentLogicalX;
            run.logicalEnd = currentLogicalX + run.width;

            float widthPerChar = run.width / Math.max(1, run.end - run.start);
            for (int c = run.start; c < run.end; c++) {
                charXMap[c] = currentLogicalX + (c - run.start) * widthPerChar;
            }

            currentLogicalX += run.width;
            bidiRuns.add(run);
            start = end;
        }
        charXMap[len] = currentLogicalX;
        totalWidth = currentLogicalX;
    }

    private void updateShadersIfNeeded() {
        if (ltrShader == null || rtlShader == null || activeColor != lastActiveColor || futureColor != lastFutureColor) {
            lastActiveColor = activeColor;
            lastFutureColor = futureColor;

            ltrShader = new LinearGradient(0, 0, 100f, 0,
                    new int[]{activeColor, activeColor, futureColor, futureColor},
                    new float[]{0f, 0.1f, 0.9f, 1f}, Shader.TileMode.CLAMP);

            rtlShader = new LinearGradient(0, 0, 100f, 0,
                    new int[]{futureColor, futureColor, activeColor, activeColor},
                    new float[]{0f, 0.1f, 0.9f, 1f}, Shader.TileMode.CLAMP);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (staticLayout == null || bidiRuns.isEmpty()) return;

        long now = android.os.SystemClock.uptimeMillis();
        if (lastFrameTime == 0) lastFrameTime = now;
        float dt = (now - lastFrameTime) / 1000f;
        lastFrameTime = now;
        if (dt > 0.05f) dt = 0.016f;

        float displacement = currentGlobalX - targetGlobalX;
        velocityX += ((-220f * displacement) - (28f * velocityX)) * dt;
        currentGlobalX += velocityX * dt;

        colorTransitionState += (targetColorState - colorTransitionState) * 25f * dt;
        int currentPastColor = blendColors(activeColor, pastViewColor, colorTransitionState);

        boolean isFullyPast = currentGlobalX >= totalWidth + 100f;
        boolean isFullyFuture = currentGlobalX <= -100f;

        if (isFullyPast) {
            canvas.save();
            canvas.translate(0, getExtraPadding());
            textPaint.setShader(null);
            textPaint.setColor(currentPastColor);
            staticLayout.draw(canvas);
            canvas.restore();
            if (Math.abs(displacement) > 0.1f || Math.abs(velocityX) > 0.5f) postInvalidateOnAnimation();
            return;
        }

        if (isFullyFuture) {
            canvas.save();
            canvas.translate(0, getExtraPadding());
            textPaint.setShader(null);
            textPaint.setColor(futureColor);
            staticLayout.draw(canvas);
            canvas.restore();
            if (Math.abs(displacement) > 0.1f || Math.abs(velocityX) > 0.5f) postInvalidateOnAnimation();
            return;
        }

        updateShadersIfNeeded();

        for (BidiRun run : bidiRuns) {
            canvas.save();
            canvas.translate(0, getExtraPadding());

            float top = staticLayout.getLineTop(run.lineIdx);
            float bottom = staticLayout.getLineBottom(run.lineIdx);
            canvas.clipRect(run.visualLeft, top, run.visualRight, bottom);

            if (currentGlobalX >= run.logicalEnd) {
                textPaint.setShader(null);
                textPaint.setColor(currentPastColor);
            } else if (currentGlobalX <= run.logicalStart) {
                textPaint.setShader(null);
                textPaint.setColor(futureColor);
            } else {
                float localProgress = (currentGlobalX - run.logicalStart) / run.width;
                float tx;
                LinearGradient activeShader;

                if (run.isRtl) {
                    tx = run.visualRight - (run.width + 100f) * localProgress;
                    activeShader = rtlShader;
                } else {
                    tx = run.visualLeft - 100f + (run.width + 100f) * localProgress;
                    activeShader = ltrShader;
                }

                shaderMatrix.setTranslate(tx, 0);
                activeShader.setLocalMatrix(shaderMatrix);
                textPaint.setShader(activeShader);
                textPaint.setColor(activeColor);
            }

            staticLayout.draw(canvas);
            canvas.restore();
        }

        if (Math.abs(currentGlobalX - targetGlobalX) > 0.1f || Math.abs(velocityX) > 0.5f) {
            postInvalidateOnAnimation();
        }
    }

    private int blendColors(int c1, int c2, float r) {
        float ir = 1f - r;
        return ((int)( ((c1>>24)&0xFF)*ir + ((c2>>24)&0xFF)*r ) << 24) |
                ((int)( ((c1>>16)&0xFF)*ir + ((c2>>16)&0xFF)*r ) << 16) |
                ((int)( ((c1>>8)&0xFF)*ir + ((c2>>8)&0xFF)*r ) << 8) |
                ((int)( (c1&0xFF)*ir + (c2&0xFF)*r ));
    }
}