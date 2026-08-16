package com.xapps.media.xmusic.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import androidx.core.content.res.ResourcesCompat;
import com.xapps.media.xmusic.R;
import com.xapps.media.xmusic.models.LyricLine;
import com.xapps.media.xmusic.models.LyricSyllable;
import com.xapps.media.xmusic.models.LyricWord;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class XLyricsLineView extends View {

    public final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sparklePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final AccelerateDecelerateInterpolator interpolator = new AccelerateDecelerateInterpolator();
    private final Random random = new Random();

    protected StaticLayout staticLayout;
    private LyricLine lyricLine;
    private LinearGradient brushShader;
    private final Matrix shaderMatrix = new Matrix();

    Layout.Alignment alignment = Layout.Alignment.ALIGN_NORMAL;

    protected int activeColor = 0xFFFFFFFF;
    protected int pastViewColor = 0x80FFFFFF;
    protected int futureColor = 0x26FFFFFF;

    protected float targetGlobalX = 0f;
    protected float targetColorState = 0f;

    protected float currentGlobalX = 0f;
    protected float colorTransitionState = 0f;
    protected float velocityX = 0f;
    protected long lastFrameTime = 0;
    protected int currentProgressMs = 0;

    protected float targetSimpleState = 0f;
    protected float currentSimpleState = 0f;
    private long customEndTime = -1;

    protected float[] lineStarts;
    private float[] lineWidths;
    protected float[] charXMap;
    protected float totalWidth = 0;

    private final List<VisualCluster> clusters = new ArrayList<>();
    private final List<Sparkle> sparkles = new ArrayList<>();
    private boolean isSparklesEnabled = false;

    private boolean isForcedActive = false;
    private static final float GRADIENT_WIDTH = 200f;
    private static final float ELEVATION_AMOUNT = 6f;

    static class LayoutCache {
        StaticLayout staticLayout;
        Layout.Alignment alignment;
        float[] lineStarts;
        float[] lineWidths;
        float[] charXMap;
        float totalWidth;
        List<VisualCluster> clusters = new ArrayList<>();
        String text;
        int width;
    }

    protected volatile LayoutCache layoutCache;

    protected static class VisualCluster {
        int start;
        int end;
        float x;
        float y;
        float width;
        int lineIdx;
        float currentElevation;
        float liftCenterGlobalX;
        boolean isHeavy;
        int wordStartMs;
        int wordEndMs;
        float normX;
        float intensity;
        boolean disableScale;
    }

    private static class Sparkle {
        float x, y;
        float baseX, baseY;
        float vx, vy;
        float alpha;
        float maxAlpha;
        float size;
        float life;
        float maxLife;
        float phase;
        float amplitude;
        float frequency;
    }

    public XLyricsLineView(Context context) {
        super(context);
        init();
    }

    public XLyricsLineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        Typeface customFont = ResourcesCompat.getFont(getContext(), R.font.gsans_flex_full);
        textPaint.setTypeface(Typeface.create(customFont, Typeface.BOLD));
        textPaint.setFontFeatureSettings("'liga' 0, 'clig' 0");
        setClipToOutline(false);
        setClickable(false);
        setFocusable(false);
    }

    public void setEnableSparkles(boolean enable) {
        this.isSparklesEnabled = enable;
        updateShader();
        invalidate();
    }

    public void setLyricColor(int color) {
        activeColor = color;
        pastViewColor = (color & 0x00FFFFFF) | 0x80000000;
        futureColor = (color & 0x00FFFFFF) | 0x26000000;
        updateShader();
        invalidate();
    }

    public void setLineGravity(Layout.Alignment alignment) {
        this.alignment = alignment;
    }

    public void setFontConfig(String s) {
        if (textPaint.setFontVariationSettings(s)) {
            invalidate();
        }
    }

    public void setTextSize(int dp) {
        textPaint.setTextSize(spToPx(dp));
    }

    public LyricLine getLyricLine() {
        return lyricLine;
    }

    public void setLyricLine(LyricLine line) {
        this.lyricLine = line;
        this.isForcedActive = (line != null && (line.isRomaji));
        if (textPaint != null) textPaint.setShader(null);
        if (line != null) setTextSize((line.isRomaji || line.isBackground) ? (line.isRomaji? 16 : 24) : 32);
    }

    public void setCustomEndTime(long time) {
        this.customEndTime = time;
    }

    public float getExtraPadding() {
        return textPaint.getTextSize() * 0.4f;
    }

    public int getDesiredHeight() {
        return staticLayout != null ? staticLayout.getHeight() + (int)(getExtraPadding() * 2) : 0;
    }

    public int getDesiredHeight(String text, int width) {
        if (width <= 0) return 0;
        StaticLayout temp = StaticLayout.Builder.obtain(text, 0, text.length(), textPaint, width)
                .setAlignment(alignment)
                .setIncludePad(true)
                .setLineSpacing(0f, 1.2f)
                .build();
        return temp.getHeight() + (int)(getExtraPadding() * 2);
    }

    public void resetProgress() {
        this.currentGlobalX = 0f;
        this.targetGlobalX = 0f;
        this.colorTransitionState = 0f;
        this.targetColorState = 0f;
        this.currentSimpleState = 0f;
        this.targetSimpleState = 0f;
        this.velocityX = 0f;
        this.currentProgressMs = 0;
        this.sparkles.clear();
        for (VisualCluster vc : clusters) {
            vc.currentElevation = 0f;
        }
        invalidate();
    }

    public void precomputeLayoutData(String text, int width) {
        if (width <= 0 || lyricLine == null) return;

        LayoutCache cache = new LayoutCache();
        cache.text = text;
        cache.width = width;
        cache.alignment = alignment;
        TextPaint paintCopy = new TextPaint(textPaint);

        cache.staticLayout = StaticLayout.Builder.obtain(lyricLine.line, 0, lyricLine.line.length(), paintCopy, width)
                .setAlignment(alignment)
                .setIncludePad(true)
                .setLineSpacing(0f, 1.2f)
                .build();

        cache.lineStarts = new float[cache.staticLayout.getLineCount()];
        cache.lineWidths = new float[cache.staticLayout.getLineCount()];
        cache.totalWidth = 0;

        for (int i = 0; i < cache.staticLayout.getLineCount(); i++) {
            cache.lineStarts[i] = cache.totalWidth;
            cache.lineWidths[i] = Math.max(1f, cache.staticLayout.getLineWidth(i));
            cache.totalWidth += cache.lineWidths[i];
        }

        cache.charXMap = new float[lyricLine.line.length() + 1];
        for (int i = 0; i < cache.charXMap.length; i++) {
            int lineIdx = cache.staticLayout.getLineForOffset(i);
            float lineLeft = cache.staticLayout.getLineLeft(lineIdx);
            cache.charXMap[i] = cache.lineStarts[lineIdx] + (cache.staticLayout.getPrimaryHorizontal(i) - lineLeft);
        }

        String content = lyricLine.line.toString();
        BreakIterator it = BreakIterator.getCharacterInstance();
        it.setText(content);

        int start = it.first();
        for (int end = it.next(); end != BreakIterator.DONE; start = end, end = it.next()) {
            VisualCluster vc = new VisualCluster();
            vc.start = start;
            vc.end = end;
            vc.lineIdx = cache.staticLayout.getLineForOffset(start);
            int endLineIdx = cache.staticLayout.getLineForOffset(end);

            vc.x = cache.staticLayout.getPrimaryHorizontal(start);
            if (vc.lineIdx == endLineIdx) {
                vc.width = cache.staticLayout.getPrimaryHorizontal(end) - vc.x;
            } else {
                vc.width = cache.staticLayout.getLineWidth(vc.lineIdx) - vc.x;
            }
            vc.y = cache.staticLayout.getLineBaseline(vc.lineIdx);
            vc.currentElevation = 0f;
            vc.liftCenterGlobalX = cache.lineStarts[vc.lineIdx] + (vc.x - cache.staticLayout.getLineLeft(vc.lineIdx)) + (vc.width / 2f);
            vc.isHeavy = false;
            vc.disableScale = false;
            vc.wordStartMs = 0;
            vc.wordEndMs = 0;
            cache.clusters.add(vc);
        }

        if (lyricLine.words != null) {
            for (int w = 0; w < lyricLine.words.size(); w++) {
                LyricWord word = lyricLine.words.get(w);
                if (word.syllables == null || word.syllables.isEmpty()) continue;

                int wordLen = 0;
                int wordStartTime = word.syllables.get(0).startTime;
                int wordEndTime = word.syllables.get(word.syllables.size() - 1).endTime;

                for (int s = 0; s < word.syllables.size(); s++) {
                    wordLen += word.syllables.get(s).text.length();
                }

                if (wordLen > 0) {
                    int wordStartOffset = word.startIndex + word.syllables.get(0).relStart;
                    int wordEndOffset = wordStartOffset + wordLen;

                    float wStartX = getGlobalXForOffsetFromCache(wordStartOffset, cache.charXMap, cache.totalWidth);
                    float wEndX = getGlobalXForOffsetFromCache(wordEndOffset, cache.charXMap, cache.totalWidth);
                    float wordWidth = wEndX - wStartX;
                    float wordCenter = wStartX + (wordWidth / 2f);

                    for (int c = 0; c < cache.clusters.size(); c++) {
                        VisualCluster vc = cache.clusters.get(c);
                        if (vc.start >= wordStartOffset && vc.end <= wordEndOffset) {
                            vc.liftCenterGlobalX = wordCenter;
                            vc.wordStartMs = wordStartTime;
                            vc.wordEndMs = wordEndTime;
                        }
                    }

                    boolean isArabic = false;
                    boolean isCJK = false;

                    String firstSyl = word.syllables.get(0).text;
                    if (firstSyl != null && !firstSyl.isEmpty()) {
                        Character.UnicodeBlock block = Character.UnicodeBlock.of(firstSyl.charAt(0));
                        if (block != null) {
                            String bName = block.toString();
                            if (bName.contains("ARABIC")) {
                                isArabic = true;
                            } else if (bName.contains("CJK") || bName.contains("HIRAGANA") || bName.contains("KATAKANA") || bName.contains("HANGUL")) {
                                isCJK = true;
                            }
                        }
                    }

                    boolean eligible = false;
                    float minMs = 200f;
                    float msRange = 300f;
                    boolean shouldDisableScale = false;

                    if (isCJK) {
                        if (wordLen == 1) eligible = true;
                        minMs = 400f;
                        msRange = 400f;
                    } else if (isArabic) {
                        if (wordLen > 1 && wordLen <= 7) eligible = true;
                        shouldDisableScale = true;
                    } else {
                        if (wordLen > 1 && wordLen <= 7) eligible = true;
                    }

                    if (eligible) {
                        float msPerLetter = (float)(wordEndTime - wordStartTime) / wordLen;

                        if (msPerLetter >= minMs) {
                            float intensityRaw = (msPerLetter - minMs) / msRange;
                            float calculatedIntensity = 0.3f + 0.7f * Math.max(0f, Math.min(1f, intensityRaw));

                            for (int c = 0; c < cache.clusters.size(); c++) {
                                VisualCluster vc = cache.clusters.get(c);
                                if (vc.start >= wordStartOffset && vc.end <= wordEndOffset) {
                                    vc.isHeavy = true;
                                    vc.intensity = calculatedIntensity;
                                    vc.disableScale = shouldDisableScale;

                                    float charCenter = getGlobalXForOffsetFromCache(vc.start, cache.charXMap, cache.totalWidth) + (getGlobalXForOffsetFromCache(vc.end, cache.charXMap, cache.totalWidth) - getGlobalXForOffsetFromCache(vc.start, cache.charXMap, cache.totalWidth)) / 2f;
                                    vc.normX = wordWidth > 0 ? ((charCenter - wStartX) / wordWidth) * 2f - 1f : 0f;
                                }
                            }
                        }
                    }
                }
            }
        }

        this.layoutCache = cache;
    }

    private float getGlobalXForOffsetFromCache(int offset, float[] charXMap, float totalWidth) {
        if (charXMap == null || offset < 0) return 0f;
        if (offset >= charXMap.length) return totalWidth;
        return charXMap[offset];
    }

    public void setText(String text, int width) {
        if (width <= 0 || lyricLine == null) return;

        LayoutCache cache = this.layoutCache;
        if (cache != null && text.equals(cache.text) && width == cache.width && this.alignment == cache.alignment) {
            this.staticLayout = cache.staticLayout;
            this.lineStarts = cache.lineStarts;
            this.lineWidths = cache.lineWidths;
            this.charXMap = cache.charXMap;
            this.totalWidth = cache.totalWidth;

            this.clusters.clear();
            this.clusters.addAll(cache.clusters);

            updateShader();
            return;
        }

        staticLayout = StaticLayout.Builder.obtain(lyricLine.line, 0, lyricLine.line.length(), textPaint, width)
                .setAlignment(alignment)
                .setIncludePad(true)
                .setLineSpacing(0f, 1.2f)
                .build();

        lineStarts = new float[staticLayout.getLineCount()];
        lineWidths = new float[staticLayout.getLineCount()];
        totalWidth = 0;

        for (int i = 0; i < staticLayout.getLineCount(); i++) {
            lineStarts[i] = totalWidth;
            lineWidths[i] = Math.max(1f, staticLayout.getLineWidth(i));
            totalWidth += lineWidths[i];
        }

        charXMap = new float[lyricLine.line.length() + 1];
        for (int i = 0; i < charXMap.length; i++) {
            int lineIdx = staticLayout.getLineForOffset(i);
            float lineLeft = staticLayout.getLineLeft(lineIdx);
            charXMap[i] = lineStarts[lineIdx] + (staticLayout.getPrimaryHorizontal(i) - lineLeft);
        }

        clusters.clear();
        String content = lyricLine.line.toString();
        BreakIterator it = BreakIterator.getCharacterInstance();
        it.setText(content);

        int start = it.first();
        for (int end = it.next(); end != BreakIterator.DONE; start = end, end = it.next()) {
            VisualCluster vc = new VisualCluster();
            vc.start = start;
            vc.end = end;
            vc.lineIdx = staticLayout.getLineForOffset(start);
            int endLineIdx = staticLayout.getLineForOffset(end);

            vc.x = staticLayout.getPrimaryHorizontal(start);
            if (vc.lineIdx == endLineIdx) {
                vc.width = staticLayout.getPrimaryHorizontal(end) - vc.x;
            } else {
                vc.width = staticLayout.getLineWidth(vc.lineIdx) - vc.x;
            }
            vc.y = staticLayout.getLineBaseline(vc.lineIdx);
            vc.currentElevation = 0f;
            vc.liftCenterGlobalX = lineStarts[vc.lineIdx] + (vc.x - staticLayout.getLineLeft(vc.lineIdx)) + (vc.width / 2f);
            vc.isHeavy = false;
            vc.disableScale = false;
            vc.wordStartMs = 0;
            vc.wordEndMs = 0;
            clusters.add(vc);
        }

        if (lyricLine.words != null) {
            for (int w = 0; w < lyricLine.words.size(); w++) {
                LyricWord word = lyricLine.words.get(w);
                if (word.syllables == null || word.syllables.isEmpty()) continue;

                int wordLen = 0;
                int wordStartTime = word.syllables.get(0).startTime;
                int wordEndTime = word.syllables.get(word.syllables.size() - 1).endTime;

                for (int s = 0; s < word.syllables.size(); s++) {
                    wordLen += word.syllables.get(s).text.length();
                }

                if (wordLen > 0) {
                    int wordStartOffset = word.startIndex + word.syllables.get(0).relStart;
                    int wordEndOffset = wordStartOffset + wordLen;

                    float wStartX = getGlobalXForOffset(wordStartOffset);
                    float wEndX = getGlobalXForOffset(wordEndOffset);
                    float wordWidth = wEndX - wStartX;
                    float wordCenter = wStartX + (wordWidth / 2f);

                    for (int c = 0; c < clusters.size(); c++) {
                        VisualCluster vc = clusters.get(c);
                        if (vc.start >= wordStartOffset && vc.end <= wordEndOffset) {
                            vc.liftCenterGlobalX = wordCenter;
                            vc.wordStartMs = wordStartTime;
                            vc.wordEndMs = wordEndTime;
                        }
                    }

                    boolean isArabic = false;
                    boolean isCJK = false;

                    String firstSyl = word.syllables.get(0).text;
                    if (firstSyl != null && !firstSyl.isEmpty()) {
                        Character.UnicodeBlock block = Character.UnicodeBlock.of(firstSyl.charAt(0));
                        if (block != null) {
                            String bName = block.toString();
                            if (bName.contains("ARABIC")) {
                                isArabic = true;
                            } else if (bName.contains("CJK") || bName.contains("HIRAGANA") || bName.contains("KATAKANA") || bName.contains("HANGUL")) {
                                isCJK = true;
                            }
                        }
                    }

                    boolean eligible = false;
                    float minMs = 200f;
                    float msRange = 300f;
                    boolean shouldDisableScale = false;

                    if (isCJK) {
                        if (wordLen == 1) eligible = true;
                        minMs = 400f;
                        msRange = 400f;
                    } else if (isArabic) {
                        if (wordLen > 1 && wordLen <= 7) eligible = true;
                        shouldDisableScale = true;
                    } else {
                        if (wordLen > 1 && wordLen <= 7) eligible = true;
                    }

                    if (eligible) {
                        float msPerLetter = (float)(wordEndTime - wordStartTime) / wordLen;

                        if (msPerLetter >= minMs) {
                            float intensityRaw = (msPerLetter - minMs) / msRange;
                            float calculatedIntensity = 0.3f + 0.7f * Math.max(0f, Math.min(1f, intensityRaw));

                            for (int c = 0; c < clusters.size(); c++) {
                                VisualCluster vc = clusters.get(c);
                                if (vc.start >= wordStartOffset && vc.end <= wordEndOffset) {
                                    vc.isHeavy = true;
                                    vc.intensity = calculatedIntensity;
                                    vc.disableScale = shouldDisableScale;

                                    float charCenter = getGlobalXForOffset(vc.start) + (getGlobalXForOffset(vc.end) - getGlobalXForOffset(vc.start)) / 2f;
                                    vc.normX = wordWidth > 0 ? ((charCenter - wStartX) / wordWidth) * 2f - 1f : 0f;
                                }
                            }
                        }
                    }
                }
            }
        }

        updateShader();
    }

    private void updateShader() {
        brushShader = new LinearGradient(0, 0, GRADIENT_WIDTH, 0,
                new int[]{activeColor, activeColor, futureColor, futureColor},
                new float[]{0f, 0.1f, 0.9f, 1f},
                Shader.TileMode.CLAMP);
    }

    private float getGlobalXForOffset(int offset) {
        if (charXMap == null || offset < 0) return 0f;
        if (offset >= charXMap.length) return totalWidth;
        return charXMap[offset];
    }

    public void updateProgress(int progressMs, boolean snap) {
        if (lyricLine == null || staticLayout == null || lyricLine.words.isEmpty()) return;

        boolean smartSnap = Math.abs(progressMs - this.currentProgressMs) > 1500;
        this.currentProgressMs = progressMs;

        long finalEndTime = lyricLine.words.isEmpty() ? lyricLine.time : lyricLine.words.get(lyricLine.words.size() - 1).getEndTime();
        long effectiveEndTime = (customEndTime != -1) ? customEndTime : finalEndTime;

        if (lyricLine.isSimpleLRC || isForcedActive) {
            if (progressMs < lyricLine.time) {
                this.targetSimpleState = 0f;
            } else if (progressMs > effectiveEndTime) {
                this.targetSimpleState = 2f;
            } else {
                this.targetSimpleState = 1f;
            }

            if (snap || smartSnap || this.currentGlobalX == 0f) {
                this.currentSimpleState = this.targetSimpleState;
                this.currentGlobalX = totalWidth + GRADIENT_WIDTH * 3f;
            }
            this.targetGlobalX = totalWidth + GRADIENT_WIDTH * 3f;
            invalidate();
            return;
        }

        float globalX = 0;
        boolean found = false;
        float lastLineEndGlobal = 0;
        int lastEndTime = lyricLine.time;

        if (progressMs < lastEndTime) {
            globalX = 0;
            found = true;
        }

        float newTargetColorState = (progressMs > effectiveEndTime) ? 1f : 0f;

        if (!found) {
            for (int i = 0; i < lyricLine.words.size(); i++) {
                LyricWord word = lyricLine.words.get(i);
                for (int j = 0; j < word.syllables.size(); j++) {
                    LyricSyllable syl = word.syllables.get(j);
                    int charIdx = word.startIndex + syl.relStart;

                    float sylGlobalStart = getGlobalXForOffset(charIdx);
                    float sylGlobalEnd = getGlobalXForOffset(charIdx + syl.text.length());

                    if (progressMs < syl.startTime) {
                        float gap = syl.startTime - lastEndTime;
                        if (gap > 0) {
                            float ratio = (float) (progressMs - lastEndTime) / gap;
                            globalX = lastLineEndGlobal + (sylGlobalStart - lastLineEndGlobal) * interpolator.getInterpolation(ratio);
                        } else {
                            globalX = lastLineEndGlobal;
                        }
                        found = true;
                        break;
                    } else if (progressMs >= syl.startTime && progressMs <= syl.endTime) {
                        float ratio = (float) (progressMs - syl.startTime) / Math.max(1, syl.endTime - syl.startTime);
                        globalX = sylGlobalStart + ((sylGlobalEnd - sylGlobalStart) * interpolator.getInterpolation(ratio));
                        found = true;
                        break;
                    }
                    lastEndTime = syl.endTime;
                    lastLineEndGlobal = sylGlobalEnd;
                }
                if (found) break;
            }
        }

        if (!found) globalX = totalWidth + (GRADIENT_WIDTH * 2.5f);

        if (snap || smartSnap || this.currentGlobalX == 0f) {
            this.currentGlobalX = globalX;
            this.velocityX = 0f;
            this.colorTransitionState = newTargetColorState;

            for (VisualCluster vc : clusters) {
                float targetElev = 0f;
                if (!lyricLine.isSimpleLRC && !isForcedActive && vc.wordEndMs > vc.wordStartMs) {
                    if (progressMs >= vc.wordEndMs) {
                        targetElev = -ELEVATION_AMOUNT;
                    } else if (progressMs >= vc.wordStartMs) {
                        float p = (float)(progressMs - vc.wordStartMs) / (vc.wordEndMs - vc.wordStartMs);
                        float organicP = p * p * (3f - 2f * p);
                        targetElev = -(ELEVATION_AMOUNT * organicP);
                    }
                }
                vc.currentElevation = targetElev;
            }
        }

        this.targetGlobalX = globalX;
        this.targetColorState = newTargetColorState;
        invalidate();
    }

    private void spawnSparkle(float headX, float topY, float bottomY) {
        if (sparkles.size() > 45) return;
        Sparkle s = new Sparkle();
        s.baseX = headX - (random.nextFloat() * spToPx(4f));
        float h = bottomY - topY;
        s.baseY = topY + (h * 0.125f) + (random.nextFloat() * (h * 0.75f));
        s.vx = (random.nextFloat() - 0.5f) * spToPx(8f);
        s.vy = -(random.nextFloat() * spToPx(10f) + spToPx(4f));
        s.maxLife = 0.4f + random.nextFloat() * 0.6f;
        s.life = s.maxLife;
        s.maxAlpha = 0.6f + random.nextFloat() * 0.4f;
        s.alpha = s.maxAlpha;
        s.size = spToPx(0.8f + random.nextFloat() * 1.4f);
        s.phase = random.nextFloat() * (float) Math.PI * 2f;
        s.amplitude = spToPx(1.5f + random.nextFloat() * 1.5f);
        s.frequency = 6f + random.nextFloat() * 8f;
        sparkles.add(s);
    }

    private int blendColors(int color1, int color2, float ratio) {
        float inverseRatio = 1f - ratio;
        float a = ((color1 >> 24) & 0xFF) * inverseRatio + ((color2 >> 24) & 0xFF) * ratio;
        float r = ((color1 >> 16) & 0xFF) * inverseRatio + ((color2 >> 16) & 0xFF) * ratio;
        float g = ((color1 >> 8) & 0xFF) * inverseRatio + ((color2 >> 8) & 0xFF) * ratio;
        float b = (color1 & 0xFF) * inverseRatio + (color2 & 0xFF) * ratio;
        return ((int) a << 24) | ((int) r << 16) | ((int) g << 8) | (int) b;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (staticLayout == null || clusters.isEmpty() || lyricLine == null) return;

        long now = android.os.SystemClock.uptimeMillis();
        if (lastFrameTime == 0) lastFrameTime = now;
        float dt = (now - lastFrameTime) / 1000f;
        lastFrameTime = now;
        if (dt > 0.05f) dt = 0.016f;

        boolean animating = false;
        boolean isSimple = lyricLine.isSimpleLRC || isForcedActive;

        if (isSimple) {
            currentSimpleState += (targetSimpleState - currentSimpleState) * 15f * dt;
            if (Math.abs(targetSimpleState - currentSimpleState) > 0.005f) animating = true;
        } else {
            colorTransitionState += (targetColorState - colorTransitionState) * 25f * dt;
            if (Math.abs(targetColorState - colorTransitionState) > 0.005f) animating = true;

            float stiffness = 220f;
            float damping = 28f;
            float displacement = currentGlobalX - targetGlobalX;
            float acceleration = (-stiffness * displacement) - (damping * velocityX);
            velocityX += acceleration * dt;
            currentGlobalX += velocityX * dt;

            if (Math.abs(displacement) > 0.1f || Math.abs(velocityX) > 0.5f) animating = true;
        }

        int currentPastColor = blendColors(activeColor, pastViewColor, colorTransitionState);
        float globalAlpha = getAlpha();

        long finalEndTime = lyricLine.words.isEmpty() ? lyricLine.time : lyricLine.words.get(lyricLine.words.size() - 1).getEndTime();
        long effectiveEndTime = (customEndTime != -1) ? customEndTime : finalEndTime;
        float waveRadius = GRADIENT_WIDTH * 0.75f;

        for (int i = 0; i < clusters.size(); i++) {
            VisualCluster vc = clusters.get(i);
            float lineLeft = staticLayout.getLineLeft(vc.lineIdx);
            float lineGlobalStart = lineStarts[vc.lineIdx];
            float lineWidth = lineWidths[vc.lineIdx];
            float lineGlobalEnd = lineGlobalStart + lineWidth;

            float targetElev = 0f;
            if (!isSimple && vc.wordEndMs > vc.wordStartMs) {
                if (currentProgressMs >= vc.wordEndMs) {
                    targetElev = -ELEVATION_AMOUNT;
                } else if (currentProgressMs >= vc.wordStartMs) {
                    float p = (float)(currentProgressMs - vc.wordStartMs) / (vc.wordEndMs - vc.wordStartMs);
                    float organicP = p * p * (3f - 2f * p);
                    targetElev = -(ELEVATION_AMOUNT * organicP);
                }
            }

            vc.currentElevation += (targetElev - vc.currentElevation) * 16f * dt;
            if (Math.abs(targetElev - vc.currentElevation) > 0.1f) animating = true;

            float scale = 1.0f;
            int shadowColor = 0;
            float spacingShift = 0f;

            if (vc.isHeavy && currentProgressMs >= vc.wordStartMs && currentProgressMs <= vc.wordEndMs) {
                animating = true;
                float p = (currentProgressMs - vc.wordStartMs) / (float) Math.max(1, vc.wordEndMs - vc.wordStartMs);

                if (!vc.disableScale) {
                    float baseScale = 1.0f + (0.1f * vc.intensity) * (float)Math.sin(Math.PI * p);
                    float perspective = -(0.08f * vc.intensity) * vc.normX * (float)Math.sin(2 * Math.PI * p);
                    scale = baseScale + perspective;
                    spacingShift = vc.normX * (10f * vc.intensity) * (float)Math.sin(p * Math.PI);
                }

                float distanceToCurrent = currentGlobalX - vc.liftCenterGlobalX;
                float glowAlphaProgress = Math.max(0f, Math.min(1f, (distanceToCurrent + waveRadius) / waveRadius));
                int shadowAlpha = (int) (255f * vc.intensity * glowAlphaProgress * (float) Math.sin(p * Math.PI));
                shadowColor = ((shadowAlpha & 0xFF) << 24) | (activeColor & 0x00FFFFFF);
            }

            float drawX = vc.x + spacingShift;
            float drawY = vc.y + vc.currentElevation + getExtraPadding();

            if (scale != 1.0f) {
                canvas.save();
                canvas.scale(scale, scale, drawX + vc.width / 2f, drawY);
            }

            if (shadowColor != 0) {
                textPaint.setShadowLayer(12f, 0f, 0f, shadowColor);
            } else {
                textPaint.clearShadowLayer();
            }

            if (isSimple) {
                int drawColor;
                if (currentSimpleState < 1f) {
                    drawColor = blendColors(futureColor, activeColor, currentSimpleState);
                } else {
                    drawColor = blendColors(activeColor, pastViewColor, currentSimpleState - 1f);
                }
                textPaint.setShader(null);
                textPaint.setColor(drawColor);
                if (globalAlpha < 1.0f) textPaint.setAlpha((int) (Color.alpha(drawColor) * globalAlpha));
                canvas.drawText(lyricLine.line, vc.start, vc.end, drawX, drawY, textPaint);
            } else {
                if (currentGlobalX >= lineGlobalEnd + GRADIENT_WIDTH) {
                    textPaint.setShader(null);
                    textPaint.setColor(currentPastColor);
                    if (globalAlpha < 1.0f) textPaint.setAlpha((int) (Color.alpha(currentPastColor) * globalAlpha));
                } else if (currentGlobalX <= lineGlobalStart - GRADIENT_WIDTH) {
                    textPaint.setShader(null);
                    textPaint.setColor(futureColor);
                    if (globalAlpha < 1.0f) textPaint.setAlpha((int) (Color.alpha(futureColor) * globalAlpha));
                } else {
                    float localX = currentGlobalX - lineGlobalStart;
                    float progress = Math.min(1f, Math.max(0f, localX / lineWidth));
                    float translate = (lineLeft - GRADIENT_WIDTH) + (lineWidth + GRADIENT_WIDTH) * progress;

                    shaderMatrix.setTranslate(translate, 0);
                    brushShader.setLocalMatrix(shaderMatrix);
                    textPaint.setShader(brushShader);
                    textPaint.setColor(activeColor);
                    if (globalAlpha < 1.0f) textPaint.setAlpha((int) (255 * globalAlpha));
                }
                canvas.drawText(lyricLine.line, vc.start, vc.end, drawX, drawY, textPaint);
            }

            if (scale != 1.0f) {
                canvas.restore();
            }
        }

        textPaint.clearShadowLayer();

        if (isSparklesEnabled && currentProgressMs >= lyricLine.time && currentProgressMs <= effectiveEndTime && !isSimple) {
            int currentLineIdx = 0;
            for (int i = 0; i < lineStarts.length; i++) {
                float start = lineStarts[i];
                float end = start + lineWidths[i];
                if (currentGlobalX >= start && currentGlobalX <= end) {
                    currentLineIdx = i;
                    break;
                } else if (currentGlobalX > end) {
                    currentLineIdx = i;
                }
            }

            float lineLeft = staticLayout.getLineLeft(currentLineIdx);
            float lineWidth = lineWidths[currentLineIdx];
            float localX = currentGlobalX - lineStarts[currentLineIdx];
            float clampedLocalX = Math.max(0f, Math.min(localX, lineWidth));

            float headX = lineLeft + clampedLocalX;

            float lineTop = staticLayout.getLineTop(currentLineIdx);
            float lineBottom = staticLayout.getLineBottom(currentLineIdx);

            if (clampedLocalX > 0 && clampedLocalX < lineWidth) {
                if (random.nextFloat() < 0.7f) {
                    int spawnCount = random.nextInt(2) + 1;
                    for(int k=0; k<spawnCount; k++) {
                        spawnSparkle(headX, lineTop + getExtraPadding(), lineBottom + getExtraPadding());
                    }
                }
            }
        }

        if (isSparklesEnabled && !sparkles.isEmpty()) {
            Iterator<Sparkle> iterator = sparkles.iterator();
            while (iterator.hasNext()) {
                Sparkle s = iterator.next();
                s.life -= dt;
                if (s.life <= 0) {
                    iterator.remove();
                    continue;
                }
                animating = true;

                s.baseX += s.vx * dt;
                s.baseY += s.vy * dt;
                s.phase += s.frequency * dt;

                s.x = s.baseX + (float) Math.cos(s.phase) * s.amplitude;
                s.y = s.baseY + (float) Math.sin(s.phase) * s.amplitude;

                float progress = s.life / s.maxLife;
                s.alpha = s.maxAlpha * progress;
                int sparkleColor = (activeColor & 0x00FFFFFF) | ((int) (255 * s.alpha) << 24);
                sparklePaint.setColor(sparkleColor);
                canvas.drawCircle(s.x, s.y, s.size * progress, sparklePaint);
            }
        }

        if (animating) postInvalidateOnAnimation();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), getDesiredHeight());
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }
}