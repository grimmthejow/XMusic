package com.xapps.media.xmusic.widget;

/*
 * Copyright 2015 Hugo Gresse
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Rewritten and optimized for my app needs.
 */

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.xapps.media.xmusic.R;

import java.util.Random;

public class VuMeterView extends View {

    public static final int STATE_PLAYING = 0;
    public static final int STATE_PAUSED = 1;
    public static final int STATE_STOPPED = 2;

    private static final int DEFAULT_BLOCKS = 3;
    private static final float DEFAULT_SPACING = 20f;
    private static final float MIN_HEIGHT_RATIO = 0.15f;
    private static final float SMOOTHING = 0.1f;
    private static final int TARGET_UPDATE_FRAMES = 20;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();

    private int state = STATE_PLAYING;

    private int blockCount;
    private float blockSpacing;
    private int color;

    private float[] current;
    private float[] target;

    private int contentHeight;
    private int blockWidth;

    private int frame;

    public VuMeterView(Context context) {
        super(context);
        init(null);
    }

    public VuMeterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public VuMeterView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        TypedArray a = getContext().obtainStyledAttributes(
                attrs,
                R.styleable.vumeter_VuMeterView
        );

        color = a.getColor(
                R.styleable.vumeter_VuMeterView_vumeter_backgroundColor,
                Color.BLACK
        );

        blockCount = a.getInt(
                R.styleable.vumeter_VuMeterView_vumeter_blockNumber,
                DEFAULT_BLOCKS
        );

        blockSpacing = a.getDimension(
                R.styleable.vumeter_VuMeterView_vumeter_blockSpacing,
                DEFAULT_SPACING
        );

        a.recycle();

        paint.setColor(color);

        current = new float[blockCount];
        target = new float[blockCount];
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        int pl = getPaddingLeft();
        int pr = getPaddingRight();
        int pt = getPaddingTop();
        int pb = getPaddingBottom();

        contentHeight = h - pt - pb;
        int contentWidth = w - pl - pr;

        blockWidth = (int) ((contentWidth - (blockCount - 1) * blockSpacing) / blockCount);

        float min = contentHeight * MIN_HEIGHT_RATIO;

        for (int i = 0; i < blockCount; i++) {
            current[i] = min;
            target[i] = min;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int base = getHeight() - getPaddingBottom();
        int left = getPaddingLeft();

        frame++;

        if (state == STATE_PLAYING && frame % TARGET_UPDATE_FRAMES == 0) {
            for (int i = 0; i < blockCount; i++) {
                target[i] = contentHeight *
                        (MIN_HEIGHT_RATIO + random.nextFloat() * (1f - MIN_HEIGHT_RATIO));
            }
        }

        for (int i = 0; i < blockCount; i++) {
            current[i] += (target[i] - current[i]) * SMOOTHING;

            float top = base - current[i];

            canvas.drawRect(
                    left,
                    top,
                    left + blockWidth,
                    base,
                    paint
            );

            left += blockWidth + blockSpacing;
        }

        postInvalidateOnAnimation();
    }

    public void pause() {
        state = STATE_PAUSED;
    }

    public void resume() {
        if (state != STATE_PLAYING) {
            state = STATE_PLAYING;
            frame = 0;
        }
    }

    public void stop() {
        state = STATE_STOPPED;
        float min = contentHeight * MIN_HEIGHT_RATIO;
        for (int i = 0; i < blockCount; i++) {
            target[i] = min;
        }
    }

    public boolean isPlaying() {
        return state == STATE_PLAYING;
    }

    public void setColor(int color) {
        this.color = color;
        paint.setColor(color);
        invalidate();
    }

    public int getColor() {
        return color;
    }

    public void setBlockCount(int count) {
        blockCount = Math.max(1, count);
        current = new float[blockCount];
        target = new float[blockCount];
        requestLayout();
    }
}
