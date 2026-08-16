package com.xapps.media.xmusic.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

public class RemainingHeightLinearLayout extends LinearLayout {

    public RemainingHeightLinearLayout(Context context) {
        super(context);
        init();
    }

    public RemainingHeightLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RemainingHeightLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int childCount = getChildCount();
        if (childCount == 0) {
            return;
        }

        View lastChild = getChildAt(childCount - 1);
        if (lastChild.getVisibility() == GONE) {
            return;
        }

        int usedHeight = getPaddingTop() + getPaddingBottom();

        for (int i = 0; i < childCount - 1; i++) {
            View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            usedHeight += child.getMeasuredHeight();
            usedHeight += lp.topMargin + lp.bottomMargin;
        }

        MarginLayoutParams lp = (MarginLayoutParams) lastChild.getLayoutParams();

        int remainingHeight = getMeasuredHeight()
                - usedHeight
                - lp.topMargin
                - lp.bottomMargin;

        if (remainingHeight < 0) {
            remainingHeight = 0;
        }

        int childWidthSpec = getChildMeasureSpec(
                widthMeasureSpec,
                getPaddingLeft()
                        + getPaddingRight()
                        + lp.leftMargin
                        + lp.rightMargin,
                lp.width);

        int childHeightSpec = MeasureSpec.makeMeasureSpec(
                remainingHeight,
                MeasureSpec.EXACTLY);

        lastChild.measure(childWidthSpec, childHeightSpec);
    }
}