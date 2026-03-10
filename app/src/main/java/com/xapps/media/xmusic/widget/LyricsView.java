package com.xapps.media.xmusic.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.xapps.media.xmusic.common.PlaybackControlListener;
import com.xapps.media.xmusic.models.LyricLine;
import com.xapps.media.xmusic.models.LyricWord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;




public class LyricsView extends ScrollingView2 {

    private final List<LyricLine> lines = new ArrayList<>();
    private final List<LyricLineCanvasView> lineViews = new ArrayList<>();

    private final List<Integer> currentActiveIndices = new ArrayList<>();
    private final List<Integer> persistedActiveIndices = new ArrayList<>();

    private int[] lineTops = new int[0];
    private int contentHeight;

    private int lastTopActiveIndex = -1;
    private boolean allowAutoScroll = true;
    private boolean isLineCentered;
    private boolean isRecovering = false;

    private static final long AUTO_SCROLL_DELAY_MS = 2000;
    private static final float SCROLL_FRICTION = 0.12f;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private PlaybackControlListener seekListener;

    private int normalLineSpacingPx;
    private int leftPaddingPx, rightPaddingPx;
    
    private float[] lineOffsets = new float[0];
    private ValueAnimator scrollAnimator;
    
    private final List<ValueAnimator> lineAnimators = new ArrayList<>();
    
    public LyricsView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float d = context.getResources().getDisplayMetrics().density;
        normalLineSpacingPx = (int) (48 * d);
        leftPaddingPx = (int) (16 * d);
        rightPaddingPx = (int) (96 * d);
    }

    /* ------------------------Update Runnable---------------- */

    private final Runnable frameTick = new Runnable() {
        @Override
        public void run() {
            boolean needsRedraw = false;
            int currentY = getScrollY();

            int h = getHeight();
            for (int i = 0; i < lineViews.size(); i++) {
                LyricLineCanvasView v = lineViews.get(i);
                v.updateManualScale();
            
                int top = lineTops[i];
                int bottom = top + v.getMeasuredHeight();
            
                if (bottom >= currentY && top <= currentY + h) {
                    if (v.consumeDirty()) {
                        needsRedraw = true;
                    }
                }
            }

            /*if (needsRedraw)*/ invalidate();
            postOnAnimation(this);
        }
    };

    /* ---------------------Public setters------------------ */

    public void setLyrics(List<LyricLine> lyricLines) {
        lines.clear();
        lineViews.clear();
        currentActiveIndices.clear();
        persistedActiveIndices.clear();
        lastTopActiveIndex = -1;

        removeCallbacks(frameTick);
        handler.removeCallbacksAndMessages(null);

        if (lyricLines != null) {
            lines.addAll(lyricLines);
            for (LyricLine l : lines) {
                LyricLineCanvasView v = new LyricLineCanvasView(getContext(), null);
                boolean small = l.isRomaji || l.isBackground;
                v.setTextSizeSp(small ? 18f : 30f);
                v.setLyricLine(l);
                lineViews.add(v);
            }
        }

        lineOffsets = new float[lineViews.size()];

        requestLayout();
        invalidate();
        allowAutoScroll = true;
        refreshBlurStates();
        smoothScrollTo(0, 0, 350);
    }

    public void configureSyncedLyrics(boolean synced, Typeface tf, int gravity, float textSizeSp) {
        for (LyricLineCanvasView v : lineViews) {
            v.setTypeface(tf);
        }
        requestLayout();
        invalidate();
    }

    public void setLyricColor(int color) {
        for (LyricLineCanvasView v : lineViews) {
            v.setColor(color);
        }
        invalidate();
    }

    public void setOnSeekListener(PlaybackControlListener l) {
        seekListener = l;
    }

    public void onProgress(int progressMs) {
        refreshBlurStates();
        updateActiveLines(progressMs);
    }

    /* ------------------- internals --------------------- */
    
    private void refreshBlurStates() {
    boolean shouldBlur = allowAutoScroll && !isRecovering;
    
        for (int i = 0; i < lineViews.size(); i++) {
            LyricLineCanvasView view = lineViews.get(i);
        
            if (!shouldBlur) {
                view.setBlurLevel(-1);
                continue;
            }

            int minDelta = 3;
            if (!persistedActiveIndices.isEmpty()) {
                minDelta = Integer.MAX_VALUE;
                for (int activeIdx : persistedActiveIndices) {
                    int delta = Math.abs(i - activeIdx);
                    if (delta < minDelta) minDelta = delta;
                }
            }

            if (minDelta == 0) {
                view.setBlurLevel(-1);
            } else {
                view.setBlurLevel(Math.min(minDelta - 1, 2));
            }
        }
    }


    private final List<Integer> scratchActive = new ArrayList<>();

private void updateActiveLines(int progressMs) {
    if (lines.isEmpty()) return;

    scratchActive.clear();

    for (int i = 0; i < lines.size(); i++) {
        LyricLine line = lines.get(i);
        if (line.isRomaji) continue;

        long start = line.time;
        long end = getLineEndTime(i);

        if (progressMs >= start && progressMs <= end) {
            scratchActive.add(i);
        }
    }

    if (scratchActive.isEmpty() && !persistedActiveIndices.isEmpty()) {
        scratchActive.addAll(persistedActiveIndices);
    }

    currentActiveIndices.clear();
    currentActiveIndices.addAll(scratchActive);

    persistedActiveIndices.clear();
    persistedActiveIndices.addAll(scratchActive);

    for (int i = 0; i < lineViews.size(); i++) {
        LyricLineCanvasView v = lineViews.get(i);
        
        boolean isActive = lines.get(i).isRomaji 
            ? (i > 0 && currentActiveIndices.contains(i - 1)) 
            : currentActiveIndices.contains(i);

        if (v.isActive() != isActive) {
            v.setCurrent(isActive, i);
        }

        if (isActive) {
            v.setCurrentProgress(progressMs);
        }
    }

    maybeStartFrameLoop();
    maybeAutoScroll();
}

    private void maybeStartFrameLoop() {
        int scrollY = getScrollY();
        int h = getHeight();

        int topsLen = lineTops.length;
        int viewsLen = lineViews.size();

        for (int idx : persistedActiveIndices) {
            if (idx < 0 || idx >= topsLen || idx >= viewsLen) continue;

            int top = lineTops[idx];
            int bottom = top + lineViews.get(idx).getMeasuredHeight();

            if (bottom >= scrollY && top <= scrollY + h) {
                removeCallbacks(frameTick);
                postOnAnimation(frameTick);
                return;
            }
        }
    }
    
    private boolean lineChange;

    private void maybeAutoScroll() {
        if (!allowAutoScroll || persistedActiveIndices.isEmpty()) return;
        int currentActiveTop = Collections.min(persistedActiveIndices);
        if (currentActiveTop != lastTopActiveIndex || !isLineCentered) {
            lastTopActiveIndex = currentActiveTop;
            centerLine(currentActiveTop);
        }
    }


    /* --------------------Children Logic------------------ */

    @Override
    protected void onMeasureForChild(int widthSpec, int heightSpec) {
        int fullWidth = MeasureSpec.getSize(widthSpec);
        int viewHeight = MeasureSpec.getSize(heightSpec);
        if (viewHeight <= 0) {
            viewHeight = getResources().getDisplayMetrics().heightPixels;
        }

        int width = fullWidth - leftPaddingPx - rightPaddingPx;
        int y = viewHeight / 3;

        lineTops = new int[lineViews.size()];

        for (int i = 0; i < lineViews.size(); i++) {
            if (!lines.get(i).isRomaji && i != 0) {
                y += normalLineSpacingPx;
            } else {
                y += normalLineSpacingPx/4;
            }

            LyricLineCanvasView v = lineViews.get(i);
            v.measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            );

            lineTops[i] = y;
            y += v.getMeasuredHeight();
        }

        contentHeight = y + viewHeight / 3;
        setChildMeasuredDimension(fullWidth, contentHeight);
    }

    @Override
    protected void onDrawForChild(@NonNull Canvas canvas) {
        int topVisible = getTopVisibleIndex();
        int bottomVisible = getBottomVisibleIndex();
        if (topVisible < 0 || bottomVisible < 0) return;

        for (int i = topVisible; i <= bottomVisible; i++) {
            LyricLine line = lines.get(i);
            LyricLineCanvasView view = lineViews.get(i);

            float x = (line.vocalType == 2) 
                ? (getWidth() - view.getMeasuredWidth() - leftPaddingPx) 
                : leftPaddingPx;
            float y = lineTops[i] + lineOffsets[i];
            canvas.save();
            canvas.translate(x, y);
            view.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    protected void onLayoutForChild(int l, int t, int r, int b) {}
    
    private final android.view.GestureDetector gestureDetector = new android.view.GestureDetector(getContext(), new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                handleLineClick(e.getX(), e.getY());
                return true;
            }
        }
    );
    
    private void handleLineClick(float x, float y) {
        if (lines.isEmpty() || lineTops.length == 0 || seekListener == null) return;

        int contentY = (int) y + getScrollY();

        int topIndex = getTopVisibleIndex();
        int bottomIndex = getBottomVisibleIndex();

        if (topIndex < 0 || bottomIndex < 0) return;

        int start = Math.max(0, topIndex);
        int end = Math.min(bottomIndex, lineTops.length - 1);
        
        allowAutoScroll = true;

        for (int i = start; i <= end; i++) {
            int top = lineTops[i];
            int bottom = top + lineViews.get(i).getMeasuredHeight();

            if (contentY >= top && contentY <= bottom) {
                LyricLine clicked = lines.get(i);
                if (clicked.isRomaji && i > 0) {
                    seekListener.onSeekRequested(lines.get(i - 1).time);
                } else {
                    seekListener.onSeekRequested(clicked.time);
                }
                return;
            }
        }
    }

    /* -------------------- Touch event ------------------- */

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent e) {
        gestureDetector.onTouchEvent(e);

        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            handler.removeCallbacksAndMessages(null); 
        }

        if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (allowAutoScroll) {
                if (scrollAnimator != null) scrollAnimator.cancel();
                isLineCentered = false;
                allowAutoScroll = false;
                refreshBlurStates(); // Clear blurs during manual movement
            }
            handler.removeCallbacksAndMessages(null);
        } else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            handler.removeCallbacksAndMessages(null); 
            handler.postDelayed(() -> {
                allowAutoScroll = true;
                refreshBlurStates();
                maybeAutoScroll();
            }, AUTO_SCROLL_DELAY_MS);
        }
        return super.onTouchEvent(e);
    }

    @Override
    protected boolean onTouchEventForChild(@NonNull MotionEvent event) {
        return false;
    }

    /* -------------------Scroll Logic --------------------- */

    private void centerLine(int index) {
        if (index < 0 || index >= lineTops.length || index >= lineViews.size()) return;
        int targetY = lineTops[index] - getHeight() / 3;
        int currentY = getScrollY();
        isLineCentered = true;    
        animateScrollTo(targetY, index);
    }

    private void animateScrollTo(int targetY, int activeIndex) {
        if (scrollAnimator != null) scrollAnimator.cancel();
        final int startY = getScrollY();
        final int delta = targetY - startY;
        final int baseIndex = getTopVisibleIndex();

        if (baseIndex < 0) return;

        final float[] startOffsets = new float[lineOffsets.length];
        System.arraycopy(lineOffsets, 0, startOffsets, 0, lineOffsets.length);

        scrollAnimator = ValueAnimator.ofFloat(0f, 1f);
        scrollAnimator.setDuration(1000);
        scrollAnimator.setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f));

        scrollAnimator.addUpdateListener(a -> {
            float p = (float) a.getAnimatedValue();
            int viewportY = (int) (startY + delta * p);
            scrollTo(0, viewportY);

            int centerY = viewportY + (getHeight() / 2);
            float compress = (float) Math.sin(p * Math.PI) * 0.1f;

            for (int i = 0; i < lineViews.size(); i++) {
                LyricLineCanvasView view = lineViews.get(i);
                
                float lineCenter = lineTops[i] + view.getMeasuredHeight() / 2f;
                float distFromCenter = lineCenter - centerY;
                lineOffsets[i] = -distFromCenter * compress;
        
                int minDelta = Integer.MAX_VALUE;
                if (persistedActiveIndices.isEmpty()) {
                    minDelta = 6;
                } else {
                    for (int activeIdx : persistedActiveIndices) {
                        minDelta = Math.min(minDelta, Math.abs(i - activeIdx));
                    }
                }

                if (minDelta == 0) {
                    view.setBlurLevel(-1); 
                } else {
                    view.setBlurLevel(Math.min(minDelta - 1, 4)); 
                }
            }
            invalidate();
        });
        
        scrollAnimator.start();
    }
    
    private int getTopVisibleIndex() {
        int scrollY = getScrollY();

        int low = 0;
        int high = Math.min(lineTops.length, lineViews.size()) - 1;
        if (high < 0) return -1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int bottom = lineTops[mid] + lineViews.get(mid).getMeasuredHeight();

            if (bottom <= scrollY) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        return low <= high + 1 ? low : -1;
    }
    
    private int getBottomVisibleIndex() {
        int viewportBottom = getScrollY() + getHeight();

        int low = 0;
        int high = Math.min(lineTops.length, lineViews.size()) - 1;
        if (high < 0) return -1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
    
            if (lineTops[mid] < viewportBottom) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        return high >= 0 ? high : -1;
    }

    private long getLineEndTime(int index) {
        if (index < 0 || index >= lines.size()) return 0;

        LyricLine line = lines.get(index);

        if (line.isSimpleLRC) {
            if (line.endTime > 0) return line.endTime -250;

            for (int i = index + 1; i < lines.size(); i++) {
                if (!lines.get(i).isRomaji) return lines.get(i).time;
            }
            return line.time + 10000;
        } else {
            long max = 0;
            for (LyricWord w : line.words) {
                max = Math.max(max, w.getEndTime());
            }
            return max;
        }
    }
}