package com.xapps.media.xmusic.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.AttributeSet;
import android.view.Gravity;

import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.xapps.media.xmusic.R;
import com.xapps.media.xmusic.models.SquigglyProgress;
import com.xapps.media.xmusic.utils.*;

public class XSeekbar extends AppCompatSeekBar {

    public static final int STYLE_MATERIAL = 0;
    public static final int STYLE_EXPRESSIVE = 1;

    private Drawable materialThumb;
    private Drawable materialTrack;
    private Drawable expressiveThumb;
    private SquigglyProgress expressiveTrack;

    public XSeekbar(Context context) {
        super(context);
        init(context);
    }

    public XSeekbar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public XSeekbar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setSplitTrack(false);

        expressiveThumb = ContextCompat.getDrawable(context, R.drawable.seekbar_thumb);

        materialTrack = createMaterialProgressDrawable(context, 0xffffffff, MaterialColorUtils.colorOutline);
        materialThumb = createThumb(context, 0xffffffff);

        expressiveTrack = new SquigglyProgress();
        expressiveTrack.setWaveLength(100);
        expressiveTrack.setLineAmplitude(8);
        expressiveTrack.setPhaseSpeed(25);
        expressiveTrack.setStrokeWidth(XUtils.convertToPx(context, 4f));
        expressiveTrack.setTransitionEnabled(true);
        expressiveTrack.setAnimate(true);

        setStyle(STYLE_MATERIAL);
    }

    public void setColor(int color) {
        expressiveTrack.setTint(color);
        
        int backgroundColor = ColorUtils.setAlphaComponent(color, 0x4D);

        setThumbTintList(ColorStateList.valueOf(color));
        setProgressTintList(ColorStateList.valueOf(color));
        setProgressBackgroundTintList(ColorStateList.valueOf(backgroundColor));
    }
    
    public void setAnimate(boolean b) {
        if (expressiveTrack != null) expressiveTrack.setAnimate(b);
    }

    public void setStyle(int style) {
        if (materialThumb == null) {
            materialThumb = getThumb();
        }
        
        if (style == 0) {
            setThumb(materialThumb);
            setProgressDrawable(materialTrack);
        } else if (style == 1) {
            setThumb(expressiveThumb);
            setProgressDrawable(expressiveTrack);
        } else {
            throw new IllegalArgumentException("Style int must be either 0 or 1");
        }
    }

    private Drawable createProgress(Context context, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(XUtils.convertToPx(context, 0.5f));
        return new ClipDrawable(drawable, Gravity.START, ClipDrawable.HORIZONTAL);
    }

    private Drawable createTrackBackground(Context context, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(XUtils.convertToPx(context, 0.5f));
        return drawable;
    }

    private Drawable createMaterialProgressDrawable(Context context, int progressColor, int backgroundColor) {
        LayerDrawable layer = new LayerDrawable(new Drawable[]{
                createTrackBackground(context, backgroundColor),
                createProgress(context, progressColor)
        });

        layer.setId(0, android.R.id.background);
        layer.setId(1, android.R.id.progress);

        layer.setLayerGravity(0, Gravity.CENTER_VERTICAL);
        layer.setLayerGravity(1, Gravity.CENTER_VERTICAL);
        
        int trackHeight = XUtils.convertToPx(context, 4f);
        layer.setLayerHeight(0, trackHeight);
        layer.setLayerHeight(1, trackHeight);

        return layer;
    }

    private Drawable createThumb(Context context, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        int size = XUtils.convertToPx(context, 16f);
        drawable.setSize(size, size);
        return drawable;
    }
}
