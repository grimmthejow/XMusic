package com.xapps.media.xmusic.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.NestedScrollingChild3;
import androidx.core.view.NestedScrollingChildHelper;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

public class SpringOverScrollLayout extends RelativeLayout implements NestedScrollingParent3, NestedScrollingChild3 {

    private static final float MAX_DRAG = 500f;
    private static final float THRESHOLD_RATIO = 2.5f;
    private static final float LOCKED_OFFSET = 200f;
    
    private static final float FRICTION_BASE = 0.9f;
    private static final float FRICTION_BUMP_STRENGTH = 0.5f;
    private static final float BUMP_WIDTH = 50f;
    private static final float JUMP_BOOST = 180f;

    private static final long COMMIT_HOLD_TIME_MS = 200;

    private final NestedScrollingParentHelper parentHelper;
    private final NestedScrollingChildHelper childHelper;
    
    private SpringAnimation springAnim;
    private SpringAnimation indicatorScaleXAnim;
    private SpringAnimation indicatorScaleYAnim;
    private SpringAnimation indicatorAlphaAnim;
    
    private float verticalOffset = 0f;
    private float targetOffset = 0f;
    private boolean isRefreshing = false;
    private boolean isVisualThresholdReached = false;
    private boolean isHoldRequirementMet = false;
    private long thresholdHoldStartTime = -1;

    private OnRefreshListener refreshListener;
    private CustomLoadingIndicator indicator;
    private View indicatorContainer;
    private View scrollTarget;

    public interface OnRefreshListener {
        void onRefresh();
    }

    public SpringOverScrollLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        parentHelper = new NestedScrollingParentHelper(this);
        childHelper = new NestedScrollingChildHelper(this);
        childHelper.setNestedScrollingEnabled(true);
        initSprings();
    }

    private void initSprings() {
        springAnim = new SpringAnimation(this, new FloatPropertyCompat<SpringOverScrollLayout>("offset") {
            @Override public float getValue(SpringOverScrollLayout o) { return verticalOffset; }
            @Override public void setValue(SpringOverScrollLayout o, float v) { 
                verticalOffset = v;
                applyTransforms();
            }
        }, 0f);
        springAnim.setSpring(new SpringForce(0f)
                .setStiffness(SpringForce.STIFFNESS_LOW)
                .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY));
    }

    private void setupIndicatorSprings() {
        if (indicator == null) return;
        SpringForce scaleSpring = new SpringForce()
                .setStiffness(SpringForce.STIFFNESS_LOW)
                .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY);
        indicatorScaleXAnim = new SpringAnimation(indicator, DynamicAnimation.SCALE_X, 1f).setSpring(scaleSpring);
        indicatorScaleYAnim = new SpringAnimation(indicator, DynamicAnimation.SCALE_Y, 1f).setSpring(scaleSpring);
        indicatorAlphaAnim = new SpringAnimation(indicator, DynamicAnimation.ALPHA, 1f)
                .setSpring(new SpringForce().setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
    }

    private void applyTransforms() {
        if (scrollTarget != null) {
            scrollTarget.setTranslationY(verticalOffset);
            if (verticalOffset < 0) {
                scrollTarget.setOverScrollMode(View.OVER_SCROLL_NEVER);
            }
        }
        
        if (indicatorContainer != null) {
            ViewGroup.LayoutParams lp = indicatorContainer.getLayoutParams();
            lp.height = (int) Math.max(1, verticalOffset);
            indicatorContainer.setLayoutParams(lp);
        }

        if (indicator != null) {
            float h = indicator.getHeight() > 0 ? indicator.getHeight() : (64 * getResources().getDisplayMetrics().density);
            indicator.setTranslationY((verticalOffset / 2f) - (h / 2f));
            float threshold = MAX_DRAG / THRESHOLD_RATIO;
            if (!isRefreshing) {
                indicator.setPullProgress(Math.min(1f, Math.max(0, verticalOffset) / threshold));
            }
        }
    }

    private void triggerIndicatorIn() {
        if (indicator == null) return;
        indicator.setVisibility(VISIBLE);
        indicatorAlphaAnim.getSpring().setStiffness(SpringForce.STIFFNESS_MEDIUM);
        indicatorAlphaAnim.setStartValue(0f).animateToFinalPosition(1f);
        indicatorScaleXAnim.setStartValue(0.7f).animateToFinalPosition(1f);
        indicatorScaleYAnim.setStartValue(0.7f).animateToFinalPosition(1f);
    }

    private void triggerIndicatorOut(boolean shouldCollapseAfter) {
        if (indicator == null) return;
        indicatorAlphaAnim.getSpring().setStiffness(SpringForce.STIFFNESS_MEDIUM);
        indicatorAlphaAnim.animateToFinalPosition(0f);
        indicatorScaleXAnim.animateToFinalPosition(0.7f);
        indicatorScaleYAnim.animateToFinalPosition(0.7f);
        indicatorAlphaAnim.addEndListener(new DynamicAnimation.OnAnimationEndListener() {
            @Override
            public void onAnimationEnd(DynamicAnimation animation, boolean canceled, float value, float velocity) {
                if (!isVisualThresholdReached) indicator.setVisibility(INVISIBLE);
                if (shouldCollapseAfter) {
                    targetOffset = 0;
                    springAnim.animateToFinalPosition(0f);
                }
                indicatorAlphaAnim.removeEndListener(this);
            }
        });
    }

    @Override
    public void onNestedScroll(@NonNull View target, int dxC, int dyC, int dxU, int dyU, int type, @NonNull int[] consumed) {
        if (isRefreshing) return;
        childHelper.dispatchNestedScroll(dxC, dyC, dxU, dyU, null, type, consumed);
        int rem = dyU - (consumed.length > 1 ? consumed[1] : 0);
        
        if (rem != 0 && type == ViewCompat.TYPE_TOUCH) {
            float threshold = MAX_DRAG / THRESHOLD_RATIO;

            if (rem < 0) {
                if (targetOffset >= threshold) {
                    if (!isVisualThresholdReached) {
                        isVisualThresholdReached = true;
                        targetOffset += JUMP_BOOST;
                        springAnim.animateToFinalPosition(targetOffset);
                        triggerIndicatorIn();
                        thresholdHoldStartTime = System.currentTimeMillis();
                    }
                    if (!isHoldRequirementMet && (System.currentTimeMillis() - thresholdHoldStartTime >= COMMIT_HOLD_TIME_MS)) {
                        isHoldRequirementMet = true;
                    }
                }
            } else {
                if (targetOffset > 0 && isVisualThresholdReached) {
                    if (targetOffset - (rem * FRICTION_BASE) < threshold) {
                        isVisualThresholdReached = false;
                        isHoldRequirementMet = false;
                        thresholdHoldStartTime = -1;
                        targetOffset = Math.max(0, targetOffset - JUMP_BOOST);
                        springAnim.animateToFinalPosition(targetOffset);
                        triggerIndicatorOut(false);
                    }
                }
            }

            float friction;
            if (targetOffset >= 0) {
                float dist = targetOffset - threshold;
                float bumpStrength = (float) Math.exp(-(dist * dist) / (2 * BUMP_WIDTH * BUMP_WIDTH));
                friction = (targetOffset < threshold) ? (FRICTION_BASE - (FRICTION_BUMP_STRENGTH * bumpStrength)) : ((FRICTION_BASE + 0.05f) * (1.0f - (float) Math.pow((targetOffset - threshold) / (MAX_DRAG - threshold), 2)));
            } else {
                friction = FRICTION_BASE * (1.0f - Math.abs(targetOffset / MAX_DRAG));
            }

            targetOffset = Math.min(MAX_DRAG, Math.max(-MAX_DRAG, targetOffset - (rem * friction)));
            springAnim.animateToFinalPosition(targetOffset);
            consumed[1] += rem;
        }
    }

    public void setRefreshing(boolean refreshing) {
        if (this.isRefreshing == refreshing) return;
        this.isRefreshing = refreshing;
        if (refreshing) {
            targetOffset = LOCKED_OFFSET;
            springAnim.animateToFinalPosition(LOCKED_OFFSET);
            if (indicator != null) {
                indicator.setVisibility(VISIBLE);
                indicator.setAlpha(1f);
                indicator.setScaleX(1f);
                indicator.setScaleY(1f);
                indicator.startRefreshing();
            }
        } else {
            isVisualThresholdReached = false;
            isHoldRequirementMet = false;
            thresholdHoldStartTime = -1;
            if (indicator != null) {
                indicator.stopRefreshing();
                triggerIndicatorOut(true);
            } else {
                targetOffset = 0;
                springAnim.animateToFinalPosition(0f);
            }
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        indicatorContainer = findViewById(com.xapps.media.xmusic.R.id.indicatorContainer);
        scrollTarget = findViewById(com.xapps.media.xmusic.R.id.songsList);
        if (indicatorContainer instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) indicatorContainer;
            for (int i = 0; i < group.getChildCount(); i++) {
                View v = group.getChildAt(i);
                if (v instanceof CustomLoadingIndicator) {
                    indicator = (CustomLoadingIndicator) v;
                    indicator.bringToFront();
                    setupIndicatorSprings();
                    break;
                }
            }
        }
    }

    @Override public void onNestedScroll(@NonNull View target, int dxC, int dyC, int dxU, int dyU, int type) { onNestedScroll(target, dxC, dyC, dxU, dyU, type, new int[2]); }
    @Override public void onStopNestedScroll(@NonNull View target, int type) { parentHelper.onStopNestedScroll(target, type); stopNestedScroll(type); if (!isRefreshing && isHoldRequirementMet && targetOffset >= (MAX_DRAG / THRESHOLD_RATIO)) { setRefreshing(true); if (refreshListener != null) refreshListener.onRefresh(); } else if (!isRefreshing) { isVisualThresholdReached = false; isHoldRequirementMet = false; thresholdHoldStartTime = -1; targetOffset = 0; springAnim.animateToFinalPosition(0f); triggerIndicatorOut(false); } }
    @Override public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes, int type) { return (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0 || startNestedScroll(axes, type); }
    @Override public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes, int type) { parentHelper.onNestedScrollAccepted(child, target, axes, type); startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, type); }
    
    @Override public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) { 
        if (isRefreshing) {
            consumed[1] = dy;
            return;
        } 
        
        if (targetOffset > 0 && dy > 0) { 
            float threshold = MAX_DRAG / THRESHOLD_RATIO; 
            float oldOffset = targetOffset; 
            targetOffset = Math.max(0, targetOffset - dy); 
            if (oldOffset >= threshold && targetOffset < threshold && isVisualThresholdReached) { 
                isVisualThresholdReached = false; 
                isHoldRequirementMet = false;
                thresholdHoldStartTime = -1; 
                targetOffset = Math.max(0, targetOffset - JUMP_BOOST); 
                triggerIndicatorOut(false); 
            } 
            springAnim.animateToFinalPosition(targetOffset); 
            consumed[1] = (int) (oldOffset - targetOffset); 
        } else if (targetOffset < 0 && dy < 0) {
            float oldOffset = targetOffset;
            targetOffset = Math.min(0, targetOffset - dy);
            springAnim.animateToFinalPosition(targetOffset);
            consumed[1] = (int) (oldOffset - targetOffset);
        }
        
        if (Math.abs(dy) > Math.abs(consumed[1])) {
            dispatchNestedPreScroll(dx, dy - consumed[1], consumed, null, type);
        }
    }
    
    @Override public void setNestedScrollingEnabled(boolean e) { childHelper.setNestedScrollingEnabled(e); }
    @Override public boolean isNestedScrollingEnabled() { return childHelper.isNestedScrollingEnabled(); }
    @Override public boolean startNestedScroll(int a, int t) { return childHelper.startNestedScroll(a, t); }
    @Override public void stopNestedScroll(int t) { childHelper.stopNestedScroll(t); }
    @Override public boolean hasNestedScrollingParent(int t) { return childHelper.hasNestedScrollingParent(t); }
    @Override public boolean dispatchNestedScroll(int dxC, int dyC, int dxU, int dyU, int[] o, int t) { return childHelper.dispatchNestedScroll(dxC, dyC, dxU, dyU, o, t); }
    @Override public void dispatchNestedScroll(int dxC, int dyC, int dxU, int dyU, int[] o, int t, int[] c) { childHelper.dispatchNestedScroll(dxC, dyC, dxU, dyU, o, t, c); }
    @Override public boolean dispatchNestedPreScroll(int dx, int dy, int[] c, int[] o, int t) { return childHelper.dispatchNestedPreScroll(dx, dy, c, o, t); }
    public void setOnRefreshListener(OnRefreshListener l) { this.refreshListener = l; }
}
