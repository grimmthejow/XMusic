package com.xapps.media.xmusic.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.transition.TransitionManager;
import com.google.android.material.transition.MaterialSharedAxis;

public class SmallToggleView extends FrameLayout {

    private AppCompatImageView mainIcon;
    private AppCompatImageView holdIcon;
    
    private boolean pressedInside = false;
    private boolean isEnabled = true;
    private boolean isHolding = false;
    
    private int currentDelay = 500;
    private int holdIconResId;
    private OnHoldListener holdListener;

    private final Handler holdHandler = new Handler(Looper.getMainLooper());
    private final Runnable holdRunnable = new Runnable() {
        @Override
        public void run() {
            if (!pressedInside) return;
            
            if (!isHolding) {
                isHolding = true;
                applyTransition(false);
                mainIcon.setVisibility(View.INVISIBLE);
                holdIcon.setImageResource(holdIconResId);
                holdIcon.setVisibility(View.VISIBLE);
            }
            
            if (holdListener != null) {
                holdListener.onHold();
            }
            
            currentDelay = Math.max(100, currentDelay - 50);
            holdHandler.postDelayed(this, currentDelay);
        }
    };

    public SmallToggleView(Context c) {
        super(c);
        init(c, null);
    }

    public SmallToggleView(Context c, AttributeSet a) {
        super(c, a);
        init(c, a);
    }

    public SmallToggleView(Context c, AttributeSet a, int s) {
        super(c, a, s);
        init(c, a);
    }

    private void init(Context context, AttributeSet attrs) {
        setClickable(true);
        setClipToOutline(true);
        
        mainIcon = new AppCompatImageView(context);
    
   	 if (attrs != null) {
       	 android.content.res.TypedArray a = context.obtainStyledAttributes(attrs, new int[]{android.R.attr.src});
       	 int srcResId = a.getResourceId(0, 0);
       	 if (srcResId != 0) {
          	  mainIcon.setImageResource(srcResId);
       	 }
      	  a.recycle();
   	 }
    
  	  addView(mainIcon, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        
        holdIcon = new AppCompatImageView(context);
        holdIcon.setVisibility(View.INVISIBLE);
        addView(holdIcon, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setOnHoldListener(int holdIconResId, OnHoldListener listener) {
        this.holdIconResId = holdIconResId;
        this.holdListener = listener;
    }
    
    public void setIconColorFilter(int color) {
   	 mainIcon.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
   	 if (holdIcon != null) {
      	  holdIcon.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
   	 }
	}

    @Override
	public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressedInside = true;
                animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start();
                if (holdListener != null) {
                    currentDelay = 500;
                    holdHandler.postDelayed(holdRunnable, currentDelay);
                }
                return true;
        
            case MotionEvent.ACTION_UP:
                boolean wasHolding = isHolding;
                handleRelease();
                if (pressedInside && isInside(e) && isEnabled && !wasHolding) {
                    performClick();
                }
                pressedInside = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                handleRelease();
                pressedInside = false;
                return true;
        }
        return super.onTouchEvent(e);
	}

	private void handleRelease() {
    	holdHandler.removeCallbacks(holdRunnable);
   	 animate().scaleX(1f).scaleY(1f).setDuration(80).start();
   	 if (isHolding) {
      	  isHolding = false;
        	applyTransition(true);
      	  holdIcon.setVisibility(View.INVISIBLE);
       	 mainIcon.setVisibility(View.VISIBLE);
   	 }
	}


    private void applyTransition(boolean resetting) {
        MaterialSharedAxis axis = new MaterialSharedAxis(MaterialSharedAxis.Z, resetting);
        TransitionManager.beginDelayedTransition(this, axis);
    }

    private boolean isInside(MotionEvent e) {
        return e.getX() >= 0 && e.getX() <= getWidth()
                && e.getY() >= 0 && e.getY() <= getHeight();
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
    
    public void setActive(boolean b) {
        setAlpha(b ? 1f : 0.3f);
        isEnabled = b;
    }

    public interface OnHoldListener {
        void onHold();
    }
}
