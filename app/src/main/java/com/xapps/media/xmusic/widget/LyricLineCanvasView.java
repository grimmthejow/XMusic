package com.xapps.media.xmusic.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.text.LineBreaker;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import com.xapps.media.xmusic.data.LiveColors;
import com.xapps.media.xmusic.models.LyricLine;
import com.xapps.media.xmusic.models.LyricWord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LyricLineCanvasView extends View {

    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private CharSequence text;
    private StaticLayout staticLayout;
    
    private ValueAnimator landingAnim;

    private static final float INACTIVE_ALPHA = 0.4f;
    private static final float ACTIVE_ALPHA = 1f;
    private static final float ACTIVE_SCALE = 1.2f;
    private static final float INACTIVE_SCALE = 1.0f;
    
    private static final float MIN_GLOW_INTENSITY = 0.1f;
    private static final float MAX_GLOW_INTENSITY = 1.5f;

    private static final int FADE_DURATION_MS = 250;
    private static final int MIN_WORD_DURATION_MS = 150;
    private static final int MAX_WORD_DURATION_MS = 3000;

    private static final int MIN_SPLIT_THRESHOLD = 20;
    private static final int DEFAULT_SPLIT_CHARS = 20;

    private final Map<Integer, KaraokeSpan> spanMap = new HashMap<>();
    private final Map<Integer, Float> spanPeakGlowMap = new HashMap<>();
    private final Map<Integer, SpanTiming> spanTimingMap = new HashMap<>();
    private final Map<Integer, Long> spanCompleteTimeMap = new HashMap<>();
    
    private long lastSpanEndTime = -1;

    private static final long DROP_DELAY_MS = 0;
    private static final long DROP_DURATION_MS = 300;
    
    private float currentScale = 1.0f;
    private boolean dirty = false;
    private static final float SCALE_INTERPOLATION = 0.1f;

    private LyricLine lyricLine;
    private boolean isUpdating = false;
    private boolean isActiveLine = false;
    private boolean isFadedOut = true;
    private boolean skipNextAnimation = false;
    private boolean isLanding = false;

    private int currentPos = -1;

    private long lastUpdateTime = 0;
    private int lastProgressMs = 0;
    private long lineEndTime = 0;

    private boolean isRtl;

    private int lineColor = LiveColors.onSurfaceContainer;

    private ValueAnimator spanAlphaAnimator;

    public static final int ALIGN_LEFT = 0;
    public static final int ALIGN_RIGHT = 1;
    
    private int currentBlurLevel = -1;
    private static final BlurMaskFilter VERY_WEAK_BLUR = new BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL);
    private static final BlurMaskFilter WEAK_BLUR = new BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL);
    private static final BlurMaskFilter MEDIUM_BLUR = new BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL);
    private static final BlurMaskFilter STRONG_BLUR = new BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL);
    private static final BlurMaskFilter VERY_STRONG_BLUR = new BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL);

    private static class SpanTiming {
        long start;
        long end;
        long nextStart;

        SpanTiming(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    private static class VirtualWord {
        String text;
        long start;
        long end;
        int startIndex;

        VirtualWord(String text, long start, long end, int startIndex) {
            this.text = text;
            this.start = start;
            this.end = end;
            this.startIndex = startIndex;
        }
    }

    private final Runnable updateRunnable =
            new Runnable() {
                @Override
                public void run() {
                    if (false) {
                        long elapsed = System.currentTimeMillis() - lastUpdateTime;
                        updateSpanProgress(lastProgressMs + (int) elapsed);
                        postOnAnimation(this);
                    }
                }
            };

    public LyricLineCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClipToOutline(false);
        textPaint.setColor(lineColor);
        textPaint.setTextSize(spToPx(25));
        textPaint.setTypeface(Typeface.DEFAULT);
        
    }
    
    public boolean isActive() {
        return isActiveLine;
    }

    public boolean hasRunningAnimation() {
        return isLanding || spanAlphaAnimator != null;
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }

    public void setSkipNextAnimation(boolean skip) {
        this.skipNextAnimation = skip;
        new Handler(Looper.getMainLooper())
                .postDelayed(
                        () -> {
                            this.skipNextAnimation = false;
                        },
                        50);
    }

    public void setBlurLevel(int level) {
        if (this.currentBlurLevel == level) return;
        this.currentBlurLevel = level;
        if (level == -1) {
            setLayerType(LAYER_TYPE_HARDWARE, null);
        } else {
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }
        invalidate();
    }

    public void setAlignment(int alignment) {
        isRtl = (alignment == ALIGN_RIGHT);
        requestLayout();
        invalidate();
    }

    public void setColor(int color) {
        lineColor = color;
        textPaint.setColor(lineColor);
        invalidate();
    }

    public void setTypeface(Typeface tf) {
        textPaint.setTypeface(tf != null ? Typeface.create(tf, Typeface.BOLD) : Typeface.DEFAULT);
        invalidate();
    }

    public void setTextSizeSp(float sp) {
        textPaint.setTextSize(spToPx(sp));
        requestLayout();
        invalidate();
    }

    public void setLyricLine(LyricLine line) {
        if (this.lyricLine == line) return;

        removeCallbacks(updateRunnable);
        animate().cancel();
        if (spanAlphaAnimator != null) spanAlphaAnimator.cancel();

        isUpdating = false;
        isActiveLine = false;
        isFadedOut = true;
        spanMap.clear();
        spanPeakGlowMap.clear();
        spanTimingMap.clear();
        spanCompleteTimeMap.clear();
        
        lastSpanEndTime = -1;
        this.lyricLine = line;
        this.lineEndTime = 0;

        if (line.words == null || line.words.isEmpty()) {
            handleNonSyncedLine(line);
            return;
        }

        isRtl = checkLineRtl(line.line.toString());
        SpannableString spannable = new SpannableString(line.line);
        int viewWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();

        for (LyricWord w : line.words) {
            if (w.getEndTime() > lineEndTime) lineEndTime = w.getEndTime();

            String rawWordText = w.getText();
            String[] fragments = rawWordText.split("(?<=\\s)|(?=\\s+)");

            int currentOffset = 0;
            long wStart = w.getStartTime();
            long wEnd = w.getEndTime();
            long wDur = wEnd - wStart;
            int wLen = rawWordText.length()> 1 ? rawWordText.length()-1: 1;

            for (String frag : fragments) {
                if (frag.isEmpty()) continue;

                int fragLen = /*frag.contains(" ")? frag.length() - 1 : */frag.length();
                long fragStart = wStart + (long) (wDur * ((double) currentOffset / wLen));
                long fragEnd = wStart + (long) (wDur * ((double) (currentOffset + fragLen) / wLen));

                List<VirtualWord> scriptParts =
                        splitMixedScriptsInternal(
                                frag, fragStart, fragEnd, w.startIndex + currentOffset);

                for (VirtualWord vw : scriptParts) {
                    float peak = calculatePeakIntensity(vw.end - vw.start);

                    if (viewWidth > 0) {
                        StaticLayout temp =
                                StaticLayout.Builder.obtain(
                                                vw.text, 0, vw.text.length(), textPaint, viewWidth)
                                        .setIncludePad(false)
                                        .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
                                        .build();

                        if (temp.getLineCount() <= 1) {
                            createAndAttachSpan(
                                    spannable,
                                    vw.startIndex,
                                    vw.startIndex + vw.text.length(),
                                    vw.start,
                                    vw.end,
                                    peak);
                        } else {
                            handleMegaWordWrap(spannable, vw, temp, peak);
                        }
                    } else {
                        createAndAttachSpan(
                                spannable,
                                vw.startIndex,
                                vw.startIndex + vw.text.length(),
                                vw.start,
                                vw.end,
                                peak);
                    }
                }
                currentOffset += fragLen;
            }
        }

        this.text = spannable;

        Layout.Alignment align;
        if (isRtl) {
            align =
                    (line.vocalType == 2)
                            ? Layout.Alignment.ALIGN_NORMAL
                            : Layout.Alignment.ALIGN_OPPOSITE;
        } else {
            align =
                    (line.vocalType == 2)
                            ? Layout.Alignment.ALIGN_OPPOSITE
                            : Layout.Alignment.ALIGN_NORMAL;
        }

        staticLayout = buildLayout(viewWidth, align);
        
        setPivotX(align == Layout.Alignment.ALIGN_OPPOSITE ? getWidth() : 0f);
        setPivotY(getHeight() / 2f);

        requestLayout();
        invalidate();
    }

    private void handleMegaWordWrap(
            SpannableString spannable, VirtualWord vw, StaticLayout layout, float peak) {
        int len = vw.text.length();
        long duration = vw.end - vw.start;

        for (int i = 0; i < layout.getLineCount(); i++) {
            int lStart = layout.getLineStart(i);
            int lEnd = layout.getLineEnd(i);

            while (lStart < lEnd && vw.text.charAt(lStart) == ' ') {
                lStart++;
            }

            if (lStart >= lEnd) continue;

            long cStart = vw.start + (long) (duration * ((double) lStart / len));
            long cEnd = vw.start + (long) (duration * ((double) lEnd / len));

            createAndAttachSpan(
                    spannable, vw.startIndex + lStart, vw.startIndex + lEnd, cStart, cEnd, peak);
        }
    }

    private void handleNonSyncedLine(LyricLine line) {
        CharSequence raw = line.line;
        String textStr = raw.toString();
        SpannableString spannable = new SpannableString(textStr);

        int width = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        if (width <= 0) {
            post(() -> setLyricLine(line));
            return;
        }

        Layout.Alignment align;
        boolean lineIsRtl = checkLineRtl(textStr);

        if (lineIsRtl) {
            align =
                    (line.vocalType == 2)
                            ? Layout.Alignment.ALIGN_NORMAL
                            : Layout.Alignment.ALIGN_OPPOSITE;
        } else {
            align =
                    (line.vocalType == 2)
                            ? Layout.Alignment.ALIGN_OPPOSITE
                            : Layout.Alignment.ALIGN_NORMAL;
        }

        StaticLayout layout =
                StaticLayout.Builder.obtain(textStr, 0, textStr.length(), textPaint, width)
                        .setAlignment(align)
                        .setIncludePad(false)
                        .build();

        for (int i = 0; i < layout.getLineCount(); i++) {
            int start = layout.getLineStart(i);
            int end = layout.getLineEnd(i);
            while (start < end && textStr.charAt(start) == ' ') start++;
            while (end > start && textStr.charAt(end - 1) == ' ') end--;
            if (start >= end) continue;

            createAndAttachSpan(spannable, start, end, line.time, line.time, MIN_GLOW_INTENSITY);
        }

        for (KaraokeSpan span : spanMap.values()) {
            span.progress = 1f;
            span.alpha = 0f;
        }

        this.text = spannable;
        this.staticLayout = layout;
        requestLayout();
        invalidate();
    }

    private boolean checkLineRtl(String text) {
        if (text == null || text.isEmpty()) return false;

        int rtl = 0;
        int ltr = 0;

        for (int i = 0; i < text.length(); i++) {
            byte dir = Character.getDirectionality(text.charAt(i));
            if (dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                    || dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
                rtl++;
            } else if (dir == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
                ltr++;
            }
        }
        return rtl > ltr;
    }

    private List<VirtualWord> splitMixedScriptsInternal(
            String text, long startTime, long endTime, int baseStartIndex) {
        List<VirtualWord> result = new ArrayList<>();
        if (text.isEmpty()) return result;

        int len = text.length();
        int lastType = getScriptType(text.charAt(0));
        int start = 0;

        for (int i = 1; i < len; i++) {
            char c = text.charAt(i);
            int currentType = getScriptType(c);
            if (currentType == 0) currentType = lastType;

            if (currentType != 0 && currentType != lastType) {
                String subText = text.substring(start, i);
                long subDuration =
                        (long) ((endTime - startTime) * ((double) subText.length() / len));
                result.add(
                        new VirtualWord(
                                subText,
                                startTime,
                                startTime + subDuration,
                                baseStartIndex + start));
                startTime += subDuration;
                start = i;
                lastType = currentType;
            }
        }

        String finalSub = text.substring(start);
        result.add(new VirtualWord(finalSub, startTime, endTime, baseStartIndex + start));
        return result;
    }

    private void addVirtualWord(List<VirtualWord> list, LyricWord parent, String subText, int relStart) {
        if (subText.isEmpty()) return;
        long totalDuration = parent.getEndTime() - parent.getStartTime();
        int parentLen = parent.getText().length();
        int subLen = subText.length();
        long startOffset = (long) (totalDuration * ((double) relStart / parentLen));
        long duration = (long) (totalDuration * ((double) subLen / parentLen));
        long subStart = parent.getStartTime() + startOffset;
        long subEnd = subStart + duration;
        list.add(new VirtualWord(subText, subStart, subEnd, parent.startIndex + relStart));
    }

    private int getScriptType(char c) {
        byte dir = Character.getDirectionality(c);
        if (dir == Character.DIRECTIONALITY_LEFT_TO_RIGHT) return 1;
        if (dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                || dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) return -1;
        return 0;
    }

    private void createAndAttachSpan(SpannableString spannable, int start, int end, long startTime, long endTime, float peakIntensity) {
        if (start >= spannable.length()) return;
        if (end > spannable.length()) end = spannable.length();

        boolean isSingleChar = (end - start == 1);
        char firstChar = spannable.charAt(start);
    
        boolean isCjk = Character.UnicodeBlock.of(firstChar) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
            || Character.UnicodeBlock.of(firstChar) == Character.UnicodeBlock.HIRAGANA
            || Character.UnicodeBlock.of(firstChar) == Character.UnicodeBlock.KATAKANA
            || Character.UnicodeBlock.of(firstChar) == Character.UnicodeBlock.HANGUL_SYLLABLES;

        boolean shouldAllowEffects = isCjk? isSingleChar : true;
    
        long duration = endTime - startTime;
        boolean bounce = shouldAllowEffects && duration >= 1600;
        float finalPeakGlow = shouldAllowEffects ? peakIntensity : 0f;
    
        KaraokeSpan span = new KaraokeSpan(ACTIVE_ALPHA);
        span.shouldBounce = bounce;

        spanMap.put(start, span);
        spanPeakGlowMap.put(start, finalPeakGlow);
        spanTimingMap.put(start, new SpanTiming(startTime, endTime));
        spannable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }


    private float calculatePeakIntensity(long duration) {
        if (duration <= MIN_WORD_DURATION_MS) return MIN_GLOW_INTENSITY;
        if (duration >= MAX_WORD_DURATION_MS) return MAX_GLOW_INTENSITY;
        float progress =
                (float) (duration - MIN_WORD_DURATION_MS)
                        / (MAX_WORD_DURATION_MS - MIN_WORD_DURATION_MS);
        return MIN_GLOW_INTENSITY + (progress * (MAX_GLOW_INTENSITY - MIN_GLOW_INTENSITY));
    }

    public void setCurrent(boolean isCurrent, int position) {
        if (this.isActiveLine == isCurrent && this.currentPos == position) {
            return;
        }

        this.currentPos = position;
        this.isActiveLine = isCurrent;

        if (!isCurrent) {
            if (isUpdating) {
                isUpdating = false;
                removeCallbacks(updateRunnable);
            }
            animateFadeOut(position);
            return;
        }

        if (landingAnim != null) {
            landingAnim.cancel();
        }

        isFadedOut = false;
        setAlpha(1.0f);

        if (spanAlphaAnimator != null) spanAlphaAnimator.cancel();
        spanAlphaAnimator = ValueAnimator.ofFloat(0f, ACTIVE_ALPHA);
        spanAlphaAnimator.setDuration(skipNextAnimation ? 0 : FADE_DURATION_MS);
        spanAlphaAnimator.addUpdateListener(
                animation -> {
                    float val = (float) animation.getAnimatedValue();
                    for (KaraokeSpan span : spanMap.values()) {
                        span.alpha = val;
                    }
                    invalidate();
                });
        spanAlphaAnimator.start();

        if (lyricLine.words != null && !lyricLine.words.isEmpty()) {
            lastUpdateTime = System.currentTimeMillis();
            postOnAnimation(updateRunnable);
        }

        setPivotX(lyricLine.vocalType == 2 ? getWidth() : 0f);
        setPivotY(getHeight() / 2f);

        animate()
                .scaleX(ACTIVE_SCALE)
                .scaleY(ACTIVE_SCALE)
                .setDuration(skipNextAnimation ? 0 : FADE_DURATION_MS)
                .start();
    }

    private void animateFadeOut(int nextActivePos) {
        if (isFadedOut) return;

        boolean isWordedLine =
                lyricLine != null
                        && !lyricLine.isRomaji
                        && lyricLine.words != null
                        && !lyricLine.words.isEmpty();

        if (isWordedLine && (nextActivePos > currentPos && nextActivePos <= currentPos + 2)) {
            if (spanAlphaAnimator != null) spanAlphaAnimator.cancel();

            animate()
                .alpha(1.0f)
                .scaleX(INACTIVE_SCALE)
                .scaleY(INACTIVE_SCALE)
                .setDuration(FADE_DURATION_MS)
                .start();

            for (KaraokeSpan span : spanMap.values()) {
                span.glowAlpha = 0f;
                span.alpha = ACTIVE_ALPHA;
            }

            startLandingAnimation();
            invalidate();
            return;
        }

        isFadedOut = true;
        if (spanAlphaAnimator != null) spanAlphaAnimator.cancel();

        spanAlphaAnimator = ValueAnimator.ofFloat(ACTIVE_ALPHA, 0.0f);
        spanAlphaAnimator.setDuration(FADE_DURATION_MS);
        spanAlphaAnimator.addUpdateListener(
                animation -> {
                    float val = (float) animation.getAnimatedValue();
                    for (KaraokeSpan span : spanMap.values()) {
                        span.alpha = val;
                        span.glowAlpha = Math.min(span.glowAlpha, val);
                    }
                    invalidate();
                });

        animate()
                .scaleX(INACTIVE_SCALE)
                .scaleY(INACTIVE_SCALE)
                .setDuration(FADE_DURATION_MS)
                .withEndAction(
                        () -> {
                            for (KaraokeSpan span : spanMap.values()) {
                                span.alpha = 0f;
                            }
                            startLandingAnimation();
                            invalidate();
                        })
                .start();

        spanAlphaAnimator.start();
    }

    public void setCurrentProgress(int progressMs) {

        if (isActiveLine && progressMs < lastProgressMs) {
            for (KaraokeSpan span : spanMap.values()) {
                span.wordCompleted = false;
                span.externalWordDrop = 0f;
                span.progress = -1.0f;
            }
            spanCompleteTimeMap.clear();
        }
        if (!isActiveLine) return;
        this.lastProgressMs = progressMs;
        this.lastUpdateTime = System.currentTimeMillis();
        updateSpanProgress(progressMs);
        invalidate();
    }

    private void startLandingAnimation() {
        isLanding = true;
        if (landingAnim != null) landingAnim.cancel();

        final Map<Integer, Float> startOffsets = new HashMap<>();
        for (Map.Entry<Integer, KaraokeSpan> entry : spanMap.entrySet()) {
            startOffsets.put(entry.getKey(), entry.getValue().externalWordDrop);
        }
    
        landingAnim = ValueAnimator.ofFloat(1f, 0f);
        landingAnim.setDuration(DROP_DURATION_MS);
        landingAnim.setInterpolator(new android.view.animation.DecelerateInterpolator());

        landingAnim.addUpdateListener(animation -> {
            float f = (float) animation.getAnimatedValue();
            for (Map.Entry<Integer, KaraokeSpan> entry : spanMap.entrySet()) {
                Float startOffset = startOffsets.get(entry.getKey());
                if (startOffset != null) {
                    entry.getValue().externalWordDrop = startOffset * f;
                }
            }
            invalidate();
        });

        landingAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                for (KaraokeSpan span : spanMap.values()) {
                    span.progress = -1.0f;
                    span.wordCompleted = false;
                    span.externalWordDrop = 0f;
                }
                spanCompleteTimeMap.clear();
                isLanding = false;
                invalidate();
            }
        });
        landingAnim.start();
    }

    private void updateSpanProgress(int progressMs) {
        if (spanMap.isEmpty()) return;

        float maxLift = textPaint.getTextSize() * 0.08f;
        long now = System.currentTimeMillis();
        boolean needsInvalidate = false;

        for (Map.Entry<Integer, KaraokeSpan> entry : spanMap.entrySet()) {

            int startIndex = entry.getKey();
            KaraokeSpan span = entry.getValue();
            SpanTiming timing = spanTimingMap.get(startIndex);

            if (isActiveLine && timing != null) {

                float oldProgress = span.progress;

                long start = timing.start;
                long nextStart = timing.nextStart > 0 ? timing.nextStart : timing.end;
                long duration = Math.max(1, nextStart - start);

                if (progressMs <= start) {

                    span.progress = 0f;
                    span.wordCompleted = false;
                    span.externalWordDrop = 0f;
                    spanCompleteTimeMap.remove(startIndex);

                } else if (progressMs >= nextStart) {

                    span.progress = 1f;

                    if (!span.wordCompleted) {
                        span.wordCompleted = true;
                        spanCompleteTimeMap.put(startIndex, now);
                    }

                } else {

                    float p = (float)(progressMs - start) / duration;
                    span.progress = p;

                    float lift = (float)Math.sin(p * (Math.PI / 2));
                    span.externalWordDrop = -maxLift * lift;

                    float glowCurve = (float)Math.sin(p * Math.PI);
                    Float peak = spanPeakGlowMap.get(startIndex);
                span.glowAlpha = glowCurve * (peak != null ? peak : 0.5f);
                }

                if (oldProgress != span.progress) dirty = true;
            }

            if (span.wordCompleted && !isLanding) {

                Long t0 = spanCompleteTimeMap.get(startIndex);
                if (t0 != null) {

                    long elapsed = now - t0;
                    float t = Math.min(1f, (float)elapsed / DROP_DURATION_MS);
                    float interpolator = (float)(1f - Math.cos(t * Math.PI * 0.5f));
                    span.externalWordDrop = -maxLift + (maxLift * interpolator);

                    if (t >= 1f) {
                        span.externalWordDrop = 0f;
                        spanCompleteTimeMap.remove(startIndex);
                    }

                    needsInvalidate = true;
                }
            }
        }

        if (needsInvalidate) invalidate();
    }

    public void updateManualScale() {
        float target = isActiveLine ? ACTIVE_SCALE : INACTIVE_SCALE;
        if (Math.abs(currentScale - target) > 0.001f) {
            currentScale += (target - currentScale) * SCALE_INTERPOLATION;
            dirty = true;
        } else {
            currentScale = target;
        }
    }

    public boolean consumeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (lyricLine == null || staticLayout == null) return;
    
        canvas.save();
        float pivotX = (lyricLine.vocalType == 2) ? getMeasuredWidth() : 0f;
        float pivotY = getMeasuredHeight() / 2f;
    
        canvas.scale(currentScale, currentScale, pivotX, pivotY);
        canvas.translate(getPaddingLeft(), getPaddingTop());

        if (currentBlurLevel != -1 && Math.abs(currentScale - 1.0f) < 0.01f) {
            if (currentBlurLevel == 0) textPaint.setMaskFilter(VERY_WEAK_BLUR);
            else if (currentBlurLevel == 1) textPaint.setMaskFilter(WEAK_BLUR);
            else if (currentBlurLevel == 2) textPaint.setMaskFilter(MEDIUM_BLUR);
            else if (currentBlurLevel == 3) textPaint.setMaskFilter(STRONG_BLUR);
            else textPaint.setMaskFilter(VERY_STRONG_BLUR);
        
            staticLayout.draw(canvas);
            textPaint.setMaskFilter(null);
        } else {
            staticLayout.draw(canvas);
        }
        canvas.restore();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(updateRunnable);
        if (spanAlphaAnimator != null) spanAlphaAnimator.cancel();
        isUpdating = false;
        setCurrent(false, currentPos);
    }

    private StaticLayout buildLayout(int width, Layout.Alignment a) {
        if (text == null || width <= 0) return null;

        return StaticLayout.Builder.obtain(text, 0, text.length(), textPaint, width)
                .setAlignment(a)
                .setIncludePad(false)
                .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(LineBreaker.HYPHENATION_FREQUENCY_NONE)
                .build();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int contentWidth = width - getPaddingLeft() - getPaddingRight();

        Layout.Alignment a =
                lyricLine.vocalType == 2
                        ? Layout.Alignment.ALIGN_OPPOSITE
                        : Layout.Alignment.ALIGN_NORMAL;

        staticLayout = buildLayout(contentWidth, a);

        int height = 0;
        if (staticLayout != null) {
            height = staticLayout.getHeight();
        }

        height += getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(width, height);
    }
}
