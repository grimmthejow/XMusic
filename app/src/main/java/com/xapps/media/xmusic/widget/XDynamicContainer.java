package com.xapps.media.xmusic.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

public class XDynamicContainer extends FrameLayout {

    private ValueAnimator animator;
    private boolean isExpanded = false;

    public XDynamicContainer(Context context) {
        super(context);
        setClipChildren(true);
        setClipToPadding(false);
    }

    public void toggle(boolean expand, boolean animate) {
        if (this.isExpanded == expand) return;
        this.isExpanded = expand;

        if (animator != null) animator.cancel();

        int childHeight = 0;
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            int parentWidth = getParent() instanceof ViewGroup ? ((ViewGroup) getParent()).getWidth() : getWidth();
            child.measure(
                MeasureSpec.makeMeasureSpec(parentWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            );
            childHeight = child.getMeasuredHeight();
        }

        int targetH = expand ? childHeight : 0;
        
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp == null) {
            lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
            setLayoutParams(lp);
        }
        
        int startH = lp.height == ViewGroup.LayoutParams.WRAP_CONTENT ? childHeight : lp.height;

        if (!animate) {
            updateHeight(expand ? ViewGroup.LayoutParams.WRAP_CONTENT : 0);
            if (getChildCount() > 0) {
                getChildAt(0).setAlpha(expand ? 1f : 0f);
            }
            return;
        }

        float startAlpha = getChildCount() > 0 ? getChildAt(0).getAlpha() : 0f;
        float targetAlpha = expand ? 1f : 0f;

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(300);
        animator.setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f));
        animator.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            updateHeight((int) (startH + (targetH - startH) * f));
            if (getChildCount() > 0) {
                getChildAt(0).setAlpha(startAlpha + (targetAlpha - startAlpha) * f);
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (expand) updateHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        animator.start();
    }

    private void updateHeight(int h) {
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp != null && lp.height != h) {
            lp.height = h;
            setLayoutParams(lp);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) animator.cancel();
    }
    
    public void forceState(boolean expand) {
        this.isExpanded = expand;
        if (animator != null) animator.cancel();
        updateHeight(expand ? ViewGroup.LayoutParams.WRAP_CONTENT : 0);
        if (getChildCount() > 0) {
            getChildAt(0).setAlpha(expand ? 1f : 0f);
        }
    }
}
