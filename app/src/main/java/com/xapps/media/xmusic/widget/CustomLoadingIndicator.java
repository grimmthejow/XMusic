package com.xapps.media.xmusic.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.PathInterpolator;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.graphics.shapes.Morph;
import androidx.graphics.shapes.RoundedPolygon;
import androidx.graphics.shapes.Shapes_androidKt;
import com.google.android.material.shape.MaterialShapes;
import com.xapps.media.xmusic.utils.MaterialColorUtils;

public class CustomLoadingIndicator extends View {

    private final Paint mShapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private final Matrix mMatrix = new Matrix();

    private float mMorphProgress = 0f;
    private float mRotationAngle = 0f;
    private float mBaseRotation = 0f;
    private boolean mIsRefreshing = false;

    private int mCurrentIdx = 0;
    private int mTargetIdx = 1;
    private Morph mActiveMorph;

    private static final float STEP_ANGLE = 160f;
    private final PathInterpolator mInterpolator = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    private static final RoundedPolygon[] SHAPES = {
        MaterialShapes.normalize(MaterialShapes.SQUARE,    true, new RectF(-1f, -1f, 1f, 1f)),
        MaterialShapes.normalize(MaterialShapes.COOKIE_12, true, new RectF(-1f, -1f, 1f, 1f)),
        MaterialShapes.normalize(MaterialShapes.PILL, true, new RectF(-1f, -1f, 1f, 1f)),
        MaterialShapes.normalize(MaterialShapes.SOFT_BURST,true, new RectF(-1f, -1f, 1f, 1f)),
        MaterialShapes.normalize(MaterialShapes.PENTAGON,  true, new RectF(-1f, -1f, 1f, 1f)),
        MaterialShapes.normalize(MaterialShapes.COOKIE_9,  true, new RectF(-1f, -1f, 1f, 1f)),
        MaterialShapes.normalize(MaterialShapes.OVAL, true, new RectF(-1f, -1f, 1f, 1f)),
        MaterialShapes.normalize(MaterialShapes.SUNNY,     true, new RectF(-1f, -1f, 1f, 1f)),
        MaterialShapes.normalize(MaterialShapes.COOKIE_4,  true, new RectF(-1f, -1f, 1f, 1f)),
        MaterialShapes.normalize(MaterialShapes.TRIANGLE, true, new RectF(-1f, -1f, 1f, 1f))
    };

    public CustomLoadingIndicator(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mShapePaint.setColor(MaterialColorUtils.colorPrimary);
        mBgPaint.setColor(MaterialColorUtils.colorPrimaryContainer);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        updateMorph();
    }

    private void updateMorph() {
        mActiveMorph = new Morph(SHAPES[mCurrentIdx], SHAPES[mTargetIdx]);
    }

    public void setPullProgress(float progress) {
        if (mIsRefreshing) return;
        mMorphProgress = progress;
        mRotationAngle = progress * STEP_ANGLE;
        invalidate();
    }

    public void startRefreshing() {
        if (mIsRefreshing) return;
        mIsRefreshing = true;
        
        mBaseRotation = mRotationAngle;

        mCurrentIdx = mTargetIdx;
        mTargetIdx = (mTargetIdx + 1) % SHAPES.length;
        
        mMorphProgress = 0f;
        updateMorph();
        
        runCycle();
    }

    private void runCycle() {
        if (!mIsRefreshing) return;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(500);
        animator.setInterpolator(mInterpolator);
        final float startRotation = mBaseRotation;

        animator.addUpdateListener(a -> {
            mMorphProgress = (float) a.getAnimatedValue();
            mRotationAngle = startRotation + (mMorphProgress * STEP_ANGLE);
            invalidate();
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (!mIsRefreshing) return;
                mBaseRotation += STEP_ANGLE;
                mCurrentIdx = mTargetIdx;
                mTargetIdx = (mTargetIdx + 1) % SHAPES.length;
                mMorphProgress = 0f;
                updateMorph();
                postDelayed(() -> runCycle(), 100);
            }
        });
        animator.start();
    }

    public void stopRefreshing() {
        mIsRefreshing = false;
        mCurrentIdx = 0;
        mTargetIdx = 1;
        mMorphProgress = 0f;
        mRotationAngle = 0f;
        mBaseRotation = 0f;
        updateMorph();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float rad = Math.min(cx, cy) * 0.85f;

        canvas.drawCircle(cx, cy, rad, mBgPaint);

        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(mRotationAngle);

        float s = rad * 0.65f;
        mPath.rewind();
        Shapes_androidKt.toPath(mActiveMorph, mMorphProgress, mPath);
        mMatrix.setScale(s, s);
        mPath.transform(mMatrix);

        canvas.drawPath(mPath, mShapePaint);
        canvas.restore();
    }
}
