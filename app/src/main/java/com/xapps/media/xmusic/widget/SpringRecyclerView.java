package com.xapps.media.xmusic.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

public class SpringRecyclerView extends RecyclerView implements NestedScrollingParent3 {

    private static final float MAX_DRAG = 800f;
    private static final float FRICTION_BASE = 0.45f;

    private SpringAnimation springAnim;
    private float verticalOffset = 0f;
    private float targetOffset = 0f;
    
    private final NestedScrollingParentHelper parentHelper;

    public SpringRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        parentHelper = new NestedScrollingParentHelper(this);
        setOverScrollMode(OVER_SCROLL_NEVER);
        initSprings();
    }

    private void initSprings() {
        springAnim = new SpringAnimation(this, new FloatPropertyCompat<SpringRecyclerView>("offset") {
            @Override public float getValue(SpringRecyclerView o) { return verticalOffset; }
            @Override public void setValue(SpringRecyclerView o, float v) { 
                verticalOffset = v;
                setTranslationY(verticalOffset);
            }
        }, 0f);
        
        springAnim.setSpring(new SpringForce(0f)
                .setStiffness(SpringForce.STIFFNESS_LOW)
                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
    }

    @Override
    public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes, int type) {
        return (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override
    public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes, int type) {
        parentHelper.onNestedScrollAccepted(child, target, axes, type);
    }

    @Override
    public void onNestedScroll(@NonNull View target, int dxC, int dyC, int dxU, int dyU, int type, @NonNull int[] consumed) {
        if (dyU != 0 && type == ViewCompat.TYPE_TOUCH) {
            float friction = FRICTION_BASE * (1.0f - Math.abs(targetOffset / MAX_DRAG));
            targetOffset = Math.min(MAX_DRAG, Math.max(-MAX_DRAG, targetOffset - (dyU * friction)));
            springAnim.animateToFinalPosition(targetOffset);
            consumed[1] += dyU;
        }
    }

    @Override
    public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
        if (targetOffset > 0 && dy > 0) {
            float oldOffset = targetOffset;
            targetOffset = Math.max(0, targetOffset - dy);
            springAnim.animateToFinalPosition(targetOffset);
            consumed[1] = (int) (oldOffset - targetOffset);
        } else if (targetOffset < 0 && dy < 0) {
            float oldOffset = targetOffset;
            targetOffset = Math.min(0, targetOffset - dy);
            springAnim.animateToFinalPosition(targetOffset);
            consumed[1] = (int) (oldOffset - targetOffset);
        }
    }

    @Override
    public void onStopNestedScroll(@NonNull View target, int type) {
        parentHelper.onStopNestedScroll(target, type);
        targetOffset = 0;
        springAnim.animateToFinalPosition(0f);
    }

    @Override public void onNestedScroll(@NonNull View target, int dxC, int dyC, int dxU, int dyU, int type) { onNestedScroll(target, dxC, dyC, dxU, dyU, type, new int[2]); }
}
