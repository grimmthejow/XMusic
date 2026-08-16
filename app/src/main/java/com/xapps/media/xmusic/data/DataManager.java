package com.xapps.media.xmusic.data;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.xapps.media.xmusic.helper.SongSorter;
import java.util.concurrent.Executors;

public class DataManager {
    public static SharedPreferences sp;
    private static boolean isInitialized = false;

    public interface OnDataInitListener {
        void onInitComplete();
    }

    public static void init(Context c) {
        sp = c.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        isInitialized = true;
    }

    public static void init(Context c, OnDataInitListener listener) {
        Executors.newSingleThreadExecutor().execute(() -> {
            sp = c.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            isInitialized = true;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (listener != null) listener.onInitComplete();
            });
        });
    }

    public static boolean isInitialized() {
        return isInitialized;
    }

    public static void setDataInitialized() {
        sp.edit().putBoolean("isDataInitialized", true).apply();
    }

    public static boolean isDataLoaded() {
        return sp.getBoolean("isDataInitialized", false);
    }

    public static void setDynamicColorsEnabled(boolean b) {
        sp.edit().putBoolean("isDynamicColorsOn", b).apply();
    }

    public static boolean isDynamicColorsOn() {
        return sp.getBoolean("isDynamicColorsOn", false);
    }

    public static void setCustomColorsEnabled(boolean b) {
        sp.edit().putBoolean("isCustomColorsOn", b).apply();
    }

    public static boolean isCustomColorsOn() {
        return sp.getBoolean("isCustomColorsOn", false);
    }

    public static void setCustomColor(int c) {
        sp.edit().putInt("customColor", c).apply();
    }

    public static int getCustomColor() {
        return sp.getInt("customColor", 0xFFFF7AAE);
    }

    public static void setProgress(int i) {
        sp.edit().putInt("progress", i).apply();
    }

    public static int getProgress() {
        return sp.getInt("progress", 0);
    }

    public static void setThemeMode(int mode) {
        sp.edit().putInt("theme", mode).apply();
    }

    public static int getThemeMode() {
        return sp.getInt("theme", 0);
    }

    public static void setOledTheme(boolean b) {
        sp.edit().putBoolean("oledTheme", b).apply();
    }

    public static boolean isOledThemeEnabled() {
        return sp.getBoolean("oledTheme", false);
    }

    public static void setNewIconEnabled(boolean b) {
        sp.edit().putBoolean("newIcon", b).apply();
    }

    public static boolean isNewIconEnabled() {
        return sp.getBoolean("newIcon", false);
    }

    public static void setStableColors(boolean b) {
        sp.edit().putBoolean("stable_colors", b).apply();
    }

    public static boolean areStableColors() {
        return sp.getBoolean("stable_colors", false);
    }

    public static void saveLatestRepeatMode(String s) {
        sp.edit().putString("repeatMode", s).apply();
    }

    public static String getLatestRepeatMode() {
        return sp.getString("repeatMode", "LOOP_OFF");
    }

    public static void saveLatestShuffleMode(String s) {
        sp.edit().putString("shuffleMode", s).apply();
    }

    public static String getLatestShuffleMode() {
        return sp.getString("shuffleMode", "SHUFFLE_OFF");
    }

    public static void saveItemsList(String s) {
        sp.edit().putString("listData", s).apply();
    }

    public static String loadItemsList() {
        return sp.getString("listData", "");
    }

    public static void setDescendingOrder(boolean b) {
        sp.edit().putBoolean("descending_order", b).apply();
    }

    public static boolean isDescendingOrder() {
        return sp.getBoolean("descending_order", true);
    }

    public static void setSongFilterType(SongSorter.SortBy sortBy) {
        sp.edit().putString("filter_type", sortBy.name()).apply();
    }

    public static SongSorter.SortBy getSongFilterType() {
        String value = sp.getString("filter_type", SongSorter.SortBy.TITLE.name());
        try {
            return SongSorter.SortBy.valueOf(value);
        } catch (IllegalArgumentException e) {
            return SongSorter.SortBy.TITLE;
        }
    }
    
    public static void setBlurOn(boolean b) {
        sp.edit().putBoolean("blur_effect", b).apply();
    }

    public static boolean isBlurOn() {
        return sp.getBoolean("blur_effect", false);
    }

    public static String getFontConfig() {
		return sp.getString("font_config", new StringBuilder()
            .append("'wdth' ").append(100).append(", ")
            .append("'wght' ").append(500).append(", ")
            .append("'opsz' ").append(18).append(", ")
            .append("'GRAD' ").append(0).append(", ")
            .append("'ROND' ").append(0).append(", ")
            .append("'slnt' ").append(0)
        .toString());
	}

    public static void setFontConfig(String config) {
		sp.edit().putString("font_config", config).apply();
	}

    public static boolean getStaticScrollState() {
        return !sp.getBoolean("lyrics_elastic_scroll", false);
    }

    public static boolean getUserStaticScrollState() {
        return !sp.getBoolean("lyrics_elastic_manual_scroll", false);
    }

    public static boolean getUseSparklesState() {
        return sp.getBoolean("lyrics_sparkles", false);
    }

}
