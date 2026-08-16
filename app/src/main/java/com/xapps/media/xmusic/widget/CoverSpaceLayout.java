package com.xapps.media.xmusic.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CoverSpaceLayout extends View {

    private int maximumSize;

    public CoverSpaceLayout(@NonNull Context context) {
        super(context);
    }

    public CoverSpaceLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CoverSpaceLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public int getMaximumSize() {
        return maximumSize;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int availableWidth = Math.max(
                0,
                MeasureSpec.getSize(widthMeasureSpec)
                        - getPaddingLeft()
                        - getPaddingRight()
        );

        int availableHeight = MeasureSpec.getSize(heightMeasureSpec);

        maximumSize = Math.min(
                availableWidth,
                availableHeight
        );

        setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                maximumSize
        );
    }
}