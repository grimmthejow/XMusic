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

    private int activeIndex = -1;
    private int targetIndex = -1;
    private int currentProgressMs = 0;

    private float scrollVelocityY = 0f;
    private long lastFrameTime = 0;
    private long lastTargetChangeTime = 0;
    private static final long STAGGER_DELAY_MS = 35L;

    private boolean isAutoScrollPaused = false;
    private boolean isPhysicsScrolling = false;
    private boolean isActuallyScrolling = false;

    private boolean useStaticScroll = false;
    private boolean userStaticScroll = false;
    private boolean enableSparkles = false;
    private float touchStartY = 0f;
    private int touchSlop;
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
        setStaticScroll(DataManager.getStaticScrollState());
        setUserStaticScroll(DataManager.getUserStaticScrollState());
        setFontConfig(DataManager.getFontConfig());
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
        lineDelegates.clear();
        activeIndex = -1;
        targetIndex = -1;
        scrollVelocityY = 0f;
        invalidate();

        int availableWidth = getWidth();
        if (availableWidth == 0) {
            availableWidth = getResources().getDisplayMetrics().widthPixels;
        }
        final int finalAvailableWidth = availableWidth;
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

                    if (nextTime != -1 && (nextTime - line.time) < 5000) {
                        continue;
                    }

                    if (nextTime != -1) {
                        line.endTime = (int) nextTime;
                    }
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

            mainHandler.post(() -> {
                List<LyricItemDelegate> tempDelegates = new ArrayList<>();
                for (LyricItem item : tempItems) {
                    LyricItemDelegate delegate = new LyricItemDelegate(getContext(), item);
                    if (currentColor != 0) delegate.setLyricColor(currentColor);
                    if (currentFontConfig != null) delegate.setFontConfig(currentFontConfig);
                    delegate.setEnableSparkles(enableSparkles);
                    tempDelegates.add(delegate);
                }

                bgExecutor.execute(() -> {
                    int contentWidth = finalAvailableWidth - parentPaddingLeft - parentPaddingRight;
                    for (LyricItemDelegate delegate : tempDelegates) {
                        int pLeft = (int) (contentWidth * 0.05f);
                        int pRight = (int) (contentWidth * 0.2f);
                        if (delegate.getItem().mainLine != null && delegate.getItem().mainLine.vocalType != 1 && !delegate.getItem().mainLine.isWaitingDots && !delegate.getItem().mainLine.isBackground) {
                            pLeft = (int) (contentWidth * 0.2f);
                            pRight = (int) (contentWidth * 0.05f);
                        }
                        int textWidth = contentWidth - pLeft - pRight;
                        delegate.precomputeLayouts(textWidth);
                    }

                    mainHandler.post(() -> {
                        lineDelegates.clear();
                        lineDelegates.addAll(tempDelegates);
                        scrollTo(0, 0);
                        requestLayout();
                        invalidate();
                    });
                });
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
        this.currentProgressMs = progressMs;

        int newActiveIndex = -1;

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
            }
        }

        activeIndex = newActiveIndex;
        invalidate();
    }

    @Override
    protected void onMeasureForChild(int widthMeasureSpec, int heightMeasureSpec) {
        int availableWidth = MeasureSpec.getSize(widthMeasureSpec);

        for (LyricItemDelegate delegate : lineDelegates) {
            delegate.measureStaticBounds(availableWidth, getPaddingLeft(), getPaddingRight());
        }

        float density = getResources().getDisplayMetrics().density;
        int screenHeight = getMeasuredHeight();
        if (screenHeight == 0) {
            screenHeight = getResources().getDisplayMetrics().heightPixels;
        }

        int currentY = getCurrentY(screenHeight, density);

        int totalHeight = currentY + (int) (screenHeight * 0.75f);
        setChildMeasuredDimension(availableWidth, totalHeight);
    }

    private int getCurrentY(int screenHeight, float density) {
        int currentY = screenHeight / 4;

        for (int i = 0; i < lineDelegates.size(); i++) {
            LyricItemDelegate delegate = lineDelegates.get(i);

            delegate.updateDynamicHeights(density);
            delegate.setTop(currentY);

            int gap = (int) (24 * density);
            if (i < lineDelegates.size() - 1) {
                LyricItemDelegate next = lineDelegates.get(i + 1);
                if (delegate.getItem().mainLine.isWaitingDots || next.getItem().mainLine.isWaitingDots) {
                    gap = (int) (16 * density);
                } else if (delegate.getItem().mainLine.vocalType != next.getItem().mainLine.vocalType) {
                    gap = (int) (40 * density);
                }
            }

            currentY += delegate.getLiveHeight() + gap;
        }
        return currentY;
    }

    @Override
    protected void onLayoutForChild(int l, int t, int r, int b) {
    }

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
            if (i == lineDelegates.size() - 1) {
                anchor = i;
            }
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
        if (dt > 0.05f) dt = 0.016f;

        for (LyricItemDelegate delegate : lineDelegates) {
            delegate.updatePhysics(dt);
        }

        float density = getResources().getDisplayMetrics().density;
        int screenHeight = getHeight();
        getCurrentY(screenHeight, density);

        if (!isAutoScrollPaused && targetIndex >= 0 && targetIndex < lineDelegates.size()) {
            if (now >= lastTargetChangeTime) {
                LyricItemDelegate activeDelegate = lineDelegates.get(targetIndex);
                int activeMainCenterY = activeDelegate.getTop() + (int)(4 * density) + (activeDelegate.rawMainHeight / 2);
                float desiredScrollY = Math.max(0, Math.min(activeMainCenterY - (screenHeight / 4f), getScrollRange()));

                float displacement = getScrollY() - desiredScrollY;

                if (Math.abs(displacement) > 0.5f || Math.abs(scrollVelocityY) > 0.5f) {
                    float stiffness = 120f;
                    float damping = 22f;

                    float acceleration = (-stiffness * displacement) - (damping * scrollVelocityY);
                    scrollVelocityY += acceleration * dt;

                    int nextY = Math.round(getScrollY() + (scrollVelocityY * dt));

                    isPhysicsScrolling = true;
                    scrollTo(getScrollX(), nextY);
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
            if (delegate.getBottom() + delegate.staggerY < scrollY || delegate.getTop() + delegate.staggerY > viewportBottom) {
                continue;
            }
            delegate.draw(canvas, getPaddingLeft(), density, getWidth());
        }

        postInvalidateOnAnimation();
    }

    @Override
    protected boolean onTouchEventForChild(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchStartY = event.getY();
                isActuallyScrolling = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!isActuallyScrolling && Math.abs(event.getY() - touchStartY) > touchSlop) {
                    isActuallyScrolling = true;
                    isPhysicsScrolling = false;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (!isActuallyScrolling) {
                    float tapY = event.getY() + getScrollY();
                    for (LyricItemDelegate delegate : lineDelegates) {
                        if (tapY >= delegate.getTop() + delegate.staggerY && tapY <= delegate.getBottom() + delegate.staggerY) {
                            if (listener != null && !delegate.getItem().mainLine.isWaitingDots) {
                                listener.onSeekRequested(delegate.getItem().mainLine.time);
                            }
                            break;
                        }
                    }
                }
                isActuallyScrolling = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                isActuallyScrolling = false;
                break;
        }
        return true;
    }
}