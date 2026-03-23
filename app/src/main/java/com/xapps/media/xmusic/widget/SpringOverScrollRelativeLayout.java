package com.xapps.media.xmusic.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.NestedScrollingChild3;
import androidx.core.view.NestedScrollingChildHelper;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.google.android.material.appbar.AppBarLayout;

public class SpringOverScrollRelativeLayout extends RelativeLayout implements NestedScrollingParent3, NestedScrollingChild3, AppBarLayout.OnOffsetChangedListener {

    private static final float MAX_DRAG = 800f;
    private static final float FRICTION_BASE = 0.45f;

    private final NestedScrollingParentHelper parentHelper;
    private final NestedScrollingChildHelper childHelper;
    
    private SpringAnimation springAnim;
    private float verticalOffset = 0f;
    private View scrollTarget;

    private int appBarOffset = 0;
    private int totalAppBarRange = 0;

    public SpringOverScrollRelativeLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        parentHelper = new NestedScrollingParentHelper(this);
        childHelper = new NestedScrollingChildHelper(this);
        childHelper.setNestedScrollingEnabled(true);
        initSprings();
    }

    private void initSprings() {
        springAnim = new SpringAnimation(this, new FloatPropertyCompat<SpringOverScrollRelativeLayout>("offset") {
            @Override public float getValue(SpringOverScrollRelativeLayout o) { return verticalOffset; }
            @Override public void setValue(SpringOverScrollRelativeLayout o, float v) { 
                verticalOffset = v;
                if (scrollTarget != null) {
                    scrollTarget.setTranslationY(verticalOffset);
                }
            }
        }, 0f);
        
        springAnim.setSpring(new SpringForce(0f)
                .setStiffness(SpringForce.STIFFNESS_LOW)
                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
    }

    @Override
    public void onOffsetChanged(AppBarLayout appBarLayout, int offset) {
        this.appBarOffset = offset;
        this.totalAppBarRange = appBarLayout.getTotalScrollRange();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getParent() instanceof View) {
            AppBarLayout appBar = ((View) getParent()).findViewById(com.xapps.media.xmusic.R.id.appbar);
            if (appBar != null) {
                appBar.addOnOffsetChangedListener(this);
            }
        }
    }

    private void applyOverScroll(float delta) {
        if (springAnim.isRunning()) springAnim.cancel();

        float ratio = Math.min(1f, Math.abs(verticalOffset / MAX_DRAG));
        float friction = FRICTION_BASE * (1.0f - ratio);
        
        verticalOffset += (delta * friction);
        
        if (scrollTarget != null) {
            scrollTarget.setTranslationY(verticalOffset);
        }
    }

    @Override
    public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
        if (verticalOffset != 0) {
            if ((verticalOffset > 0 && dy > 0) || (verticalOffset < 0 && dy < 0)) {
                float oldOffset = verticalOffset;
                if (verticalOffset > 0) {
                    verticalOffset = Math.max(0, verticalOffset - dy);
                } else {
                    verticalOffset = Math.min(0, verticalOffset - dy);
                }
                consumed[1] = (int) (oldOffset - verticalOffset);
                if (scrollTarget != null) scrollTarget.setTranslationY(verticalOffset);
            }
        }

        if (dy != consumed[1]) {
            int[] pConsumed = new int[2];
            childHelper.dispatchNestedPreScroll(dx, dy - consumed[1], pConsumed, null, type);
            consumed[1] += pConsumed[1];
        }
    }

    @Override
    public void onNestedScroll(@NonNull View target, int dxC, int dyC, int dxU, int dyU, int type, @NonNull int[] consumed) {
        int[] pConsumed = new int[2];
        childHelper.dispatchNestedScroll(dxC, dyC, dxU, dyU, null, type, pConsumed);
        consumed[1] += pConsumed[1];
        
        int rem = dyU - pConsumed[1];

        if (rem != 0 && type == ViewCompat.TYPE_TOUCH) {
            boolean isAtTop = appBarOffset >= 0 && !ViewCompat.canScrollVertically(target, -1);
            boolean isAtBottom = Math.abs(appBarOffset) >= totalAppBarRange && !ViewCompat.canScrollVertically(target, 1);

            if ((rem < 0 && isAtTop) || (rem > 0 && isAtBottom)) {
                applyOverScroll(-rem);
                consumed[1] += rem;
            }
        }
    }

    @Override
    public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes, int type) {
        if (springAnim.isRunning()) {
            springAnim.cancel();
        }
        return (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override
    public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes, int type) {
        parentHelper.onNestedScrollAccepted(child, target, axes, type);
        startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, type);
    }

    @Override
    public void onStopNestedScroll(@NonNull View target, int type) {
        parentHelper.onStopNestedScroll(target, type);
        stopNestedScroll(type);
        if (verticalOffset != 0) {
            springAnim.setStartVelocity(0);
            springAnim.setStartValue(verticalOffset);
            springAnim.animateToFinalPosition(0f);
        }
    }

    @Override protected void onFinishInflate() { super.onFinishInflate(); if (getChildCount() > 0) scrollTarget = getChildAt(0); }
    @Override public void onNestedScroll(@NonNull View target, int dxC, int dyC, int dxU, int dyU, int type) { onNestedScroll(target, dxC, dyC, dxU, dyU, type, new int[2]); }
    @Override public void setNestedScrollingEnabled(boolean e) { childHelper.setNestedScrollingEnabled(e); }
    @Override public boolean isNestedScrollingEnabled() { return childHelper.isNestedScrollingEnabled(); }
    @Override public boolean startNestedScroll(int a, int t) { return childHelper.startNestedScroll(a, t); }
    @Override public void stopNestedScroll(int t) { childHelper.stopNestedScroll(t); }
    @Override public boolean hasNestedScrollingParent(int t) { return childHelper.hasNestedScrollingParent(t); }
    @Override public boolean dispatchNestedScroll(int dxC, int dyC, int dxU, int dyU, int[] o, int t) { return childHelper.dispatchNestedScroll(dxC, dyC, dxU, dyU, o, t); }
    @Override public void dispatchNestedScroll(int dxC, int dyC, int dxU, int dyU, int[] o, int t, int[] c) { childHelper.dispatchNestedScroll(dxC, dyC, dxU, dyU, o, t, c); }
    @Override public boolean dispatchNestedPreScroll(int dx, int dy, int[] c, int[] o, int t) { return childHelper.dispatchNestedPreScroll(dx, dy, c, o, t); }
}
