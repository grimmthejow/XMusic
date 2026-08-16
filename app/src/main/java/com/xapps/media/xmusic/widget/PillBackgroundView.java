package com.xapps.media.xmusic.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class PillBackgroundView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float effectiveWidth;

    public PillBackgroundView(@NonNull Context context) {
        super(context);
    }

    public PillBackgroundView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public PillBackgroundView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    public void update(float effectiveWidth) {
        if (this.effectiveWidth == effectiveWidth) return;

        this.effectiveWidth = effectiveWidth;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = Math.min(effectiveWidth, getWidth());

        if (width <= 0f) return;

        float left = (getWidth() - width) / 2f;
        float right = left + width;
        float radius = getHeight() / 2f;

        canvas.drawRoundRect(
                left,
                0f,
                right,
                getHeight(),
                radius,
                radius,
                paint
        );
    }
}