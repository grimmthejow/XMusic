package com.xapps.media.xmusic.widget;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.res.ResourcesCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.xapps.media.xmusic.R;
import com.xapps.media.xmusic.utils.MaterialColorUtils;

import com.xapps.media.xmusic.utils.XUtils;
import java.util.ArrayList;
import java.util.List;

public class ExpressiveTabs extends HorizontalScrollView {

    public static final int ACTIVE_BG_COLOR = MaterialColorUtils.colorPrimaryContainer;
    public static final int INACTIVE_BG_COLOR = Color.TRANSPARENT;
    public static final int ACTIVE_TEXT_COLOR = MaterialColorUtils.colorOnPrimaryContainer;
    public static final int INACTIVE_TEXT_COLOR = MaterialColorUtils.colorOutline;

    public interface OnTabSelectedListener {
        void onTabSelected(int position, String text);
    }

    private OnTabSelectedListener listener;
    private int selectedIndex = -1;
    private final List<ExpressiveTab> tabs = new ArrayList<>();
    private final LinearLayout container;
    private static Typeface cachedFont;

    public ExpressiveTabs(Context context) {
        this(context, null);
    }

    public ExpressiveTabs(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExpressiveTabs(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipToPadding(false);

        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setPadding(16, 16, 16, 16);
        container.setClipToPadding(false);

        addView(container, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    public void addTab(String text) {
        ExpressiveTab tab = new ExpressiveTab(getContext());
        tab.setText(text);
        int position = tabs.size();

        tab.setOnClickListener(v -> setSelectedIndex(position));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(12, 0, 12, 0);
        container.addView(tab, params);
        tabs.add(tab);

        if (selectedIndex == -1) {
            selectedIndex = 0;
            tab.updateStateInstant(true);
        } else {
            tab.updateStateInstant(false);
        }
    }

    public void setSelectedIndex(int index) {
        if (index < 0 || index >= tabs.size() || index == selectedIndex) return;

        if (selectedIndex != -1) {
            tabs.get(selectedIndex).animateToInactive();
        }

        selectedIndex = index;
        ExpressiveTab selectedTab = tabs.get(selectedIndex);
        selectedTab.animateToActive();

        scrollToTab(selectedTab);

        if (listener != null) {
            listener.onTabSelected(selectedIndex, selectedTab.getText().toString());
        }
    }

    private void scrollToTab(ExpressiveTab tab) {
        post(() -> {
            int targetScrollX = tab.getLeft() - container.getPaddingLeft();
            int currentScrollX = getScrollX();

            if (targetScrollX == currentScrollX) return;

            int direction = targetScrollX > currentScrollX ? 1 : -1;

            if (canScrollHorizontally(direction)) {
                smoothScrollTo(targetScrollX, 0);
            }
        });
    }

    public void setOnTabSelectedListener(OnTabSelectedListener listener) {
        this.listener = listener;
    }

    private static class ExpressiveTab extends AppCompatTextView {
        private float currentGrad = 0f;
        private SpringAnimation scaleXAnim;
        private SpringAnimation scaleYAnim;
        private SpringAnimation gradAnim;
        private ValueAnimator bgColorAnim;
        private ValueAnimator textColorAnim;
        
        private final int sideMargin;
        private final int verticalMargin;
        
        private int currentBgColor = INACTIVE_BG_COLOR;
        private int currentTextColor = INACTIVE_TEXT_COLOR;

        private static final FloatPropertyCompat<ExpressiveTab> GRAD_PROPERTY =
                new FloatPropertyCompat<>("grad") {
                    @Override
                    public float getValue(ExpressiveTab tab) {
                        return tab.currentGrad;
                    }

                    @Override
                    public void setValue(ExpressiveTab tab, float value) {
                        tab.currentGrad = value;
                        tab.setFontVariationSettings("'wght' " + value);
                    }
                };

        public ExpressiveTab(Context context) {
            super(context);
            sideMargin = XUtils.convertToPx(context, 12f);
            verticalMargin = XUtils.convertToPx(context, 8f);
            setupView();
            setupAnimations();
        }

        @SuppressLint("ClickableViewAccessibility")
        private void setupView() {
            if (cachedFont == null) {
                cachedFont = ResourcesCompat.getFont(getContext(), R.font.gsans_flex_full);
            }
            if (cachedFont != null) {
                setTypeface(cachedFont);
            }
            
            setFontVariationSettings("'wght' 500");

            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setColor(currentBgColor);
            drawable.setCornerRadius(32f);
            setBackground(drawable);
            
            setPadding(sideMargin, verticalMargin, sideMargin, verticalMargin);
            setTextColor(currentTextColor);
            setTextSize(16f);
            setGravity(Gravity.CENTER);
            setClickable(true);
            setFocusable(true);

            setOnTouchListener((v, event) -> {
                ExpressiveTabs parent = (ExpressiveTabs) getParent().getParent();
                boolean isActive = parent.selectedIndex == parent.tabs.indexOf(this);

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        float pressScale = isActive ? 0.98f : 0.94f;
                        scaleXAnim.animateToFinalPosition(pressScale);
                        scaleYAnim.animateToFinalPosition(pressScale);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        float targetScale = isActive ? 1.05f : 1.0f;
                        scaleXAnim.animateToFinalPosition(targetScale);
                        scaleYAnim.animateToFinalPosition(targetScale);
                        break;
                }
                return false;
            });
        }

        private void setupAnimations() {
            scaleXAnim = createSpring(DynamicAnimation.SCALE_X, 1.0f);
            scaleYAnim = createSpring(DynamicAnimation.SCALE_Y, 1.0f);
            gradAnim = createSpring(GRAD_PROPERTY, 0f);
        }

        private <K> SpringAnimation createSpring(FloatPropertyCompat<K> property, float finalPos) {
            SpringAnimation anim = new SpringAnimation((K) this, property, finalPos);
            SpringForce force = new SpringForce(finalPos);
            force.setStiffness(SpringForce.STIFFNESS_MEDIUM);
            force.setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY);
            anim.setSpring(force);
            return anim;
        }

        private void animateColors(int targetBg, int targetText) {
            if (bgColorAnim != null && bgColorAnim.isRunning()) bgColorAnim.cancel();
            if (textColorAnim != null && textColorAnim.isRunning()) textColorAnim.cancel();

            bgColorAnim = ValueAnimator.ofArgb(currentBgColor, targetBg);
            bgColorAnim.setDuration(200);
            bgColorAnim.addUpdateListener(anim -> {
                currentBgColor = (int) anim.getAnimatedValue();
                GradientDrawable bg = (GradientDrawable) getBackground();
                bg.setColor(currentBgColor);
            });
            bgColorAnim.start();

            textColorAnim = ValueAnimator.ofArgb(currentTextColor, targetText);
            textColorAnim.setDuration(200);
            textColorAnim.addUpdateListener(anim -> {
                currentTextColor = (int) anim.getAnimatedValue();
                setTextColor(currentTextColor);
            });
            textColorAnim.start();
        }

        public void animateToActive() {
            animateColors(ACTIVE_BG_COLOR, ACTIVE_TEXT_COLOR);
            scaleXAnim.animateToFinalPosition(1.05f);
            scaleYAnim.animateToFinalPosition(1.05f);
            gradAnim.animateToFinalPosition(600f);
        }

        public void animateToInactive() {
            animateColors(INACTIVE_BG_COLOR, INACTIVE_TEXT_COLOR);
            scaleXAnim.animateToFinalPosition(1.0f);
            scaleYAnim.animateToFinalPosition(1.0f);
            gradAnim.animateToFinalPosition(400f);
        }

        public void updateStateInstant(boolean active) {
            if (bgColorAnim != null && bgColorAnim.isRunning()) bgColorAnim.cancel();
            if (textColorAnim != null && textColorAnim.isRunning()) textColorAnim.cancel();

            currentBgColor = active ? ACTIVE_BG_COLOR : INACTIVE_BG_COLOR;
            currentTextColor = active ? ACTIVE_TEXT_COLOR : INACTIVE_TEXT_COLOR;

            GradientDrawable bg = (GradientDrawable) getBackground();
            bg.setColor(currentBgColor);
            setTextColor(currentTextColor);

            float scale = active ? 1.05f : 1.0f;
            setScaleX(scale);
            setScaleY(scale);

            float weight = active ? 600f : 400f;
            currentGrad = weight;
            setFontVariationSettings("'wght' " + weight);

            if (scaleXAnim != null) scaleXAnim.setStartValue(scale);
            if (scaleYAnim != null) scaleYAnim.setStartValue(scale);
            if (gradAnim != null) gradAnim.setStartValue(weight);
        }
    }
}
