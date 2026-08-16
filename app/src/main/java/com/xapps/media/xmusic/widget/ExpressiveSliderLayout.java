package com.xapps.media.xmusic.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.activity.BackEventCompat;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.xapps.media.xmusic.models.ViewDragHelper;
import com.xapps.media.xmusic.utils.XUtils;

import java.util.ArrayList;
import java.util.List;

public class ExpressiveSliderLayout extends FrameLayout {

    public interface SliderCallback {
        void onStateChanged(int state);
        void onSlide(float slideOffset);
        void onSwipe(boolean toRight);
    }

    public static final int STATE_COLLAPSED = 1;
    public static final int STATE_EXPANDED = 2;
    public static final int STATE_DRAGGING = 3;
    public static final int STATE_SETTLING = 4;
    public static final int STATE_HIDDEN = 5;

    private static final int DISMISS_THRESHOLD = 65;
    private static final int MAX_SWIPE_DISTANCE = 150;
    private static final int TOUCH_SLOP = 15;

    private ViewDragHelper dragHelper;
    private View sheetView;
    private final List<SliderCallback> sliderCallbacks = new ArrayList<>();
    private GradientDrawable internalBackground;
    private final float[] cornerRadii = new float[8];

    private SpringAnimation settleSpringAnim;
    private SpringAnimation settleXSpringAnim;

    private SpringAnimation jumpBoostAnim;
    private int currentJumpBoost = 0;
    private boolean hasTriggeredBoost = false;
    private int accumulatedFingerDrag = 0;
    private int currentXOffset = 0;

    private boolean layoutRequestedDuringAnimation = false;

    private float initialTouchX;
    private float initialTouchY;
    private boolean isAxisDecided = false;
    private boolean isHorizontalLocked = false;
    private boolean isVerticalLocked = false;

    private float rawHorizontalDrag = 0f;
    private float rawVerticalOverdrag = 0f;
    private int capturedTop;

    private int collapsedTop;
    private int expandedTop;
    private int hiddenTop;
    private int physicalBottom;
    private int currentState = STATE_HIDDEN;
    private int targetState = STATE_HIDDEN;
    private int peekHeight = 250;
    private int systemBottomInset = 0;

    private int floatingBottomMargin = 0;
    private int floatingSideMargin = 0;
    private float cornerRadiusTopLeft = 0f;
    private float cornerRadiusTopRight = 0f;
    private float cornerRadiusBottomLeft = 0f;
    private float cornerRadiusBottomRight = 0f;
    private int sheetBackgroundColor = Color.parseColor("#1C1B1F");
    private float lastExponentialFactor = -1f;

    private float sheetAlpha = 0f;
    private ValueAnimator alphaAnimator;
    private SpringAnimation scaleXSpringAnim;
    private SpringAnimation scaleYSpringAnim;

    private boolean isDraggable = true;
    private boolean isValidBack = false;
    private boolean isInitialLayout = true;

    private final OnBackPressedCallback backCallback =
            new OnBackPressedCallback(false) {
                @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                @Override
                public void handleOnBackStarted(@NonNull BackEventCompat backEvent) {
                    isValidBack = currentState == STATE_EXPANDED;
                    if (isValidBack) dispatchState(STATE_DRAGGING);
                }

                @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                @Override
                public void handleOnBackProgressed(@NonNull BackEventCompat backEvent) {
                    if (!isValidBack) return;
                    float progress = backEvent.getProgress();
                    float slideOffset = Math.max(0f, 1f - progress);
                    int range = collapsedTop - expandedTop;
                    int targetTop = collapsedTop - (int) (range * slideOffset);
                    int dy = targetTop - sheetView.getTop();
                    ViewCompat.offsetTopAndBottom(sheetView, dy);
                    applyExponentialMorph(slideOffset, sheetView.getTop());
                    for (SliderCallback callback : sliderCallbacks) {
                        callback.onSlide(slideOffset);
                    }
                }

                @Override
                public void handleOnBackPressed() {
                    if (!isValidBack && XUtils.predictiveBackSupported()) return;
                    setState(STATE_COLLAPSED);
                }

                @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                @Override
                public void handleOnBackCancelled() {
                    if (!isValidBack) return;
                    setState(STATE_EXPANDED);
                }
            };

    public ExpressiveSliderLayout(@NonNull Context context) {
        super(context);
        init();
    }

    public ExpressiveSliderLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        dragHelper = ViewDragHelper.create(this, 1.0f, new DragCallback());
        internalBackground = new GradientDrawable();
        internalBackground.setColor(sheetBackgroundColor);
        ViewCompat.setElevation(this, 0f);
        setClickable(false);

        ViewCompat.setOnApplyWindowInsetsListener(
                this,
                (v, insets) -> {
                    int newBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
                    if (systemBottomInset != newBottom) {
                        systemBottomInset = newBottom;
                        requestLayout();
                    }
                    return insets;
                });

        FloatValueHolder boostHolder = new FloatValueHolder(0f);
        jumpBoostAnim = new SpringAnimation(boostHolder);
        SpringForce boostForce = new SpringForce();
        boostForce.setStiffness(SpringForce.STIFFNESS_MEDIUM);
        boostForce.setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY);
        jumpBoostAnim.setSpring(boostForce);

        jumpBoostAnim.addUpdateListener(
                (animation, value, velocity) -> {
                    if (sheetView != null
                            && dragHelper.getViewDragState() == ViewDragHelper.STATE_DRAGGING) {
                        int newBoost = Math.round(value);
                        int dyBoost = newBoost - currentJumpBoost;
                        currentJumpBoost = newBoost;
                        int currentTop = sheetView.getTop();
                        if (currentTop + dyBoost > physicalBottom) {
                            dyBoost = physicalBottom - currentTop;
                        }
                        if (dyBoost != 0) {
                            ViewCompat.offsetTopAndBottom(sheetView, dyBoost);
                            float progress = calculateSlideOffset(sheetView.getTop());
                            applyExponentialMorph(progress, sheetView.getTop());
                            for (SliderCallback callback : sliderCallbacks) {
                                callback.onSlide(progress);
                            }
                        }
                    }
                });

        jumpBoostAnim.addEndListener((animation, canceled, value, velocity) -> checkQueuedLayout());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (getChildCount() > 0) {
            sheetView = getChildAt(0);
            sheetView.setBackground(internalBackground);
            sheetView.setClipToOutline(true);
            sheetView.setClickable(true);

            scaleXSpringAnim = new SpringAnimation(sheetView, DynamicAnimation.SCALE_X);
            SpringForce forceX = new SpringForce(1f);
            forceX.setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
            forceX.setStiffness(SpringForce.STIFFNESS_LOW);
            scaleXSpringAnim.setSpring(forceX);

            scaleYSpringAnim = new SpringAnimation(sheetView, DynamicAnimation.SCALE_Y);
            SpringForce forceY = new SpringForce(1f);
            forceY.setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
            forceY.setStiffness(SpringForce.STIFFNESS_LOW);
            scaleYSpringAnim.setSpring(forceY);
        }
    }

    public void setupPredictiveBack(ComponentActivity activity) {
        activity.getOnBackPressedDispatcher().addCallback(activity, backCallback);
    }

    public OnBackPressedCallback getPredictiveBackCallback() {
        return backCallback;
    }

    public void addSliderCallback(SliderCallback callback) {
        if (!sliderCallbacks.contains(callback)) {
            sliderCallbacks.add(callback);
        }
    }

    public void removeSliderCallback(SliderCallback callback) {
        sliderCallbacks.remove(callback);
    }

    public void setDraggable(boolean draggable) {
        this.isDraggable = draggable;
    }

    public boolean isDraggable() {
        return isDraggable;
    }

    public void setPeekHeight(int peekHeight) {
        this.peekHeight = peekHeight;
        requestLayout();
    }

    public int getPeekHeight() {
        return this.peekHeight;
    }

    public void setSheetBackgroundColor(int color) {
        this.sheetBackgroundColor = color;
        if (internalBackground != null) {
            internalBackground.setColor(color);
        }
    }

    public void setFloatingMargins(int sideMargin, int bottomMargin) {
        this.floatingSideMargin = sideMargin;
        this.floatingBottomMargin = bottomMargin;
        requestLayout();
    }

    public int getFloatingMargin(@NonNull String type) {
        if ("side".equalsIgnoreCase(type)) {
            return floatingSideMargin;
        } else if ("bottom".equalsIgnoreCase(type)) {
            return floatingBottomMargin;
        }
        return 0;
    }

    public void setFloatingCornerRadii(float tl, float tr, float bl, float br) {
        this.cornerRadiusTopLeft = tl;
        this.cornerRadiusTopRight = tr;
        this.cornerRadiusBottomLeft = bl;
        this.cornerRadiusBottomRight = br;
        if (currentState == STATE_COLLAPSED) {
            applyExponentialMorph(0f, sheetView.getTop());
        }
    }

    public void setSlideOffset(float offset) {
        if (sheetView == null || !ViewCompat.isLaidOut(this)) return;
        float safeOffset = Math.max(0f, Math.min(1f, offset));
        int range = collapsedTop - expandedTop;
        int targetTop = collapsedTop - (int) (range * safeOffset);
        int dy = targetTop - sheetView.getTop();
        if (dy != 0) {
            ViewCompat.offsetTopAndBottom(sheetView, dy);
            applyExponentialMorph(safeOffset, sheetView.getTop());
            for (SliderCallback callback : sliderCallbacks) {
                callback.onSlide(safeOffset);
            }
        }
    }

    public float getSlideOffset() {
        if (sheetView == null || !ViewCompat.isLaidOut(this)) return 0f;
        return calculateSlideOffset(sheetView.getTop());
    }

    public void setState(int state) {
        if (this.currentState == state) return;
        this.targetState = state;
        if (sheetView == null || !ViewCompat.isLaidOut(this) || getHeight() == 0) {
            this.currentState = state;
            if (state == STATE_HIDDEN) {
                sheetAlpha = 0f;
                if (sheetView != null) {
                    sheetView.setAlpha(0f);
                    sheetView.setScaleX(0.8f);
                    sheetView.setScaleY(0.8f);
                    sheetView.setVisibility(View.INVISIBLE);
                    currentXOffset = 0;
                    sheetView.setTranslationX(0f);
                }
            } else {
                sheetAlpha = 1f;
                if (sheetView != null) {
                    sheetView.setAlpha(1f);
                    sheetView.setScaleX(1f);
                    sheetView.setScaleY(1f);
                    sheetView.setVisibility(View.VISIBLE);
                }
            }
            return;
        }
        if (state == STATE_HIDDEN) {
            dispatchState(STATE_SETTLING);
            settleAlpha(0f);
            settleWithSpring(collapsedTop, STATE_HIDDEN, 0f);
        } else {
            if (currentState == STATE_HIDDEN) {
                sheetView.setVisibility(View.VISIBLE);
                sheetView.setScaleX(0.8f);
                sheetView.setScaleY(0.8f);
                settleAlpha(1f);
                if (scaleXSpringAnim != null) scaleXSpringAnim.animateToFinalPosition(1f);
                if (scaleYSpringAnim != null) scaleYSpringAnim.animateToFinalPosition(1f);
            }
            int targetTop;
            if (state == STATE_EXPANDED) {
                targetTop = expandedTop;
            } else if (state == STATE_COLLAPSED) {
                targetTop = collapsedTop;
            } else {
                return;
            }
            dispatchState(STATE_SETTLING);
            settleWithSpring(targetTop, state, 0f);
        }
    }

    public int getState() {
        return this.currentState;
    }

    private void settleAlpha(float targetAlpha) {
        if (alphaAnimator != null && alphaAnimator.isRunning()) {
            alphaAnimator.cancel();
        }
        alphaAnimator = ValueAnimator.ofFloat(sheetAlpha, targetAlpha);
        alphaAnimator.setDuration(200);
        alphaAnimator.addUpdateListener(animation -> {
            sheetAlpha = (float) animation.getAnimatedValue();
            if (sheetView != null) {
                sheetView.setAlpha(sheetAlpha);
                if (sheetAlpha <= 0.01f && targetState == STATE_HIDDEN) {
                    sheetView.setVisibility(View.INVISIBLE);
                } else {
                    sheetView.setVisibility(View.VISIBLE);
                }
            }
        });
        alphaAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (sheetAlpha <= 0.01f && targetState == STATE_HIDDEN) {
                    dispatchState(STATE_HIDDEN);
                    if (sheetView != null) {
                        sheetView.setVisibility(View.INVISIBLE);
                        sheetView.setScaleX(0.8f);
                        sheetView.setScaleY(0.8f);
                        int dy = collapsedTop - sheetView.getTop();
                        if (dy != 0) {
                            ViewCompat.offsetTopAndBottom(sheetView, dy);
                        }
                    }
                }
                checkQueuedLayout();
            }
        });
        alphaAnimator.start();
    }

    private void settleWithSpring(int targetTop, int state, float initialVelocity) {
        this.targetState = state;
        if (settleSpringAnim != null && settleSpringAnim.isRunning()) {
            settleSpringAnim.cancel();
        }
        FloatValueHolder holder = new FloatValueHolder(sheetView.getTop());
        settleSpringAnim = new SpringAnimation(holder);
        settleSpringAnim.setStartVelocity(initialVelocity);
        SpringForce force = new SpringForce(targetTop);
        force.setDampingRatio((sheetView.getTop() > targetTop) ? SpringForce.DAMPING_RATIO_NO_BOUNCY : SpringForce.DAMPING_RATIO_LOW_BOUNCY);
        force.setStiffness(500f);
        settleSpringAnim.setSpring(force);
        settleSpringAnim.addUpdateListener((animation, value, velocity) -> {
            int beforeTop = sheetView.getTop();
            int rounded = Math.round(value);
            int dy = rounded - beforeTop;
            ViewCompat.offsetTopAndBottom(sheetView, dy);
            int afterTop = sheetView.getTop();
            float progress = calculateSlideOffset(afterTop);
            applyExponentialMorph(progress, afterTop);
            for (SliderCallback callback : sliderCallbacks) {
                callback.onSlide(progress);
            }
        });
        settleSpringAnim.addEndListener(
                (animation, canceled, value, velocity) -> {
                    if (!canceled) {
                        int exactTop = hiddenTop;
                        if (this.targetState == STATE_EXPANDED) exactTop = expandedTop;
                        else if (this.targetState == STATE_COLLAPSED) exactTop = collapsedTop;
                        if (this.targetState == STATE_HIDDEN) {
                            sheetAlpha = 0f;
                            if (sheetView != null) {
                                sheetView.setAlpha(0f);
                                sheetView.setScaleX(0.8f);
                                sheetView.setScaleY(0.8f);
                                sheetView.setVisibility(View.INVISIBLE);
                                int dy = collapsedTop - sheetView.getTop();
                                if (dy != 0) {
                                    ViewCompat.offsetTopAndBottom(sheetView, dy);
                                }
                            }
                        } else {
                            if (exactTop >= 0 && physicalBottom > 0 && exactTop <= physicalBottom) {
                                float progress = calculateSlideOffset(exactTop);
                                applyExponentialMorph(progress, exactTop);
                            }
                        }
                        dispatchState(this.targetState);
                    }
                    checkQueuedLayout();
                });
        settleSpringAnim.start();
    }

    private void settleXWithSpring(int targetXOffset, float initialVelocity) {
        if (settleXSpringAnim != null && settleXSpringAnim.isRunning()) {
            settleXSpringAnim.cancel();
        }
        FloatValueHolder holder = new FloatValueHolder((float) currentXOffset);
        settleXSpringAnim = new SpringAnimation(holder);
        settleXSpringAnim.setStartVelocity(initialVelocity);
        SpringForce force = new SpringForce((float) targetXOffset);
        force.setDampingRatio(0.95f);
        force.setStiffness(800f);
        settleXSpringAnim.setSpring(force);
        settleXSpringAnim.addUpdateListener((animation, value, velocity) -> {
            currentXOffset = Math.round(value);
            applyExponentialMorph(calculateSlideOffset(sheetView.getTop()), sheetView.getTop());
        });
        settleXSpringAnim.addEndListener((animation, canceled, value, velocity) -> checkQueuedLayout());
        settleXSpringAnim.start();
    }

    private boolean isAnimating() {
        return (settleSpringAnim != null && settleSpringAnim.isRunning())
                || (settleXSpringAnim != null && settleXSpringAnim.isRunning())
                || (jumpBoostAnim != null && jumpBoostAnim.isRunning());
    }

    private void checkQueuedLayout() {
        if (layoutRequestedDuringAnimation && !isAnimating()) {
            layoutRequestedDuringAnimation = false;
            super.requestLayout();
        }
    }

    private float calculateSlideOffset(int top) {
        if (top <= collapsedTop) {
            float range = collapsedTop - expandedTop;
            return range > 0 ? (float) (collapsedTop - top) / range : 0f;
        } else {
            float hideRange = physicalBottom - collapsedTop;
            return hideRange > 0 ? ((float) (collapsedTop - top) / hideRange) : 0f;
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int previousTop = sheetView != null ? sheetView.getTop() : 0;
        if (currentState == STATE_HIDDEN && previousTop == 0 && getHeight() > 0) {
            previousTop = getHeight();
        }
        super.onLayout(changed, left, top, right, bottom);
        if (sheetView != null && getHeight() > 0) {
            int actualPeekHeight = peekHeight + (floatingBottomMargin == 0 ? systemBottomInset : 0);
            int actualBottomMargin =
                    floatingBottomMargin > 0 ? floatingBottomMargin + systemBottomInset : 0;
            expandedTop = 0;
            physicalBottom = getHeight();
            collapsedTop = physicalBottom - actualPeekHeight - actualBottomMargin;
            hiddenTop = collapsedTop;
            if (isInitialLayout) {
                isInitialLayout = false;
                if (currentState == STATE_HIDDEN) {
                    previousTop = hiddenTop;
                    sheetAlpha = 0f;
                    sheetView.setScaleX(0.8f);
                    sheetView.setScaleY(0.8f);
                    sheetView.setVisibility(View.INVISIBLE);
                } else if (currentState == STATE_COLLAPSED) {
                    previousTop = collapsedTop;
                    sheetAlpha = 1f;
                    sheetView.setScaleX(1f);
                    sheetView.setScaleY(1f);
                    sheetView.setVisibility(View.VISIBLE);
                } else if (currentState == STATE_EXPANDED) {
                    previousTop = expandedTop;
                    sheetAlpha = 1f;
                    sheetView.setScaleX(1f);
                    sheetView.setScaleY(1f);
                    sheetView.setVisibility(View.VISIBLE);
                }
                sheetView.offsetTopAndBottom(previousTop - sheetView.getTop());
                sheetView.setAlpha(sheetAlpha);
            }
            if (currentState == STATE_SETTLING
                    && settleSpringAnim != null
                    && settleSpringAnim.isRunning()) {
                int dynamicTargetTop = collapsedTop;
                if (targetState == STATE_EXPANDED) {
                    dynamicTargetTop = expandedTop;
                } else if (targetState == STATE_HIDDEN) {
                    dynamicTargetTop = hiddenTop;
                }
                settleSpringAnim.getSpring().setFinalPosition(dynamicTargetTop);
            }
            if (currentState == STATE_DRAGGING || currentState == STATE_SETTLING) {
                float progress = calculateSlideOffset(previousTop);
                applyExponentialMorph(progress, previousTop);
            } else {
                int targetTop;
                if (currentState == STATE_EXPANDED) targetTop = expandedTop;
                else if (currentState == STATE_HIDDEN) targetTop = hiddenTop;
                else targetTop = collapsedTop;
                float progress = calculateSlideOffset(targetTop);
                applyExponentialMorph(progress, targetTop);
            }
        }
    }

    private void processAxisIntent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            initialTouchX = ev.getX();
            initialTouchY = ev.getY();
            isAxisDecided = false;
            isHorizontalLocked = false;
            isVerticalLocked = false;
        } else if (action == MotionEvent.ACTION_MOVE && !isAxisDecided) {
            float dx = Math.abs(ev.getX() - initialTouchX);
            float dy = Math.abs(ev.getY() - initialTouchY);
            if (dx > TOUCH_SLOP || dy > TOUCH_SLOP) {
                isAxisDecided = true;
                if (dx > dy && currentState == STATE_COLLAPSED) {
                    isHorizontalLocked = true;
                } else {
                    isVerticalLocked = true;
                }
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isDraggable || currentState == STATE_HIDDEN || sheetAlpha < 0.1f) return false;
        processAxisIntent(ev);
        boolean intercepted = dragHelper.shouldInterceptTouchEvent(ev);
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (dragHelper.isViewUnder(sheetView, (int) ev.getX(), (int) ev.getY())) {
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
            }
        }
        return intercepted;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isDraggable || currentState == STATE_HIDDEN || sheetAlpha < 0.1f) return false;
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (!dragHelper.isViewUnder(sheetView, (int) ev.getX(), (int) ev.getY())) {
                return false;
            }
        }
        processAxisIntent(ev);
        dragHelper.processTouchEvent(ev);
        return true;
    }

    private void dispatchState(int state) {
        if (currentState != state) {
            currentState = state;
            for (SliderCallback callback : sliderCallbacks) {
                callback.onStateChanged(state);
            }
        }
    }

    private void applyExponentialMorph(float slideOffset, int currentTop) {
        if (sheetView == null) return;
        if (alphaAnimator == null || !alphaAnimator.isRunning()) {
            sheetAlpha = (currentState == STATE_HIDDEN) ? 0f : 1f;
        }
        sheetView.setAlpha(sheetAlpha);
        if (sheetAlpha <= 0.01f) {
            sheetView.setVisibility(View.INVISIBLE);
        } else {
            sheetView.setVisibility(View.VISIBLE);
        }
        float safeOffset = Math.max(0f, Math.min(1f, slideOffset));
        float invertedOffset = 1.0f - safeOffset;
        float exponentialFactor = (float) Math.pow(invertedOffset, 3.0f);
        if (internalBackground != null) {
            if (Math.abs(lastExponentialFactor - exponentialFactor) > 0.001f) {
                lastExponentialFactor = exponentialFactor;
                float currentTl = cornerRadiusTopLeft * (float) Math.pow(exponentialFactor, 0.1f);
                float currentTr = cornerRadiusTopRight * (float) Math.pow(exponentialFactor, 0.1f);
                float currentBl = cornerRadiusBottomLeft * (float) Math.pow(exponentialFactor, 0.1f);
                float currentBr = cornerRadiusBottomRight * (float) Math.pow(exponentialFactor, 0.1f);
                cornerRadii[0] = currentTl;
                cornerRadii[1] = currentTl;
                cornerRadii[2] = currentTr;
                cornerRadii[3] = currentTr;
                cornerRadii[4] = currentBr;
                cornerRadii[5] = currentBr;
                cornerRadii[6] = currentBl;
                cornerRadii[7] = currentBl;
                internalBackground.setCornerRadii(cornerRadii);
            }
        }
        int parentWidth = getWidth();
        int parentHeight = getHeight();
        int baseLeft = (int) (floatingSideMargin * exponentialFactor);
        int currentRight = parentWidth - baseLeft;
        int currentBottom;
        int actualBottomMargin =
                floatingBottomMargin > 0 ? floatingBottomMargin + systemBottomInset : 0;
        int actualPeekHeight = peekHeight + (floatingBottomMargin == 0 ? systemBottomInset : 0);
        if (currentTop < expandedTop) {
            currentBottom = currentTop + (parentHeight - expandedTop);
        } else if (currentTop > collapsedTop) {
            currentBottom = currentTop + actualPeekHeight;
        } else {
            currentBottom = parentHeight - (int) (actualBottomMargin * exponentialFactor);
        }
        sheetView.layout(baseLeft, currentTop, currentRight, currentBottom);
        sheetView.setTranslationX(currentXOffset);
    }

    private class DragCallback extends ViewDragHelper.Callback {

        @Override
        public boolean tryCaptureView(@NonNull View child, int pointerId) {
            return isDraggable && child == sheetView;
        }

        @Override
        public void onViewCaptured(@NonNull View capturedChild, int activePointerId) {
            capturedTop = capturedChild.getTop();
            float max = MAX_SWIPE_DISTANCE;
            float tension = max * 0.5f;
            float safeCurrent = Math.min(Math.abs(currentXOffset), max * 0.999f);
            rawHorizontalDrag = (float) (-tension * Math.log(1.0 - (safeCurrent / max)));
            if (currentXOffset < 0) rawHorizontalDrag = -rawHorizontalDrag;
            rawVerticalOverdrag = Math.max(0, capturedTop - collapsedTop);
        }

        @Override
        public void onViewPositionChanged(
                @NonNull View changedView, int left, int top, int dx, int dy) {
            currentXOffset += dx;
            float progress = calculateSlideOffset(top);
            applyExponentialMorph(progress, sheetView.getTop());
            for (SliderCallback callback : sliderCallbacks) {
                callback.onSlide(progress);
            }
        }

        @Override
        public void onViewDragStateChanged(int state) {
            if (state == ViewDragHelper.STATE_DRAGGING) {
                dispatchState(STATE_DRAGGING);
                if (settleSpringAnim != null && settleSpringAnim.isRunning()) {
                    settleSpringAnim.cancel();
                }
                if (settleXSpringAnim != null && settleXSpringAnim.isRunning()) {
                    settleXSpringAnim.cancel();
                }
            }
        }

        @Override
        public void onViewReleased(@NonNull View releasedChild, float xvel, float yvel) {
            float activeXvel = isHorizontalLocked ? xvel : 0f;
            float activeYvel = isVerticalLocked ? yvel : 0f;
            if (isHorizontalLocked) {
                boolean swipedRight = currentXOffset > 0;
                if (Math.abs(currentXOffset) > (MAX_SWIPE_DISTANCE * 2.0f / 3.0f)) {
                    for (SliderCallback callback : sliderCallbacks) {
                        callback.onSwipe(swipedRight);
                    }
                }
            }
            settleXWithSpring(0, activeXvel);
            if (jumpBoostAnim != null && jumpBoostAnim.isRunning()) {
                jumpBoostAnim.cancel();
            }
            hasTriggeredBoost = false;
            currentJumpBoost = 0;
            int targetTop;
            int targetStateLocal;
            boolean passedThreshold = accumulatedFingerDrag >= DISMISS_THRESHOLD;
            accumulatedFingerDrag = 0;
            if (activeYvel < -500) {
                targetTop = expandedTop;
                targetStateLocal = STATE_EXPANDED;
            } else if (activeYvel > 500) {
                if (releasedChild.getTop() >= collapsedTop - 100) {
                    targetTop = physicalBottom;
                    targetStateLocal = STATE_HIDDEN;
                } else {
                    targetTop = collapsedTop;
                    targetStateLocal = STATE_COLLAPSED;
                }
            } else {
                if (releasedChild.getTop() > collapsedTop) {
                    if (passedThreshold) {
                        targetTop = physicalBottom;
                        targetStateLocal = STATE_HIDDEN;
                    } else {
                        targetTop = collapsedTop;
                        targetStateLocal = STATE_COLLAPSED;
                    }
                } else {
                    if (releasedChild.getTop() < (collapsedTop + expandedTop) / 2) {
                        targetTop = expandedTop;
                        targetStateLocal = STATE_EXPANDED;
                    } else {
                        targetTop = collapsedTop;
                        targetStateLocal = STATE_COLLAPSED;
                    }
                }
            }
            dispatchState(STATE_SETTLING);
            float maxVelocity = 1000f;
            float clampedVelocity = Math.max(-maxVelocity, Math.min(activeYvel, maxVelocity));
            settleWithSpring(targetTop, targetStateLocal, clampedVelocity);
        }

        @Override
        public int clampViewPositionVertical(@NonNull View child, int top, int dy) {
            if (!isAxisDecided || isHorizontalLocked) {
                return child.getTop();
            }
            int currentTop = child.getTop();
            if (top > collapsedTop) {
                if (dy < 0) {
                    if (hasTriggeredBoost) {
                        hasTriggeredBoost = false;
                        jumpBoostAnim.cancel();
                        currentJumpBoost = 0;
                    }
                    rawVerticalOverdrag += dy;
                    if (rawVerticalOverdrag < 0) rawVerticalOverdrag = 0;
                } else {
                    if (currentTop <= collapsedTop) {
                        rawVerticalOverdrag = top - collapsedTop;
                    } else {
                        rawVerticalOverdrag += dy;
                    }
                }
                accumulatedFingerDrag = Math.round(rawVerticalOverdrag);
                float maxV = DISMISS_THRESHOLD * 1.5f;
                float clampedV = maxV * (1.0f - (float) Math.exp(-rawVerticalOverdrag / (DISMISS_THRESHOLD * 0.8f)));
                int targetTopLocal = collapsedTop + Math.round(clampedV);
                if (rawVerticalOverdrag < DISMISS_THRESHOLD) {
                    if (hasTriggeredBoost) {
                        hasTriggeredBoost = false;
                        jumpBoostAnim.cancel();
                        currentJumpBoost = 0;
                    }
                    return Math.min(targetTopLocal, physicalBottom);
                } else {
                    if (!hasTriggeredBoost) {
                        hasTriggeredBoost = true;
                        currentJumpBoost = 0;
                        jumpBoostAnim.setStartValue(0f);
                        int overdrag = currentTop - collapsedTop;
                        int catchUpAmount = Math.max(0, accumulatedFingerDrag - overdrag);
                        jumpBoostAnim.getSpring().setFinalPosition((float) catchUpAmount);
                        jumpBoostAnim.start();
                    }
                    return Math.min(targetTopLocal, physicalBottom);
                }
            }
            rawVerticalOverdrag = 0f;
            accumulatedFingerDrag = 0;
            if (hasTriggeredBoost) {
                hasTriggeredBoost = false;
                jumpBoostAnim.cancel();
                currentJumpBoost = 0;
            }
            return Math.max(expandedTop, Math.min(top, collapsedTop));
        }

        @Override
        public int clampViewPositionHorizontal(@NonNull View child, int left, int dx) {
            if (!isAxisDecided || isVerticalLocked || !isHorizontalLocked) {
                return child.getLeft();
            }
            rawHorizontalDrag += dx;
            float max = MAX_SWIPE_DISTANCE;
            float tension = max * 0.5f;
            float absRaw = Math.abs(rawHorizontalDrag);
            float clampedOffset = max * (1.0f - (float) Math.exp(-absRaw / tension));
            if (rawHorizontalDrag < 0) {
                clampedOffset = -clampedOffset;
            }
            int allowedDx = Math.round(clampedOffset) - currentXOffset;
            return child.getLeft() + allowedDx;
        }

        @Override
        public int getViewHorizontalDragRange(@NonNull View child) {
            return MAX_SWIPE_DISTANCE * 2;
        }

        @Override
        public int getViewVerticalDragRange(@NonNull View child) {
            return physicalBottom - expandedTop;
        }
    }

    @Override
    public void requestLayout() {
        if (isAnimating()) {
            layoutRequestedDuringAnimation = true;
        } else {
            super.requestLayout();
        }
    }
}