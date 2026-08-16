package com.xapps.media.xmusic.utils;

import android.Manifest;
import android.animation.Animator;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Interpolator;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.animation.ValueAnimator;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatDelegate;

import androidx.core.content.ContextCompat;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
import com.xapps.media.xmusic.data.DataManager;
import com.xapps.media.xmusic.R;

import java.lang.reflect.Field;
import java.util.Locale;

public class XUtils {

    private static String theme = "auto";
	
	public static void increaseMarginsSmoothly(View view, int increaseLeft, int increaseTop, int increaseRight, int increaseBottom, long duration) {
		ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
		int startLeft = params.leftMargin;
		int startTop = params.topMargin;
		int startRight = params.rightMargin;
		int startBottom = params.bottomMargin;
		int endLeft = startLeft + increaseLeft;
		int endTop = startTop + increaseTop;
		int endRight = startRight + increaseRight;
		int endBottom = startBottom + increaseBottom;
		ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
		animator.setDuration(duration);
		animator.addUpdateListener(animation -> {
			float progress = (float) animation.getAnimatedValue();
			params.leftMargin = (int) (startLeft + progress * (endLeft - startLeft));
			params.topMargin = (int) (startTop + progress * (endTop - startTop));
			params.rightMargin = (int) (startRight + progress * (endRight - startRight));
			params.bottomMargin = (int) (startBottom + progress * (endBottom - startBottom));
			view.setLayoutParams(params);
		});
		
		animator.start();
	}
	
	public static void setMargins(View view, int left, int top, int right, int bottom) {
		ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
		params.setMargins(left, top, right, bottom);
		view.setLayoutParams(params);
	}
    
    public static void animateMarginsTo(View view, int left, int top, int right, int bottom, long duration, android.view.animation.Interpolator interpolator) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        int initialTop = params.topMargin;
        int initialBottom = params.bottomMargin;
        int initialLeft = params.leftMargin;
        int initialRight = params.rightMargin;
        
        int diffTop = top - initialTop;
        int diffBottom = bottom - initialBottom;
        int diffLeft = left - initialLeft;
        int diffRight = right - initialRight;
        
        android.view.animation.Interpolator interpolator2 = new android.view.animation.PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f);
        
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
		animator.setDuration(duration);
        animator.setInterpolator(interpolator != null? interpolator : interpolator2);
		animator.addUpdateListener(animation -> {
			float progress = (float) animation.getAnimatedValue();
			params.leftMargin = (int) (initialLeft + progress * diffLeft);
			params.topMargin = (int) (initialTop + progress * diffTop);
			params.rightMargin = (int) (initialRight + progress * diffRight);
			params.bottomMargin = (int) (initialBottom + progress * diffBottom);
			view.setLayoutParams(params);
		});
		
		animator.start();
    }
	
	public static void increaseMargins(View view, int increaseLeft, int increaseTop, int increaseRight, int increaseBottom) {
		ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
		params.leftMargin += increaseLeft;
		params.topMargin += increaseTop;
		params.rightMargin += increaseRight;
		params.bottomMargin += increaseBottom;
		view.setLayoutParams(params);
	}
	
	public static int getStatusBarHeight(Context c) {
		Resources resources = c.getResources();
		int resourceId = resources.getIdentifier("status_bar_height", "dimen", "android");
		if (resourceId > 0) {
			return resources.getDimensionPixelSize(resourceId);
		}
		return 0;
	}
    
    public static int getNavigationBarHeight(Context context) {
        int resourceId = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return context.getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }
	
	public static int convertToPx(Context context, float dp) {
		float density = context.getResources().getDisplayMetrics().density;
		return (int) (dp * density + 0.5f);
	}
    
    public static float convertSpToPx(Context context, float sp) {
        float density = context.getResources().getDisplayMetrics().density;
        float fontScale = context.getResources().getConfiguration().fontScale;
        return sp * density * fontScale;
    }
	
	public static int getMargin(View view, String side) {
		if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
			ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
			switch (side.toLowerCase()) {
				case "left":
				return params.leftMargin;
				case "top":
				return params.topMargin;
				case "right":
				return params.rightMargin;
				case "bottom":
				return params.bottomMargin;
				default:
				throw new IllegalArgumentException("Invalid margin side: " + side);
			}
		}
		return 0;
	}
    
    public static void showMessage(Context context, String s) {
        Toast.makeText(context, s, Toast.LENGTH_SHORT).show();
    }
    
    public static void openSettings(Context c) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", c.getPackageName(), null);
        intent.setData(uri);
        c.startActivity(intent);
    }
    
    public static void animateColor(int fromColor, int toColor, ValueAnimator.AnimatorUpdateListener listener) {
        ValueAnimator colorAnimator = ValueAnimator.ofArgb(fromColor, toColor);
		colorAnimator.setDuration(0);
		colorAnimator.addUpdateListener(listener);
		colorAnimator.start();
	}
    
    public static Integer extractColor(View view) {
		Drawable background = view.getBackground();
		if (background instanceof GradientDrawable) {
			try {
				Field stateField = GradientDrawable.class.getDeclaredField("mFillPaint");
				stateField.setAccessible(true);
				Paint fillPaint = (Paint) stateField.get(background);
				return fillPaint.getColor();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}
    
    public static int interpolateColor(int startColor, int endColor, float percentage) {
			return (int) new android.animation.ArgbEvaluator().evaluate(percentage, startColor, endColor);
	}

    public static float clamp(float value, float min, float max) {
			return Math.max(min, Math.min(max, value));
	}

    public static boolean isDarkMode(Context context) {
        int night =
        context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isLight = night != Configuration.UI_MODE_NIGHT_YES;
        return !isLight;
    }
    
    public static void setThemeMode(String mode) {
        switch (mode) {
            case "light":
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            break;
            case "dark":
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            break;
            case "auto":
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            break;
        }
        theme = mode;
    }

    public static int normalizeColor(int color) {
        return color | 0xFF000000;
    }

    public static void updateTheme() {
        switch(DataManager.getThemeMode()) {
            case 0:
                setThemeMode("auto");
            break;
            case 1:
                setThemeMode("dark");
            break;
            case 2:
                setThemeMode("light");
            break;
        }
    }
    
    public static void applyDynamicColors(Activity c) {
        if (DataManager.isDynamicColorsOn() && Build.VERSION.SDK_INT >= 31) {
            if (DataManager.isCustomColorsOn()) {
                DynamicColorsOptions.Builder options = new DynamicColorsOptions.Builder();
                options.setContentBasedSource(DataManager.getCustomColor());
                DynamicColors.applyToActivityIfAvailable(c, options.build());
            } else {
                DynamicColors.applyToActivityIfAvailable(c);
            }
        }
    }
    
    public static void applyDynamicColors(Activity c, boolean oledMode) {
        if (oledMode) {
            if (DataManager.isDynamicColorsOn() && Build.VERSION.SDK_INT >= 31) {
                if (DataManager.isCustomColorsOn()) {
                    DynamicColorsOptions.Builder options = new DynamicColorsOptions.Builder();
                    options.setContentBasedSource(DataManager.getCustomColor());
                    DynamicColors.applyToActivityIfAvailable(c, options.build());
                } else {
                    DynamicColors.applyToActivityIfAvailable(c);
                }
            } 
        } else {
            applyDynamicColors(c);
        }
    }
    
    public static void applyDynamicColors(Application a) {
        if (DataManager.isDynamicColorsOn() && Build.VERSION.SDK_INT >= 31) {
            if (DataManager.isCustomColorsOn()) {
                DynamicColorsOptions.Builder options = new DynamicColorsOptions.Builder();
                options.setContentBasedSource(DataManager.getCustomColor());
                options.setThemeOverlay(R.style.AppTheme);
                DynamicColors.applyToActivitiesIfAvailable(a, options.build());
            } else {
                DynamicColors.applyToActivitiesIfAvailable(a);
            }
        }
    }
    
    public static boolean areAllPermsGranted(Context c) {
        if (Build.VERSION.SDK_INT <= 29) {
            return checkPermissionAllowed(c, Manifest.permission.READ_EXTERNAL_STORAGE) && checkPermissionAllowed(c, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        } else if (30 <= Build.VERSION.SDK_INT && Build.VERSION.SDK_INT <= 32) {
            return Environment.isExternalStorageManager();
        } else {
            return checkPermissionAllowed(c, Manifest.permission.READ_MEDIA_AUDIO) && checkPermissionAllowed(c, Manifest.permission.POST_NOTIFICATIONS);
        }
    }
    
    public static boolean checkPermissionAllowed(Context context, String permission) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            return false;
        }
    }
    
    public static boolean areBlursOrDynamicColorsSupported() {
        return Build.VERSION.SDK_INT >= 31;
    }
    
    public static void animateBlur(View view, boolean enable, long duration) {
        ValueAnimator va = ValueAnimator.ofFloat(enable? 0.1f : 1f, enable? 1f : 0.1f);
        va.setDuration(duration);
        va.addUpdateListener(a -> {
            float progress = (float) a.getAnimatedValue();
            view.setRenderEffect(RenderEffect.createBlurEffect(25f*progress, 25f*progress, Shader.TileMode.CLAMP));
        });
        va.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (!enable) view.setRenderEffect(null);
            }
            @Override
            public void onAnimationStart(Animator a) {
            }
            @Override
            public void onAnimationRepeat(Animator a) {
            }
            @Override
            public void onAnimationCancel(Animator a) {
            }
        });
        va.start();
    }
	
	public static String hashFilePath(String filePath) {
        return String.valueOf(filePath.hashCode());
    }
	
	public static String getAudioCodec(Context context, Uri uri) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();

        try {
            mmr.setDataSource(context, uri);

            String mime = mmr.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_MIMETYPE
            );

            if (mime != null) {
                switch (mime) {
                    case "audio/flac":
                        return "FLAC";
                    case "audio/mpeg":
                        return "MP3";
                    case "audio/opus":
                        return "OPUS";
                    case "audio/vorbis":
                        return "VORBIS";
                    case "audio/alac":
                        return "ALAC";
                    case "audio/mp4":
                        break; // sus
                    default:
                    if (mime.startsWith("audio/")) {
                        return mime.replace("audio/", "").toUpperCase();
                    }
                }
            }

        } catch (Exception ignored) {
        } finally {
			try {
                mmr.release();
			} catch (Exception e) {
				
			}
        }

        MediaExtractor extractor = new MediaExtractor();

        try {
            extractor.setDataSource(context, uri, null);

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);

                if (mime == null || !mime.startsWith("audio/")) continue;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    String codecs = format.getString(MediaFormat.KEY_CODECS_STRING);

                    if (codecs != null) {
                        String lower = codecs.toLowerCase();

                        if (lower.contains("alac")) return "ALAC";
                        if (lower.contains("mp4a")) return "AAC";
                        if (lower.contains("opus")) return "OPUS";
                    }
                }

                if ("audio/mp4a-latm".equals(mime)) return "AAC";
                if ("audio/alac".equals(mime)) return "ALAC";
            }
    
        } catch (Exception ignored) {
        } finally {
            extractor.release();
        }

        return "UNKNOWN";
    }

    public static String millisecondsToDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, remainingSeconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSeconds);
        }
    }
    

    public static void animateSize(View v, int w, int h, long d) {
        ViewGroup.LayoutParams p = v.getLayoutParams();

        int sw = v.getWidth();
        int sh = v.getHeight();

        int tw = (w == -1) ? sw : w;
        int th = (h == -1) ? sh : h;

        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(d);

        a.addUpdateListener(i -> {
            float f = (float) i.getAnimatedValue();
            p.width = (int) (sw + (tw - sw) * f);
            p.height = (int) (sh + (th - sh) * f);
            v.setLayoutParams(p);
        });
    
        a.start();
    }
    
    public static void runOnReady(View view, Runnable action) {
        if (view == null || action == null) {
            return;
        }
        if (isViewTrulyVisible(view)) {
            action.run();
            return;
        }
        view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (isViewTrulyVisible(view)) {
                    view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    action.run();
                }
            }
        });
    }

    private static boolean isViewTrulyVisible(View view) {
        if (!view.isShown()) {
            return false;
        }
        Rect hitRect = new Rect();
        return view.getGlobalVisibleRect(hitRect);
    }

    public static boolean predictiveBackSupported() {
        return Build.VERSION.SDK_INT >= 34;
    }
    

}
