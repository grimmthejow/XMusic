package com.xapps.media.xmusic.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.xapps.media.xmusic.R;
import com.xapps.media.xmusic.utils.XUtils;

import java.io.File;

public class SongCover extends FrameLayout {

    private final Rect collapsedBounds = new Rect();
    private final Rect expandedBounds = new Rect();
    private final Rect currentChildBounds = new Rect();

    private int collapsedCenterX;
    private int collapsedCenterY;
    private int collapsedSize;

    private int expandedCenterX;
    private int expandedCenterY;
    private int expandedSize;

    private AppCompatImageView innerImageView;
    private Drawable previousDrawable;
    private ValueAnimator animator;
    private float transitionProgress = 1f;
    private CustomTarget<Drawable> glideTarget;
    private boolean isManualGeometry = false;

    private int collapsedMargin = -1;

    public SongCover(Context context) {
        super(context);
        init(context, null);
    }

    public SongCover(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public SongCover(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setClipChildren(false);
        setClipToPadding(false);

        collapsedMargin = XUtils.convertToPx(context, 8f);

        innerImageView =
                new AppCompatImageView(context) {
                    @Override
                    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                        super.onSizeChanged(w, h, oldw, oldh);
                        if (previousDrawable != null) {
                            previousDrawable.setBounds(0, 0, w, h);
                        }
                    }

                    @Override
                    protected void onDraw(Canvas canvas) {
                        if (transitionProgress < 1f && previousDrawable != null) {
                            canvas.save();
                            float previousAlpha = Math.max(0f, 1f - transitionProgress * 2f);
                            float previousScale = 1f + 0.1f * transitionProgress;
                            previousDrawable.setAlpha((int) (255 * previousAlpha));
                            canvas.scale(previousScale, previousScale, getWidth() / 2f, getHeight() / 2f);
                            previousDrawable.draw(canvas);
                            canvas.restore();

                            canvas.save();
                            float currentAlpha = Math.max(0f, (transitionProgress - 0.5f) * 2f);
                            float currentScale = 0.9f + 0.1f * transitionProgress;

                            Drawable currentDrawable = getDrawable();
                            if (currentDrawable != null) {
                                currentDrawable.setAlpha((int) (255 * currentAlpha));
                            }

                            canvas.scale(currentScale, currentScale, getWidth() / 2f, getHeight() / 2f);
                            super.onDraw(canvas);
                            canvas.restore();
                        } else {
                            Drawable currentDrawable = getDrawable();
                            if (currentDrawable != null) {
                                currentDrawable.setAlpha(255);
                            }
                            super.onDraw(canvas);
                        }
                    }
                };

        innerImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

        addView(innerImageView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, new int[] {android.R.attr.src});
            Drawable src = ta.getDrawable(0);
            if (src != null) {
                innerImageView.setImageDrawable(src);
            }
            ta.recycle();
        }

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(350);
        animator.setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f));

        animator.addUpdateListener(animation -> {
            transitionProgress = (float) animation.getAnimatedValue();
            innerImageView.invalidate();
        });
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (isManualGeometry && !currentChildBounds.isEmpty()) {
            if (innerImageView != null) {
                innerImageView.layout(
                        currentChildBounds.left,
                        currentChildBounds.top,
                        currentChildBounds.right,
                        currentChildBounds.bottom);
            }
        } else {
            super.onLayout(changed, left, top, right, bottom);
        }
    }

    public void captureCollapsedBounds() {
        collapsedBounds.set(getLeft(), getTop(), getRight(), getBottom());
        collapsedCenterX = collapsedBounds.centerX();
        collapsedCenterY = collapsedBounds.centerY();
        collapsedSize = Math.min(collapsedBounds.width(), collapsedBounds.height());

        if (collapsedMargin == -1) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) getLayoutParams();
            collapsedMargin = lp.leftMargin;
        }
    }

    public void setCollapsedBounds(int left, int top, int right, int bottom) {
        collapsedBounds.set(left, top, right, bottom);
        collapsedCenterX = collapsedBounds.centerX();
        collapsedCenterY = collapsedBounds.centerY();
        collapsedSize = Math.min(collapsedBounds.width(), collapsedBounds.height());
    }

    public void setExpandedBounds(int left, int top, int right, int bottom) {
        expandedBounds.set(left, top, right, bottom);
        expandedCenterX = expandedBounds.centerX();
        expandedCenterY = expandedBounds.centerY();
        expandedSize = Math.min(expandedBounds.width(), expandedBounds.height());
    }

    public void setExpansionProgress(float progress) {
        isManualGeometry = true;

        int centerX = lerp(collapsedCenterX, expandedCenterX, progress);
        int centerY = lerp(collapsedCenterY, expandedCenterY, progress);
        int size = lerp(collapsedSize, expandedSize, progress);

        int half = size / 2;
        int left = centerX - half;
        int top = centerY - half;

        int marginOffset = Math.round(collapsedMargin * progress);

        currentChildBounds.set(
                left - marginOffset,
                top - marginOffset,
                left + size,
                top + size
        );

        if (innerImageView != null) {
            innerImageView.layout(
                    currentChildBounds.left,
                    currentChildBounds.top,
                    currentChildBounds.right,
                    currentChildBounds.bottom
            );
        }
    }

    public void releaseManualGeometry() {
        isManualGeometry = false;
        requestLayout();
    }

    private static int lerp(int start, int end, float progress) {
        return Math.round(start + (end - start) * progress);
    }

    public void load(Uri uri) {
        executeGlideLoad(uri);
    }

    public void load(File file) {
        executeGlideLoad(file);
    }

    public void load(int resourceId) {
        executeGlideLoad(resourceId);
    }

    public void load(String filePath) {
        executeGlideLoad(filePath);
    }

    private void executeGlideLoad(Object model) {
        if (glideTarget != null) {
            Glide.with(getContext()).clear(glideTarget);
        }

        glideTarget = new CustomTarget<Drawable>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                setImage(resource);
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
                if (placeholder != null) {
                    setImageWithoutAnimation(placeholder);
                }
            }
        };

        Glide.with(getContext())
                .load(model == null ? R.drawable.placeholder : model)
                .error(R.drawable.placeholder)
                .override(expandedSize)
                .centerCrop()
                .into(glideTarget);
    }

    public void setImage(Drawable drawable) {
        Drawable current = innerImageView.getDrawable();
        if (current != null && drawable != null && current != drawable && innerImageView.getWidth() >= expandedSize / 2) {
            if (current.getConstantState() != null) {
                previousDrawable = current.getConstantState().newDrawable().mutate();
                previousDrawable.setBounds(0, 0, innerImageView.getWidth(), innerImageView.getHeight());
            } else {
                previousDrawable = current;
            }

            drawable = drawable.mutate();

            transitionProgress = 0f;
            animator.cancel();
            animator.start();
        } else {
            transitionProgress = 1f;
            previousDrawable = null;
            animator.cancel();
            if (drawable != null) {
                drawable = drawable.mutate();
            }
        }

        innerImageView.setImageDrawable(drawable);
    }

    public void setImage(Bitmap bitmap) {
        setImage(bitmap == null ? null : new BitmapDrawable(getResources(), bitmap));
    }

    public void setImageWithoutAnimation(Drawable drawable) {
        transitionProgress = 1f;
        previousDrawable = null;
        animator.cancel();
        if (drawable != null) {
            drawable = drawable.mutate();
        }
        innerImageView.setImageDrawable(drawable);
    }

    public void setImageWithoutAnimation(Bitmap bitmap) {
        setImageWithoutAnimation(bitmap == null ? null : new BitmapDrawable(getResources(), bitmap));
    }

    public void setRadius(float radius) {
        innerImageView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        innerImageView.setClipToOutline(radius > 0);
    }

    public void setScaleType(ImageView.ScaleType scaleType) {
        innerImageView.setScaleType(scaleType);
    }
}