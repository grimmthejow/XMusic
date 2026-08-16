package com.xapps.media.xmusic.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.text.Layout;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import com.xapps.media.xmusic.common.PlaybackControlListener;
import com.xapps.media.xmusic.data.DataManager;
import com.xapps.media.xmusic.models.LyricLine;
import com.xapps.media.xmusic.utils.ColorPaletteUtils;
import com.xapps.media.xmusic.utils.MaterialColorUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class XLyricsView extends RecyclerView {

    private static final int TYPE_LTR = 0;
    private static final int TYPE_BIDI = 1;
    
    private static final float BLUR_FACTOR = 4f;
    private static final int SEEK_THRESHOLD = 1500;
    private static final int RECOVERY_DELAY_MS = 2000;

    private final List<LyricItem> lyricItems = new ArrayList<>();
    private final List<Integer> activeIndexes = new ArrayList<>();
    private final Set<Integer> suppressedIndexes = new HashSet<>();
    private final PathInterpolator yosEasing = new PathInterpolator(0.75f, 0f, 0.25f, 1f);

    private PlaybackControlListener listener;
    private int activeIndex = -1;
    private int currentProgressMs = 0;
    private int currentColor, rippleColor;

    private boolean isAutoScrollPaused = false;
    private boolean isBlurEnabled = true;
    private float touchStartY;
    private boolean isActuallyScrolling = false;
    private int touchSlop;

    private final Runnable recoveryRunnable = () -> {
        isAutoScrollPaused = false;
        isBlurEnabled = true;
        updateActiveStates();
        if (activeIndex != -1) {
            int finalTarget = activeIndex;
            if (finalTarget < lyricItems.size() && lyricItems.get(finalTarget).mainLine.isWaitingDots && finalTarget + 1 < lyricItems.size()) {
                finalTarget++;
            }
            setActiveLine(finalTarget);
        }
    };

    public XLyricsView(@NonNull Context context) {
        super(context);
        init();
    }

    public XLyricsView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void setListener(PlaybackControlListener l) {
        listener = l;
    }

    public void setLyricColor(int color, int color2) {
        currentColor = color;
        rippleColor = color2;
        if (getAdapter() != null) getAdapter().notifyDataSetChanged();
    }

    private void init() {
        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        setLayoutManager(new LinearLayoutManager(getContext()));
        setHasFixedSize(false);
        setItemAnimator(null);
        setClipChildren(false);
        setClipToPadding(false);
        addItemDecoration(new LyricPaddingDecoration());

        addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    for (int i = 0; i < getChildCount(); i++) {
                        View child = getChildAt(i);
                        LyricViewHolder holder = (LyricViewHolder) getChildViewHolder(child);
                        if (holder != null) {
                            holder.springY.animateToFinalPosition(0f);
                        }
                    }
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy == 0 || isAutoScrollPaused) return;

                LinearLayoutManager llm = (LinearLayoutManager) getLayoutManager();
                if (llm == null) return;

                int firstVis = llm.findFirstVisibleItemPosition();

                for (int i = 0; i < getChildCount(); i++) {
                    View child = getChildAt(i);
                    LyricViewHolder holder = (LyricViewHolder) getChildViewHolder(child);
                    int pos = getChildAdapterPosition(child);

                    if (pos == NO_POSITION || holder == null) continue;

                    LinearLayout container = (LinearLayout) holder.wrapper.getChildAt(0);
                    int dist = Math.abs(pos - firstVis);

                    float waveBoost = (dy > 0 ? 1 : -1) * (0.1f * dist);
                    container.setTranslationY(container.getTranslationY() + dy + waveBoost);

                    float stiffness = Math.max(15f, 200f - (dist * 20f));
                    holder.springY.getSpring().setStiffness(stiffness);

                    holder.springY.animateToFinalPosition(0f);
                }
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchStartY = ev.getY();
                isActuallyScrolling = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!isActuallyScrolling && Math.abs(ev.getY() - touchStartY) > touchSlop) {
                    isActuallyScrolling = true;
                }
                if (isActuallyScrolling) {
                    isAutoScrollPaused = true;
                    if (isBlurEnabled) {
                        isBlurEnabled = false;
                        updateActiveStates();
                    }
                    removeCallbacks(recoveryRunnable);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isActuallyScrolling) {
                    removeCallbacks(recoveryRunnable);
                    postDelayed(recoveryRunnable, RECOVERY_DELAY_MS);
                }
                isActuallyScrolling = false;
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    public void setLyrics(List<LyricLine> newLyrics) {
        this.lyricItems.clear();
        this.suppressedIndexes.clear();
        for (int i = 0; i < newLyrics.size(); i++) {
            LyricLine line = newLyrics.get(i);
            
            if (line.isLinkedBg || line.isRomaji) continue;

            LyricItem item = new LyricItem(line, i);
            
            for (int j = i + 1; j < newLyrics.size(); j++) {
                LyricLine next = newLyrics.get(j);
                if (next.isWaitingDots || (!next.isLinkedBg && !next.isRomaji)) {
                    break;
                }
                if (next.isLinkedBg && item.linkedBgLine == null) {
                    item.linkedBgLine = next;
                    item.linkedBgIndex = j;
                }
                if (next.isRomaji && item.romajiLine == null) {
                    item.romajiLine = next;
                    item.romajiIndex = j;
                }
            }
            
            lyricItems.add(item);
        }
        if (getAdapter() == null) {
            setAdapter(new LyricsAdapter());
        } else {
            getAdapter().notifyDataSetChanged();
        }
        scrollToPosition(0);
    }

    public void onProgress(int progressMs) {
        int diff = Math.abs(progressMs - this.currentProgressMs);
        boolean justSeeked = diff > SEEK_THRESHOLD;
        this.currentProgressMs = progressMs;

        if (lyricItems.isEmpty()) return;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof FrameLayout) {
                LinearLayout container = (LinearLayout) ((FrameLayout) child).getChildAt(0);
                for (int j = 0; j < container.getChildCount(); j++) {
                    View innerChild = container.getChildAt(j);
                    if (innerChild instanceof XDynamicContainer) {
                        XDynamicContainer dyn = (XDynamicContainer) innerChild;
                        if (dyn.getChildCount() > 0) {
                            View dynChild = dyn.getChildAt(0);
                            if (dynChild instanceof XLyricsLineView) {
                                ((XLyricsLineView) dynChild).updateProgress(progressMs, false);
                            } else if (dynChild instanceof XWaitingDotsView) {
                                ((XWaitingDotsView) dynChild).updateProgress(progressMs);
                            }
                        }
                    } else if (innerChild instanceof XLyricsLineView) {
                        ((XLyricsLineView) innerChild).updateProgress(progressMs, false);
                    }
                }
            }
        }

        int topActiveIdx = -1;
        int nextMainIdx = -1;
        List<Integer> newActiveIndexes = new ArrayList<>();

        for (int i = 0; i < lyricItems.size(); i++) {
            LyricItem item = lyricItems.get(i);
            boolean isActive;
            long mainEndTime = getEndTimeForLine(item.mainIndex);
            
            if (item.linkedBgLine != null) {
                mainEndTime = Math.max(mainEndTime, getEndTimeForLine(item.linkedBgIndex));
            }

            if (item.mainLine.isWaitingDots) {
                isActive = progressMs >= item.mainLine.time && progressMs <= item.mainLine.endTime;
            } else if (item.mainLine.isBackground) {
                isActive = progressMs >= (item.mainLine.time - 1000) && progressMs <= mainEndTime;
            } else {
                isActive = progressMs >= item.mainLine.time && progressMs <= mainEndTime;
            }

            if (item.mainLine.isWaitingDots) {
                if (justSeeked) {
                    if (progressMs > item.mainLine.time + 500 && isActive) {
                        suppressedIndexes.add(i);
                    } else if (progressMs <= item.mainLine.time + 500) {
                        suppressedIndexes.remove(i);
                    }
                }
                
                if (progressMs > getEndTimeForLine(item.mainIndex)) {
                    suppressedIndexes.remove(i);
                }

                if (suppressedIndexes.contains(i)) {
                    isActive = false;
                }
            }

            if (isActive) {
                newActiveIndexes.add(i);
                if (!item.mainLine.isWaitingDots) topActiveIdx = i; 
            }
            if (!item.mainLine.isBackground && !item.mainLine.isWaitingDots && progressMs < item.mainLine.time && nextMainIdx == -1) {
                nextMainIdx = i;
            }
        }
        
        if (topActiveIdx == -1 && !newActiveIndexes.isEmpty()) {
            topActiveIdx = newActiveIndexes.get(0);
        }

        boolean activeStatesChanged = !activeIndexes.equals(newActiveIndexes);
        if (activeStatesChanged) {
            activeIndexes.clear();
            activeIndexes.addAll(newActiveIndexes);
            updateActiveStates();
        }

        int targetFocus = (topActiveIdx != -1) ? topActiveIdx : (nextMainIdx != -1 ? nextMainIdx : 0);
        
        if (targetFocus != activeIndex) {
            int oldIndex = activeIndex;
            activeIndex = targetFocus;

            int scrollTarget = targetFocus;
            if (targetFocus != -1 && targetFocus < lyricItems.size() && lyricItems.get(targetFocus).mainLine.isWaitingDots) {
                if (targetFocus + 1 < lyricItems.size()) {
                    scrollTarget = targetFocus + 1;
                }
            }

            boolean wasTransient = oldIndex != -1 && oldIndex < lyricItems.size() && 
                (lyricItems.get(oldIndex).mainLine.isWaitingDots || lyricItems.get(oldIndex).mainLine.isBackground);
            boolean isNextAfterTransient = wasTransient && targetFocus == oldIndex + 1;

            if (!isNextAfterTransient && !isAutoScrollPaused) {
                setActiveLine(scrollTarget);
            }
        } else if (activeStatesChanged && !isAutoScrollPaused && !justSeeked) {
            int finalTarget = activeIndex;
            if (finalTarget != -1 && finalTarget < lyricItems.size() && lyricItems.get(finalTarget).mainLine.isWaitingDots) {
                if (finalTarget + 1 < lyricItems.size()) {
                    finalTarget = finalTarget + 1;
                }
            }
            
            boolean isTransient = finalTarget != -1 && finalTarget < lyricItems.size() &&
                (finalTarget == -1 ? false : (lyricItems.get(finalTarget).mainLine.isWaitingDots || lyricItems.get(finalTarget).mainLine.isBackground));
            
            if (!isTransient) {
                setActiveLine(finalTarget);
            }
        }
    }

    public void updateActiveStates() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            int pos = getChildAdapterPosition(child);
            if (pos == NO_POSITION) continue;

            boolean isActive = activeIndexes.contains(pos);
            LinearLayout container = (LinearLayout) ((FrameLayout) child).getChildAt(0);
            LyricItem item = lyricItems.get(pos);

            int minDistance = Integer.MAX_VALUE;
            if (activeIndexes.isEmpty()) {
                minDistance = Math.abs(pos - activeIndex);
            } else {
                for (int activeIdx : activeIndexes) {
                    minDistance = Math.min(minDistance, Math.abs(pos - activeIdx));
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!isBlurEnabled || !DataManager.isBlurOn()) {
                    container.setRenderEffect(null);
                } else {
                    float blurRadius = minDistance * BLUR_FACTOR;
                    if (blurRadius > 0.1f) {
                        container.setRenderEffect(RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP));
                    } else {
                        container.setRenderEffect(null);
                    }
                }
            }

            if (!item.mainLine.isWaitingDots) {
                long scaleDelay = isActive ? 0 : 45;
                container.animate()
                    .scaleX(isActive ? 1.01f : 1.0f)
                    .scaleY(isActive ? 1.01f : 1.0f)
                    .setStartDelay(scaleDelay)
                    .setDuration(isActive ? 350 : 300)
                    .setInterpolator(yosEasing)
                    .start();
            }

            for (int j = 0; j < container.getChildCount(); j++) {
                View innerChild = container.getChildAt(j);
                if (innerChild instanceof XDynamicContainer) {
                    ((XDynamicContainer) innerChild).toggle(isActive, true);
                    if (((ViewGroup) innerChild).getChildCount() > 0) {
                        View dynChild = ((ViewGroup) innerChild).getChildAt(0);
                        if (dynChild instanceof XLyricsLineView || dynChild instanceof XLyricsRomajiLineView) {
                            float targetAlpha = 0.35f;
                            if (dynChild instanceof XLyricsLineView) {
                                targetAlpha = 0.0f;
                            }
                            dynChild.animate()
                                    .alpha(isActive ? 0.65f : targetAlpha)
                                    .setDuration(300)
                                    .start();
                        }
                    }
                } else if (innerChild instanceof XLyricsLineView) {
                    innerChild.animate()
                        .alpha(isActive ? 1.0f : 0.35f)
                        .setDuration(300)
                        .start();
                }
            }
        }
    }

    public void setActiveLine(int index) {
        LinearSmoothScroller scroller = new LinearSmoothScroller(getContext()) {
            @Override
            protected int getVerticalSnapPreference() {
                return SNAP_TO_START;
            }
            @Override
            public int calculateDyToMakeVisible(View view, int snapPreference) {
                int dy = super.calculateDyToMakeVisible(view, SNAP_TO_START);
                RecyclerView.LayoutManager layoutManager = getLayoutManager();
                if (layoutManager != null) {
                    int rvTop = layoutManager.getPaddingTop();
                    int rvHeight = layoutManager.getHeight() - rvTop - layoutManager.getPaddingBottom();
                    dy += (rvHeight / 4);
                }
                return dy;
            }
            @Override
            protected int calculateTimeForScrolling(int dx) {
                return Math.min(super.calculateTimeForScrolling(dx), 400);
            }
            @Override
            protected float calculateSpeedPerPixel(DisplayMetrics dm) {
                return 150f / dm.densityDpi;
            }
        };
        scroller.setTargetPosition(activeIndexes.isEmpty()? index : activeIndexes.get(0));
        if (getLayoutManager() != null) getLayoutManager().startSmoothScroll(scroller);
    }

    private long getEndTimeForLine(int index) {
        if (index == -1) return 0;
        LyricLine current = getLyricLineByIndex(index);
        if (current == null) return 0;
        return current.endTime > 0 ? current.endTime : current.time + 5000;
    }

    private LyricLine getLyricLineByIndex(int index) {
        for (LyricItem item : lyricItems) {
            if (item.mainIndex == index) return item.mainLine;
            if (item.romajiIndex == index) return item.romajiLine;
            if (item.linkedBgIndex == index) return item.linkedBgLine;
        }
        return null;
    }

    private RippleDrawable createRipple() {
        ShapeDrawable mask = new ShapeDrawable(new RoundRectShape(new float[]{0,0,0,0,0,0,0,0}, null, null));
        mask.getPaint().setColor(0xFFFFFFFF);
        return new RippleDrawable(ColorStateList.valueOf(0x66909090), null, mask);
    }

    private class LyricsAdapter extends RecyclerView.Adapter<LyricViewHolder> {
        
        @NonNull
        @Override
        public LyricViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context ctx = parent.getContext();
            
            FrameLayout wrapper = new FrameLayout(ctx);
            wrapper.setClipChildren(false);
            wrapper.setClipToPadding(false);
            wrapper.setLayoutParams(new ViewGroup.LayoutParams(-1, -2));
            
            LinearLayout container = new LinearLayout(ctx);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setClipChildren(false);
            container.setClipToPadding(false);
            container.setLayoutParams(new FrameLayout.LayoutParams(-1, -2));
            
            XDynamicContainer dotsWrapper = new XDynamicContainer(ctx);
            dotsWrapper.setClipChildren(false);
            dotsWrapper.setClipToPadding(false);
            dotsWrapper.setVisibility(View.GONE);
            
            XWaitingDotsView dotsView = new XWaitingDotsView(ctx);
            FrameLayout.LayoutParams dotsLp = new FrameLayout.LayoutParams(-2, -2);
            dotsLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            dotsView.setLayoutParams(dotsLp);
            dotsWrapper.addView(dotsView);
            
            XLyricsLineView mainLineView = (viewType == TYPE_BIDI) 
                    ? new XLyricsBidiLineView(ctx) 
                    : new XLyricsLineView(ctx);
            LinearLayout.LayoutParams mainLp = new LinearLayout.LayoutParams(-2, -2);
            mainLineView.setLayoutParams(mainLp);
            mainLineView.setVisibility(View.GONE);

            XDynamicContainer bgWrapper = new XDynamicContainer(ctx);
            bgWrapper.setClipChildren(false);
            bgWrapper.setClipToPadding(false);
            bgWrapper.setVisibility(View.GONE);

            XLyricsLineView bgLineView = (viewType == TYPE_BIDI) 
                    ? new XLyricsBidiLineView(ctx) 
                    : new XLyricsLineView(ctx);
            FrameLayout.LayoutParams bgLp = new FrameLayout.LayoutParams(-2, -2);
            bgLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            bgLineView.setLayoutParams(bgLp);
            bgWrapper.addView(bgLineView);

            XDynamicContainer romajiWrapper = new XDynamicContainer(ctx);
            romajiWrapper.setTranslationY(-26f * ctx.getResources().getDisplayMetrics().density);
            romajiWrapper.setTranslationZ(-1f);
            romajiWrapper.setVisibility(View.GONE);

            XLyricsRomajiLineView romajiView = new XLyricsRomajiLineView(ctx);
            FrameLayout.LayoutParams romajiLp = new FrameLayout.LayoutParams(-1, -2);
            romajiLp.gravity = Gravity.BOTTOM;
            romajiView.setLayoutParams(romajiLp);
            romajiWrapper.addView(romajiView);

            container.addView(dotsWrapper);
            container.addView(mainLineView);
            container.addView(bgWrapper);
            container.addView(romajiWrapper);
            
            wrapper.addView(container);
            return new LyricViewHolder(wrapper, container, dotsWrapper, dotsView, bgWrapper, bgLineView, mainLineView, romajiWrapper, romajiView);
        }

        @Override
        public void onBindViewHolder(@NonNull LyricViewHolder holder, int position) {
            LyricItem item = lyricItems.get(position);
            int leftPad = (int) (getWidth() * 0.05f);
            int rightPad = (int) (getWidth() * 0.2f);
            int topBotPad = (int) (4 * getResources().getDisplayMetrics().density);

            holder.springY.cancel();
            holder.container.setTranslationY(0f);
            holder.springY.getSpring().setStiffness(200f);

            boolean isActive = activeIndexes.contains(position);
            holder.container.setScaleX(isActive ? 1.01f : 1.0f);
            holder.container.setScaleY(isActive ? 1.01f : 1.0f);

            int minDistance = Integer.MAX_VALUE;
            if (activeIndexes.isEmpty()) {
                minDistance = Math.abs(position - activeIndex);
            } else {
                for (int activeIdx : activeIndexes) {
                    minDistance = Math.min(minDistance, Math.abs(position - activeIdx));
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!isBlurEnabled || !DataManager.isBlurOn()) {
                    holder.container.setRenderEffect(null);
                } else {
                    float blurRadius = minDistance * BLUR_FACTOR;
                    if (blurRadius > 0.1f) {
                        holder.container.setRenderEffect(RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP));
                    } else {
                        holder.container.setRenderEffect(null);
                    }
                }
            }

            long parentEndTime = getEndTimeForLine(item.mainIndex);
            if (item.linkedBgLine != null) {
                parentEndTime = Math.max(parentEndTime, getEndTimeForLine(item.linkedBgIndex));
            }

            if (item.mainLine.isWaitingDots) {
                holder.wrapper.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                holder.wrapper.setVisibility(View.VISIBLE);
                holder.container.setBackground(null);
                holder.container.setPadding(leftPad, 0, 0, 0);
                
                holder.bgWrapper.setVisibility(View.GONE);
                holder.mainLineView.setVisibility(View.GONE);
                holder.romajiWrapper.setVisibility(View.GONE);
                holder.dotsWrapper.setVisibility(View.VISIBLE);

                holder.dotsView.setPrimaryColor(currentColor);
                holder.dotsView.setTimes(item.mainLine.time, item.mainLine.endTime);
                holder.dotsView.updateProgress(currentProgressMs);
                holder.dotsWrapper.forceState(isActive);
                
            } else {
                holder.wrapper.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                holder.wrapper.setVisibility(View.VISIBLE);
                holder.dotsWrapper.setVisibility(View.GONE);

                if (item.mainLine.isBackground) {
                    holder.container.setPadding(leftPad, 0, rightPad, 0);
                    holder.bgWrapper.setPadding(0, topBotPad, 0, topBotPad);
                    holder.container.setBackground(createRipple());
                    
                    holder.mainLineView.setVisibility(View.GONE);
                    holder.bgWrapper.setVisibility(View.VISIBLE);
                    holder.bgLineView.setVisibility(View.VISIBLE);
                    
                    holder.bgWrapper.forceState(isActive);
                    
                    holder.bgLineView.setLyricColor(currentColor);
                    holder.bgLineView.setFontConfig(DataManager.getFontConfig());
                    holder.bgLineView.setLyricLine(item.mainLine);
                    holder.bgLineView.setCustomEndTime(getEndTimeForLine(item.mainIndex));
                    if (item.mainLine.line != null) {
                        holder.bgLineView.setText(item.mainLine.line.toString(), getWidth() - leftPad - rightPad);
                    }
                    holder.bgLineView.setAlpha(isActive ? 0.65f : 0.0f);
                    holder.bgLineView.updateProgress(currentProgressMs, true);
                    
                } else {
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) holder.mainLineView.getLayoutParams();
                    FrameLayout.LayoutParams lp2 = (FrameLayout.LayoutParams) holder.bgLineView.getLayoutParams();
                    
                    if (item.mainLine.vocalType != 1) {
                        holder.container.setPadding(rightPad, topBotPad, leftPad, topBotPad);
                        lp.gravity = Gravity.RIGHT;
                        lp2.gravity = Gravity.RIGHT;
                        holder.mainLineView.setLineGravity(Layout.Alignment.ALIGN_OPPOSITE);
                        holder.bgLineView.setLineGravity(Layout.Alignment.ALIGN_OPPOSITE);
                    } else {
                        holder.container.setPadding(leftPad, topBotPad, rightPad, topBotPad);
                        lp.gravity = Gravity.LEFT;
                        lp2.gravity = Gravity.LEFT;
                        holder.mainLineView.setLineGravity(Layout.Alignment.ALIGN_NORMAL);
                        holder.bgLineView.setLineGravity(Layout.Alignment.ALIGN_NORMAL);
                    }
                    holder.mainLineView.setLayoutParams(lp);
                    holder.bgLineView.setLayoutParams(lp2);
                    holder.bgWrapper.setPadding(0, 0, 0, 0);
                    holder.container.setBackground(createRipple());
                    
                    holder.mainLineView.setVisibility(View.VISIBLE);
                    
                    holder.mainLineView.setLyricColor(currentColor);
                    holder.mainLineView.setFontConfig(DataManager.getFontConfig());
                    holder.mainLineView.setLyricLine(item.mainLine);
                    holder.mainLineView.setCustomEndTime(parentEndTime);
                    if (item.mainLine.line != null) {
                        holder.mainLineView.setText(item.mainLine.line.toString(), getWidth() - leftPad - rightPad);
                    }
                    holder.mainLineView.setAlpha(isActive ? 1.0f : 0.35f);
                    holder.mainLineView.updateProgress(currentProgressMs, true);

                    if (item.linkedBgLine != null) {
                        holder.bgWrapper.setVisibility(View.VISIBLE);
                        holder.bgLineView.setVisibility(View.VISIBLE);
                        holder.bgWrapper.forceState(isActive);
                        
                        holder.bgLineView.setLyricColor(currentColor);
                        holder.bgLineView.setFontConfig(DataManager.getFontConfig());
                        holder.bgLineView.setLyricLine(item.linkedBgLine);
                        holder.bgLineView.setCustomEndTime(parentEndTime);
                        if (item.linkedBgLine.line != null) {
                            holder.bgLineView.setText(item.linkedBgLine.line.toString(), getWidth() - leftPad - rightPad);
                        }
                        holder.bgLineView.setAlpha(isActive ? 0.65f : 0f);
                        holder.bgLineView.updateProgress(currentProgressMs, true);
                    } else {
                        holder.bgWrapper.setVisibility(View.GONE);
                        holder.bgLineView.setVisibility(View.GONE);
                    }
                }

                if (item.romajiLine != null) {
                    holder.romajiWrapper.setVisibility(View.VISIBLE);
                    holder.romajiView.setLyricLine(item.romajiLine);
                    holder.romajiView.setLyricColor(currentColor);
                    holder.romajiView.setFontConfig(DataManager.getFontConfig());
                    if (item.romajiLine.line != null) {
                        holder.romajiView.setText(item.romajiLine.line.toString(), getWidth() - leftPad - rightPad);
                    }
                    holder.romajiView.setAlpha(isActive ? 0.65f : 0.35f);
                    holder.romajiWrapper.forceState(isActive);
                } else {
                    holder.romajiWrapper.setVisibility(View.GONE);
                }
            }

            holder.container.setOnClickListener(v -> {
                if (activeIndex != position && listener != null && !item.mainLine.isWaitingDots) {
                    listener.onSeekRequested(item.mainLine.time);
                }
            });
        }

        @Override
        public void onViewRecycled(@NonNull LyricViewHolder holder) {
            super.onViewRecycled(holder);
            holder.container.animate().cancel();
            holder.springY.cancel();
            holder.container.setTranslationY(0f);
            holder.springY.getSpring().setStiffness(200f);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                holder.container.setRenderEffect(null);
            }

            holder.dotsWrapper.forceState(false);
            holder.dotsView.setTimes(0, 0);
            holder.dotsView.updateProgress(0);

            holder.bgWrapper.forceState(false);
            holder.bgLineView.animate().cancel();
            holder.bgLineView.setLyricLine(null);
            holder.bgLineView.resetProgress();

            holder.mainLineView.animate().cancel();
            holder.mainLineView.setLyricLine(null);
            holder.mainLineView.resetProgress();

            holder.romajiWrapper.forceState(false);
            holder.romajiView.setLyricLine(null);
        }

        @Override
        public int getItemCount() {
            return lyricItems.size();
        }

        @Override
        public int getItemViewType(int position) {
            LyricLine line = lyricItems.get(position).mainLine;
            if (line == null || line.line == null) return TYPE_LTR;

            for (char c : line.line.toString().toCharArray()) {
                if ((c >= '\u0600' && c <= '\u06FF') || (c >= '\u0750' && c <= '\u077F')) {
                    return TYPE_BIDI;
                }
            }
            return TYPE_LTR;
        }
    }

    private static class LyricViewHolder extends RecyclerView.ViewHolder {
        FrameLayout wrapper;
        LinearLayout container;
        XDynamicContainer dotsWrapper;
        XWaitingDotsView dotsView;
        XDynamicContainer bgWrapper;
        XLyricsLineView bgLineView;
        XLyricsLineView mainLineView;
        XDynamicContainer romajiWrapper;
        XLyricsRomajiLineView romajiView;
        SpringAnimation springY;

        LyricViewHolder(View v, LinearLayout c, XDynamicContainer dw, XWaitingDotsView dv, XDynamicContainer bw, XLyricsLineView bl, XLyricsLineView ml, XDynamicContainer rw, XLyricsRomajiLineView rv) {
            super(v);
            this.wrapper = (FrameLayout) v;
            this.container = c;
            this.dotsWrapper = dw;
            this.dotsView = dv;
            this.bgWrapper = bw;
            this.bgLineView = bl;
            this.mainLineView = ml;
            this.romajiWrapper = rw;
            this.romajiView = rv;

            springY = new SpringAnimation(container, DynamicAnimation.TRANSLATION_Y, 0f);
            springY.setSpring(new SpringForce(0f).setStiffness(200f).setDampingRatio(0.85f));
        }
    }

    private static class LyricItem {
        LyricLine mainLine; int mainIndex; 
        LyricLine romajiLine; int romajiIndex = -1;
        LyricLine linkedBgLine; int linkedBgIndex = -1;
        
        LyricItem(LyricLine main, int idx) { this.mainLine = main; this.mainIndex = idx; }
    }

    private class LyricPaddingDecoration extends ItemDecoration {
        @Override
        public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view,
                                   @NonNull RecyclerView parent, @NonNull State state) {
            int position = parent.getChildAdapterPosition(view);
            if (position == 0) outRect.top = parent.getHeight() / 4;
            else if (position == state.getItemCount() - 1) outRect.bottom = (int) (parent.getHeight() * (3.0/4.0));
        }
    }
}
