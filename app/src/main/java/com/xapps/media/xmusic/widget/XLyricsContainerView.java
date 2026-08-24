package com.xapps.media.xmusic.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.xapps.media.xmusic.common.PlaybackControlListener;
import com.xapps.media.xmusic.data.DataManager;
import com.xapps.media.xmusic.models.LyricLine;
import com.xapps.media.xmusic.models.LyricItem;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;

public class XLyricsContainerView extends ScrollingView2 {

    private final List<LyricItemDelegate> lineDelegates = new ArrayList<>();
    private PlaybackControlListener listener;

    private int targetIndex = -1;
    private int activeBlurIndex = -1;

    private float scrollVelocityY = 0f;
    private long lastFrameTime = 0;
    private long lastTargetChangeTime = 0;
    private static final long STAGGER_DELAY_MS = 35L;

    private boolean isAutoScrollPaused = false;
    private boolean isPhysicsScrolling = false;
    private boolean isActuallyScrolling = false;
    private boolean isInteractive = true;

    private boolean useStaticScroll = false;
    private boolean userStaticScroll = false;
    private boolean enableSparkles = false;
    private boolean lyricAnticipation = false;
    private boolean enableBlurs = false;

    private android.graphics.BlurMaskFilter[] blurFilters;

    private float touchStartY = 0f;
    private int touchSlop;
    private LyricItemDelegate pressedDelegate;
    private int pressedPart = 0;
    private final Runnable recoveryRunnable = () -> isAutoScrollPaused = false;

    private int currentColor;
    private String currentFontConfig;

    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public XLyricsContainerView(@NonNull Context context) {
        this(context, null);
    }

    public XLyricsContainerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipToOutline(false);
        initBlurFilters();
    }

    private void initBlurFilters() {
        blurFilters = new android.graphics.BlurMaskFilter[5];
        for (int i = 0; i < 5; i++) {
            blurFilters[i] = new android.graphics.BlurMaskFilter((i + 1) * 4f, android.graphics.BlurMaskFilter.Blur.NORMAL);
        }
    }

    public void setEnableBlurs(boolean enable) {
        this.enableBlurs = enable;
        invalidate();
    }

    public void setLyricAnticipation(boolean anticipate) {
        this.lyricAnticipation = anticipate;
    }

    public void setStaticScroll(boolean staticScroll) {
        this.useStaticScroll = staticScroll;
        if (staticScroll) {
            for (LyricItemDelegate delegate : lineDelegates) {
                delegate.staggerY = 0f;
                delegate.staggerVy = 0f;
            }
            invalidate();
        }
    }

    public void setUserStaticScroll(boolean userStaticScroll) {
        this.userStaticScroll = userStaticScroll;
    }

    public void setListener(PlaybackControlListener l) {
        this.listener = l;
    }

    public void setLyricColor(int color) {
        this.currentColor = color;
        for (LyricItemDelegate delegate : lineDelegates) {
            delegate.setLyricColor(color);
        }
        invalidate();
    }

    public void setFontConfig(String config) {
        this.currentFontConfig = config;
        for (LyricItemDelegate delegate : lineDelegates) {
            delegate.setFontConfig(config);
        }
        requestLayout();
        invalidate();
    }

    public void setEnableSparkles(boolean enableSparkles) {
        this.enableSparkles = enableSparkles;
        for (LyricItemDelegate delegate : lineDelegates) {
            delegate.setEnableSparkles(enableSparkles);
        }
        requestLayout();
        invalidate();
    }

    public void setLyrics(List<LyricLine> newLyrics) {
        if (newLyrics == null || newLyrics.isEmpty()) {
            lineDelegates.clear();
            targetIndex = -1;
            activeBlurIndex = -1;
            scrollVelocityY = 0f;
            invalidate();
            return;
        }
        isInteractive = false;
        animate().cancel();
        animate().alpha(0f).setDuration(200L).withEndAction(() -> performLyricsSwap(newLyrics)).start();
    }

    private void performLyricsSwap(List<LyricLine> newLyrics) {
        lineDelegates.clear();
        targetIndex = -1;
        activeBlurIndex = -1;
        scrollVelocityY = 0f;
        invalidate();
        final int finalAvailableWidth = getWidth() != 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        final int parentPaddingLeft = getPaddingLeft();
        final int parentPaddingRight = getPaddingRight();
        bgExecutor.execute(() -> {
            List<LyricItem> tempItems = new ArrayList<>();
            for (int i = 0; i < newLyrics.size(); i++) {
                LyricLine line = newLyrics.get(i);
                if (line.isLinkedBg || line.isRomaji) continue;
                if (line.isWaitingDots) {
                    long nextTime = -1;
                    for (int j = i + 1; j < newLyrics.size(); j++) {
                        LyricLine checkLine = newLyrics.get(j);
                        if (!checkLine.isWaitingDots && !checkLine.isBackground) {
                            nextTime = checkLine.time;
                            break;
                        }
                    }
                    if (nextTime != -1 && (nextTime - line.time) < 5000) continue;
                    if (nextTime != -1) line.endTime = (int) nextTime;
                }
                LyricItem item = new LyricItem(line, i);
                for (int j = i + 1; j < newLyrics.size(); j++) {
                    LyricLine next = newLyrics.get(j);
                    if (next.isWaitingDots || (!next.isLinkedBg && !next.isRomaji)) break;
                    if (next.isLinkedBg && item.linkedBgLine == null) {
                        item.linkedBgLine = next;
                        item.linkedBgIndex = j;
                    }
                    if (next.isRomaji && item.romajiLine == null) {
                        item.romajiLine = next;
                        item.romajiIndex = j;
                    }
                }
                tempItems.add(item);
            }
            List<LyricItemDelegate> tempDelegates = new ArrayList<>();
            int contentWidth = finalAvailableWidth - parentPaddingLeft - parentPaddingRight;
            for (LyricItem item : tempItems) {
                LyricItemDelegate delegate = new LyricItemDelegate(getContext(), item);
                if (currentColor != 0) delegate.setLyricColor(currentColor);
                if (currentFontConfig != null) delegate.setFontConfig(currentFontConfig);
                delegate.setEnableSparkles(enableSparkles);
                int pLeft = (int) (contentWidth * 0.05f);
                int pRight = (int) (contentWidth * 0.2f);
                if (item.mainLine != null && item.mainLine.vocalType != 1 && !item.mainLine.isWaitingDots && !item.mainLine.isBackground) {
                    pLeft = (int) (contentWidth * 0.2f);
                    pRight = (int) (contentWidth * 0.05f);
                }
                int textWidth = contentWidth - pLeft - pRight;
                delegate.precomputeLayouts(textWidth);
                tempDelegates.add(delegate);
            }
            mainHandler.post(() -> {
                lineDelegates.clear();
                lineDelegates.addAll(tempDelegates);
                scrollTo(0, 0);
                requestLayout();
                invalidate();
                animate().alpha(1f).setDuration(250L).withEndAction(() -> isInteractive = true).start();
            });
        });
    }

    private long getEndTimeForLine(int rawIndex) {
        for (LyricItemDelegate delegate : lineDelegates) {
            if (delegate.getItem().mainIndex == rawIndex) {
                LyricLine line = delegate.getItem().mainLine;
                return line.endTime > 0 ? line.endTime : line.time + 5000;
            }
            if (delegate.getItem().romajiIndex == rawIndex) {
                LyricLine line = delegate.getItem().romajiLine;
                return line.endTime > 0 ? line.endTime : line.time + 5000;
            }
            if (delegate.getItem().linkedBgIndex == rawIndex) {
                LyricLine line = delegate.getItem().linkedBgLine;
                return line.endTime > 0 ? line.endTime : line.time + 5000;
            }
        }
        return 0;
    }

    public void updateLyricsProgress(int progressMs, boolean justSeeked) {
        if (lineDelegates.isEmpty()) return;

        int newActiveIndex = -1;
        int nextUpcomingIndex = -1;

        for (int i = 0; i < lineDelegates.size(); i++) {
            LyricItemDelegate delegate = lineDelegates.get(i);
            LyricItem item = delegate.getItem();
            long mainEndTime = getEndTimeForLine(item.mainIndex);

            if (item.linkedBgLine != null) {
                mainEndTime = Math.max(mainEndTime, getEndTimeForLine(item.linkedBgIndex));
            }

            boolean isActive;
            if (item.mainLine.isWaitingDots) {
                isActive = progressMs >= item.mainLine.time && progressMs <= item.mainLine.endTime;
                if (justSeeked && progressMs > item.mainLine.time + 500) isActive = false;
            } else if (item.mainLine.isBackground) {
                isActive = progressMs >= (item.mainLine.time - 1000) && progressMs <= mainEndTime;
            } else {
                isActive = progressMs >= item.mainLine.time && progressMs <= mainEndTime;
            }

            delegate.isActive = isActive;
            delegate.syncInnerProgress(progressMs, justSeeked, mainEndTime);

            if (justSeeked) {
                delegate.snapState(isActive);
            }

            if (isActive && newActiveIndex == -1) {
                newActiveIndex = i;
            }

            if (!isActive && progressMs < item.mainLine.time && nextUpcomingIndex == -1) {
                nextUpcomingIndex = i;
            }
        }

        if (newActiveIndex == -1 && lyricAnticipation && nextUpcomingIndex != -1) {
            newActiveIndex = nextUpcomingIndex;
        }

        if (newActiveIndex != -1 && newActiveIndex != targetIndex) {
            int indexDelta = Math.abs(newActiveIndex - (targetIndex == -1 ? 0 : targetIndex));
            targetIndex = newActiveIndex;

            if (!justSeeked) {
                long delayMs = Math.min(400L, indexDelta * STAGGER_DELAY_MS);
                lastTargetChangeTime = android.os.SystemClock.uptimeMillis() + delayMs;
            } else {
                lastTargetChangeTime = 0;
                scrollVelocityY = 0f;
                activeBlurIndex = targetIndex;
            }
        }
        invalidate();
    }

    @Override
    protected void onMeasureForChild(int widthMeasureSpec, int heightMeasureSpec) {
        int availableWidth = MeasureSpec.getSize(widthMeasureSpec);
        for (LyricItemDelegate delegate : lineDelegates) {
            delegate.measureStaticBounds(availableWidth, getPaddingLeft(), getPaddingRight());
        }
        float density = getResources().getDisplayMetrics().density;
        int hSize = MeasureSpec.getSize(heightMeasureSpec);
        int screenHeight = hSize != 0 ? hSize : (getMeasuredHeight() != 0 ? getMeasuredHeight() : getResources().getDisplayMetrics().heightPixels);
        float currentY = getCurrentY(screenHeight, density);
        setChildMeasuredDimension(availableWidth, (int) (currentY + (screenHeight * 0.75f)));
    }

    private float getCurrentY(int screenHeight, float density) {
        float currentY = screenHeight / 4f;
        for (int i = 0; i < lineDelegates.size(); i++) {
            LyricItemDelegate delegate = lineDelegates.get(i);
            delegate.updateDynamicHeights(density);
            delegate.setTop(currentY);
            float gap = 24 * density;
            if (i < lineDelegates.size() - 1) {
                LyricItemDelegate next = lineDelegates.get(i + 1);
                if (delegate.getItem().mainLine.isWaitingDots || next.getItem().mainLine.isWaitingDots) {
                    gap = 16 * density;
                } else if (delegate.getItem().mainLine.vocalType != next.getItem().mainLine.vocalType) {
                    gap = 40 * density;
                }
            }
            currentY += delegate.getLiveHeight() + gap;
        }
        return currentY;
    }

    @Override
    protected void onLayoutForChild(int l, int t, int r, int b) {}

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        int dy = t - oldt;
        if (dy == 0) return;
        if (!isPhysicsScrolling) {
            isAutoScrollPaused = true;
            removeCallbacks(recoveryRunnable);
            postDelayed(recoveryRunnable, 2000);
        }
        if (useStaticScroll) return;
        if (!isPhysicsScrolling && userStaticScroll) return;
        int anchor = 0;
        for (int i = 0; i < lineDelegates.size(); i++) {
            if (lineDelegates.get(i).getBottom() > t) {
                anchor = i;
                break;
            }
            if (i == lineDelegates.size() - 1) anchor = i;
        }
        for (int i = 0; i < lineDelegates.size(); i++) {
            LyricItemDelegate delegate = lineDelegates.get(i);
            int dist = Math.abs(i - anchor);
            delegate.staggerY += dy;
            delegate.stiffness = Math.max(20f, 120f - (dist * 20f));
        }
    }

    @Override
    protected void onDrawForChild(@NonNull Canvas canvas) {
        if (lineDelegates.isEmpty()) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (lastFrameTime == 0) lastFrameTime = now;
        float dt = (now - lastFrameTime) / 1000f;
        lastFrameTime = now;
        if (dt > 0.016f) dt = 0.016f;
        for (LyricItemDelegate delegate : lineDelegates) delegate.updatePhysics(dt);
        float density = getResources().getDisplayMetrics().density;
        int screenHeight = getHeight();
        getCurrentY(screenHeight, density);

        if (!isAutoScrollPaused && targetIndex >= 0 && targetIndex < lineDelegates.size()) {
            if (now >= lastTargetChangeTime) {
                activeBlurIndex = targetIndex;
                LyricItemDelegate activeDelegate = lineDelegates.get(targetIndex);
                float activeMainCenterY = activeDelegate.getTop() + (4 * density) + (activeDelegate.rawMainHeight / 2f);
                float desiredScrollY = Math.max(0, Math.min(activeMainCenterY - (screenHeight / 4f), getScrollRange()));
                float displacement = getScrollY() - desiredScrollY;
                if (Math.abs(displacement) > 0.5f || Math.abs(scrollVelocityY) > 0.5f) {
                    float stiffness = 120f;
                    float damping = 22f;
                    float acceleration = (-stiffness * displacement) - (damping * scrollVelocityY);
                    scrollVelocityY += acceleration * dt;
                    isPhysicsScrolling = true;
                    scrollTo(getScrollX(), Math.round(getScrollY() + (scrollVelocityY * dt)));
                    isPhysicsScrolling = false;
                } else {
                    scrollVelocityY = 0f;
                }
            }
        } else {
            scrollVelocityY = 0f;
        }

        int scrollY = getScrollY();
        int viewportBottom = scrollY + screenHeight;
        for (int i = 0; i < lineDelegates.size(); i++) {
            LyricItemDelegate delegate = lineDelegates.get(i);
            if (delegate.getBottom() + delegate.staggerY < scrollY || delegate.getTop() + delegate.staggerY > viewportBottom) continue;

            if (enableBlurs && !isAutoScrollPaused && activeBlurIndex >= 0) {
                int distance = Math.abs(i - activeBlurIndex);
                if (distance == 0 || delegate.isActive || delegate.getActiveProgress() > 0.001f) {
                    delegate.setBlurFilter(null);
                } else {
                    int level = Math.min(distance, 5) - 1;
                    delegate.setBlurFilter(blurFilters[level]);
                }
            } else {
                delegate.setBlurFilter(null);
            }

            delegate.draw(canvas, getPaddingLeft(), density, getWidth());
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected boolean onTouchEventForChild(@NonNull MotionEvent event) {
        if (!isInteractive) return false;
        int action = event.getActionMasked();
        float y = event.getY();
        float density = getResources().getDisplayMetrics().density;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                touchStartY = y;
                isActuallyScrolling = false;
                pressedDelegate = null;
                pressedPart = 0;
                float tapY = y;
                for (LyricItemDelegate delegate : lineDelegates) {
                    if (tapY >= delegate.getTop() + delegate.staggerY && tapY <= delegate.getBottom() + delegate.staggerY) {
                        int part = delegate.getPartAt(tapY, density);
                        if (part != 0) {
                            pressedDelegate = delegate;
                            pressedPart = part;
                            delegate.setPressed(part, true);
                        }
                        break;
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!isActuallyScrolling && Math.abs(y - touchStartY) > touchSlop) {
                    isActuallyScrolling = true;
                    isPhysicsScrolling = false;
                    if (pressedDelegate != null) {
                        pressedDelegate.setPressed(0, false);
                        pressedDelegate = null;
                        pressedPart = 0;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (!isActuallyScrolling && pressedDelegate != null) {
                    float releaseY = y;
                    int part = pressedDelegate.getPartAt(releaseY, density);
                    if (part == pressedPart) {
                        if (listener != null) {
                            if (part == 1) {
                                listener.onSeekRequested(pressedDelegate.getItem().mainLine.time);
                            } else if (part == 2 && pressedDelegate.getItem().linkedBgLine != null) {
                                listener.onSeekRequested(pressedDelegate.getItem().linkedBgLine.time);
                            }
                        }
                    }
                }
                if (pressedDelegate != null) {
                    pressedDelegate.setPressed(0, false);
                }
                pressedDelegate = null;
                pressedPart = 0;
                isActuallyScrolling = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                if (pressedDelegate != null) {
                    pressedDelegate.setPressed(0, false);
                }
                pressedDelegate = null;
                pressedPart = 0;
                isActuallyScrolling = false;
                break;
        }
        return true;
    }
}