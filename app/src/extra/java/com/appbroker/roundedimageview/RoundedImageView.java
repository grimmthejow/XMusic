package com.appbroker.roundedimageview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.cardview.widget.CardView;

public class RoundedImageView extends androidx.appcompat.widget.AppCompatImageView {

    public enum Corner{
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_RIGHT,
        BOTTOM_LEFT,
    }

    private final float[] radii;

    public RoundedImageView(@NonNull Context context) {
        this(context,null);
    }

    public RoundedImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs,0);
    }

    public RoundedImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        final TypedArray typedArray=context.obtainStyledAttributes(attrs, R.styleable.RoundedImageView,defStyleAttr,0);
        this.setAdjustViewBounds(typedArray.getBoolean(R.styleable.RoundedImageView_android_adjustViewBounds,true));
        final float radius=typedArray.getDimension(R.styleable.RoundedImageView_radius,0);
        final float topLeftRadius=typedArray.getDimension(R.styleable.RoundedImageView_topLeftRadius,radius);
        final float topRightRadius=typedArray.getDimension(R.styleable.RoundedImageView_topRightRadius,radius);
        final float bottomLeftRadius=typedArray.getDimension(R.styleable.RoundedImageView_bottomLeftRadius,radius);
        final float bottomRightRadius=typedArray.getDimension(R.styleable.RoundedImageView_bottomRightRadius,radius);
        radii= new float[]{topLeftRadius, topLeftRadius, topRightRadius, topRightRadius, bottomRightRadius, bottomRightRadius, bottomLeftRadius, bottomLeftRadius};

        typedArray.recycle();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Path path=new Path();
        RectF mainRectF = new RectF(0, 0, getWidth(), getHeight());
        path.addRoundRect(mainRectF,radii,Path.Direction.CW);
        canvas.clipPath(path);
        super.onDraw(canvas);
    }

    public void setRadius(@Px int radius){
        bytefill(radii,radius);
    }
    
    public void setRadius(@Px float radius){
        bytefill(radii,Math.round(radius));
    }

    public void setRadius(Corner corner,@Px int radius){
        switch (corner){
            case TOP_LEFT:
                radii[0]=radius;
                radii[1]=radius;
                break;
            case TOP_RIGHT:
                radii[2]=radius;
                radii[3]=radius;
                break;
            case BOTTOM_RIGHT:
                radii[4]=radius;
                radii[5]=radius;
                break;
            case BOTTOM_LEFT:
                radii[6]=radius;
                radii[7]=radius;
                break;
        }
    }

    private void bytefill(@NonNull float[] array, int value) {
        int len = array.length;
        array[0] = value;
        for (int i = 1; i < len; i += i) {
            System.arraycopy(array, 0, array, i, Math.min((len - i), i));
        }
    }
}