package com.xapps.media.xmusic.activity.manager;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;

import androidx.core.graphics.ColorUtils;
import androidx.core.view.*;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.Player;
import androidx.transition.TransitionManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.transition.MaterialFadeThrough;

import com.xapps.media.xmusic.R;
import com.xapps.media.xmusic.activity.RootActivity;
import com.xapps.media.xmusic.callback.CallbackInterface;
import com.xapps.media.xmusic.common.PlaybackControlListener;
import com.xapps.media.xmusic.data.DataManager;
import com.xapps.media.xmusic.data.LiveColors;
import com.xapps.media.xmusic.data.RuntimeData;
import com.xapps.media.xmusic.databinding.ActivityRootBinding;
import com.xapps.media.xmusic.fragment.SettingsFragment;
import com.xapps.media.xmusic.fragment.SongsListFragment;
import com.xapps.media.xmusic.lyric.LyricsExtractor;
import com.xapps.media.xmusic.utils.LyricsParser;
import com.xapps.media.xmusic.utils.ColorPaletteUtils;
import com.xapps.media.xmusic.utils.MaterialColorUtils;
import com.xapps.media.xmusic.utils.XUtils;
import com.xapps.media.xmusic.viewmodel.MainActivityViewModel;
import com.xapps.media.xmusic.widget.*;

import kotlin.Unit;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UIManager implements PlaybackControlListener {

    private final RootActivity activity;
    private final ActivityRootBinding binding;
    public static final int LAYOUT_STATE_FULL = 0;
    public static final int LAYOUT_STATE_EXPOSE_TABS = 1;
    public static final int LAYOUT_STATE_EXPOSE_BNV = 2;
    public static final int LAYOUT_STATE_EXPOSE_PLAYER = 3;
    public static final int LAYOUT_STATE_EXPOSE_PLAYER_ONLY = 4;
    public static final int LAYOUT_STATE_EXPOSE_PLAYER_TABS = 5;
    public static final int LAYOUT_STATE_EXPOSE_TABS_BNV = 6;
    public static final int LAYOUT_STATE_EXPOSE_FULL = 7;

    public static final int ANIMATION_DURATION_INSTANT = 0;
    public static final int ANIMATION_DURATION_SHORT = 300;
    public static final int ANIMATION_DURATION_NORMAL = 500;

    public int bnvNeededMargin, playerNeededMargin, tabsNeededMargin, playerDockedNeededMargin;

    private int layoutState = LAYOUT_STATE_FULL;

    private volatile long metadataRequestId;

    private Handler handler = new Handler(Looper.getMainLooper());

    private final ExecutorService metadataExecutor = Executors.newSingleThreadExecutor();

    public boolean tabsHidden, bnvHidden, playerDocked, playerHidden;

    private boolean isOledTheme, isBlurOn;

    public int peekHeight, sideMargins, bottomMargin, dockedMargin;

    private Interpolator interpolator;

    private ValueAnimator colorAnimator;

    private int bnvHeight;

    private int playerSurface, bottomSheetColor;

    private Map<String, Integer> effectiveOldColors = new HashMap<>();

    public MainActivityViewModel viewModel;

    public UIManager(RootActivity activity) {
        this.activity = activity;
        this.binding = activity.getBinding();
        viewModel = new ViewModelProvider(activity).get(MainActivityViewModel.class);
    }

    public void initUI() {
        setupColors();
        setupShapes();
        setupDimensions();
        setupInitialState();
    }

    private void setupColors() {
        binding.rootCard.setCardBackgroundColor(MaterialColorUtils.colorSurfaceContainer);
        binding.miniPlayer.setSheetBackgroundColor(MaterialColorUtils.colorSurfaceContainer);

        binding.bottomNavigation.setBackground(new ColorDrawable(MaterialColorUtils.colorSurface));
    }

    private void setupDimensions() {
        sideMargins = XUtils.convertToPx(activity, 8f);

        ViewKt.doOnLayout(
                binding.collapsedPlayer.musicProgress,
                v -> {
                    peekHeight =
                            XUtils.convertToPx(activity, 64f)
                                    + binding.collapsedPlayer.musicProgress.getHeight();
                    binding.miniPlayer.setPeekHeight(peekHeight);

                    return Unit.INSTANCE;
                });

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation, null);

        ViewKt.doOnLayout(
                binding.bottomNavigation,
                v -> {
                    bottomMargin =
                            XUtils.convertToPx(activity, 16f)
                                    + binding.bottomNavigation.getHeight();

                    playerNeededMargin =
                            binding.miniPlayer.getPeekHeight()
                                    + XUtils.convertToPx(activity, 24f)
                                    + binding.bottomNavigation.getHeight()
                                    + XUtils.getNavigationBarHeight(activity);
                    playerDockedNeededMargin =
                            binding.miniPlayer.getPeekHeight()
                                    + XUtils.convertToPx(activity, 40f)
                                    + XUtils.getNavigationBarHeight(activity);
                    bnvNeededMargin =
                            binding.bottomNavigation.getHeight()
                                    + XUtils.getNavigationBarHeight(activity)
                                    + XUtils.convertToPx(activity, 8f);

                    bnvHeight = binding.bottomNavigation.getHeight();

                    binding.bottomNavigation.setPadding(
                            binding.bottomNavigation.getPaddingLeft(),
                            binding.bottomNavigation.getPaddingTop(),
                            binding.bottomNavigation.getPaddingTop(),
                            binding.bottomNavigation.getPaddingBottom()
                                    + XUtils.getNavigationBarHeight(activity));

                    return Unit.INSTANCE;
                });

        ViewKt.doOnLayout(
                binding.tabLayout,
                v -> {
                    XUtils.increaseMargins(
                            binding.tabLayout, 0, XUtils.getStatusBarHeight(activity), 0, 0);

                    tabsNeededMargin =
                            binding.tabLayout.getHeight()
                                    + XUtils.getStatusBarHeight(activity)
                                    + XUtils.convertToPx(activity, 8f);

                    return Unit.INSTANCE;
                });
    }

    private void setupShapes() {
        int cornerRadius = XUtils.convertToPx(activity, 12f);
        binding.miniPlayer.setFloatingCornerRadii(
                cornerRadius, cornerRadius, cornerRadius, cornerRadius);
        binding.collapsedPlayer.cover.setRadius(XUtils.convertToPx(activity, 8f));
    }

    private void setupInitialState() {
        TypedValue tv = new TypedValue();
        activity.getTheme()
                .resolveAttribute(
                        com.google.android.material.R.attr.motionEasingEmphasizedInterpolator,
                        tv,
                        true);
        interpolator = AnimationUtils.loadInterpolator(activity, tv.resourceId);

        binding.tabLayout.addTab("Songs");
        binding.tabLayout.addTab("Artists");
        binding.tabLayout.addTab("Albums");
        binding.tabLayout.addTab("Genres");
        binding.tabLayout.addTab("Playlists");
        binding.tabLayout.addTab("Browse");

        binding.viewpager.setAdapter(new PagerAdapter(activity));

        binding.containerRoot.setClipChildren(false);
        binding.collapsedPlayer.motionRoot.setClipChildren(false);

        loadSettings();
        updateLyrics();
    }

    // [ -------------- Layout State management methods ----------

    public void setLayoutState(int state, String root) {
        setLayoutState(state, true, root);
    }

    public void setLayoutState(int state, boolean animate, String root) {
        // XUtils.showMessage(activity, "state changed by: " + root);  // DEBUG ONLY

        if (layoutState == state) return;
        if (state > 7 || state < 0) throw new IllegalArgumentException("Invalid state int");

        int duration = animate ? ANIMATION_DURATION_NORMAL : 0;

        layoutState = state;
        switch (state) {
            case LAYOUT_STATE_EXPOSE_PLAYER:
                dockPlayerInternal(false, animate);
                hideBnvInternal(false, animate);
                hideTabsInternal(true, animate);
                hidePlayerInternal(false);
                XUtils.animateMarginsTo(
                        binding.rootCard, 0, 0, 0, playerNeededMargin, duration, interpolator);
                XUtils.animateMarginsTo(
                        binding.searchCard, 0, 0, 0, playerNeededMargin, duration, interpolator);
                XUtils.animateMarginsTo(
                        binding.settingsCard, 0, 0, 0, playerNeededMargin, duration, interpolator);
                break;

            case LAYOUT_STATE_EXPOSE_BNV:
                hideBnvInternal(false, animate);
                hideTabsInternal(true, animate);
                hidePlayerInternal(true);
                XUtils.animateMarginsTo(
                        binding.rootCard, 0, 0, 0, bnvNeededMargin, duration, interpolator);
                XUtils.animateMarginsTo(
                        binding.searchCard, 0, 0, 0, bnvNeededMargin, duration, interpolator);
                XUtils.animateMarginsTo(
                        binding.settingsCard, 0, 0, 0, bnvNeededMargin, duration, interpolator);
                break;

            case LAYOUT_STATE_FULL:
                hideBnvInternal(true, animate);
                hideTabsInternal(true, animate);
                hidePlayerInternal(true);
                XUtils.animateMarginsTo(binding.rootCard, 0, 0, 0, 0, duration, interpolator);
                XUtils.animateMarginsTo(binding.searchCard, 0, 0, 0, 0, duration, interpolator);
                XUtils.animateMarginsTo(binding.settingsCard, 0, 0, 0, 0, duration, interpolator);
                break;

            case LAYOUT_STATE_EXPOSE_FULL:
                hideBnvInternal(false, animate);
                hideTabsInternal(false, animate);
                hidePlayerInternal(false);
                XUtils.animateMarginsTo(
                        binding.rootCard,
                        0,
                        tabsNeededMargin,
                        0,
                        bnvNeededMargin,
                        duration,
                        interpolator);
                XUtils.animateMarginsTo(
                        binding.searchCard,
                        0,
                        0,
                        0,
                        playerNeededMargin,
                        duration,
                        interpolator);
                XUtils.animateMarginsTo(
                        binding.settingsCard,
                        0,
                        0,
                        0,
                        playerNeededMargin,
                        duration,
                        interpolator);
                dockPlayerInternal(false, animate);
                break;

            case LAYOUT_STATE_EXPOSE_TABS_BNV:
                hideBnvInternal(false, animate);
                hideTabsInternal(false, animate);
                hidePlayerInternal(true);
                XUtils.animateMarginsTo(
                        binding.rootCard,
                        0,
                        tabsNeededMargin,
                        0,
                        bnvNeededMargin,
                        duration,
                        interpolator);
                XUtils.animateMarginsTo(
                        binding.searchCard,
                        0,
                        0,
                        0,
                        bnvNeededMargin,
                        duration,
                        interpolator);
                XUtils.animateMarginsTo(
                        binding.settingsCard,
                        0,
                        0,
                        0,
                        bnvNeededMargin,
                        duration,
                        interpolator);
                break;

            case LAYOUT_STATE_EXPOSE_PLAYER_ONLY:
                hideBnvInternal(true, animate);
                hideTabsInternal(true, animate);
                hidePlayerInternal(false);
                XUtils.animateMarginsTo(
                        binding.rootCard,
                        0,
                        0,
                        0,
                        playerDockedNeededMargin,
                        duration,
                        interpolator);
                XUtils.animateMarginsTo(
                        binding.searchCard,
                        0,
                        0,
                        0,
                        playerDockedNeededMargin,
                        duration,
                        interpolator);
                XUtils.animateMarginsTo(
                        binding.settingsCard,
                        0,
                        0,
                        0,
                        playerDockedNeededMargin,
                        duration,
                        interpolator);
                dockPlayerInternal(true, animate);
                break;

            case LAYOUT_STATE_EXPOSE_TABS:
                hideBnvInternal(true, animate);
                hideTabsInternal(false, animate);
                hidePlayerInternal(true);
                XUtils.animateMarginsTo(
                        binding.rootCard, 0, tabsNeededMargin, 0, 0, duration, interpolator);
                XUtils.animateMarginsTo(
                        binding.searchCard, 0, 0, 0, 0, duration, interpolator);
                XUtils.animateMarginsTo(
                        binding.settingsCard, 0, 0, 0, 0, duration, interpolator);
                break;

            case LAYOUT_STATE_EXPOSE_PLAYER_TABS:
                hideBnvInternal(true, animate);
                hideTabsInternal(false, animate);
                hidePlayerInternal(false);
                XUtils.animateMarginsTo(
                        binding.rootCard,
                        0,
                        tabsNeededMargin,
                        0,
                        playerDockedNeededMargin,
                        duration,
                        interpolator);
                XUtils.animateMarginsTo(
                        binding.searchCard,
                        0,
                        0,
                        0,
                        playerDockedNeededMargin,
                        duration,
                        interpolator);
                XUtils.animateMarginsTo(
                        binding.settingsCard,
                        0,
                        0,
                        0,
                        playerDockedNeededMargin,
                        duration,
                        interpolator);
                break;
        }
    }

    public void hideBnv(boolean b) {
        hideBnv(b, true);
    }

    public void hideBnv(boolean b, boolean animate) {
        if (playerHidden) {
            hideBnvInternal(b, animate);
        } else {
            hideBnvInternal(b, animate);
            dockPlayerInternal(b, animate);
        }
        computeState();
    }

    private void computeState() {
        if (!playerHidden) {
            if (!bnvHidden && !tabsHidden) layoutState = LAYOUT_STATE_EXPOSE_FULL;
            else if (!bnvHidden && tabsHidden) layoutState = LAYOUT_STATE_EXPOSE_PLAYER;
            else if (bnvHidden && !tabsHidden) layoutState = LAYOUT_STATE_EXPOSE_PLAYER_TABS;
            else layoutState = LAYOUT_STATE_EXPOSE_PLAYER_ONLY;
        } else {
            if (!bnvHidden && !tabsHidden) layoutState = LAYOUT_STATE_EXPOSE_TABS_BNV;
            else if (!bnvHidden && tabsHidden) layoutState = LAYOUT_STATE_EXPOSE_BNV;
            else if (bnvHidden && !tabsHidden) layoutState = LAYOUT_STATE_EXPOSE_TABS;
            else layoutState = LAYOUT_STATE_FULL;
        }
    }

    private void hideBnvInternal(boolean hide, boolean animate) {
        bnvHidden = hide;
        if (animate) {
            binding.bottomNavigation
                    .animate()
                    .scaleX(hide ? 0.85f : 1f)
                    .scaleY(hide ? 0.85f : 1f)
                    .translationY(hide ? 15 : 0)
                    .alpha(hide ? 0f : 1f)
                    .setDuration(ANIMATION_DURATION_NORMAL)
                    .withStartAction(
                            () -> {
                                if (!hide) binding.bottomNavigation.setEnabled(true);
                            })
                    .withEndAction(
                            () -> {
                                if (hide) binding.bottomNavigation.setEnabled(false);
                            })
                    .setInterpolator(interpolator)
                    .start();
        } else {
            binding.bottomNavigation.setScaleX(hide ? 0.85f : 1f);
            binding.bottomNavigation.setScaleY(hide ? 0.85f : 1f);
            binding.bottomNavigation.setTranslationY(hide ? 15 : 0);
            binding.bottomNavigation.setAlpha(hide ? 0f : 1f);
            binding.bottomNavigation.setEnabled(!hide);
        }
    }

    private void hideTabsInternal(boolean hide, boolean animate) {
        tabsHidden = hide;
        if (animate) {
            binding.tabLayout
                    .animate()
                    .scaleX(hide ? 0.85f : 1f)
                    .scaleY(hide ? 0.85f : 1f)
                    .translationY(hide ? -XUtils.convertToPx(activity, 5f) : 0)
                    .alpha(hide ? 0f : 1f)
                    .setDuration(ANIMATION_DURATION_NORMAL)
                    .withStartAction(
                            () -> {
                                if (!hide) binding.tabLayout.setEnabled(true);
                            })
                    .withEndAction(
                            () -> {
                                if (hide) binding.tabLayout.setEnabled(false);
                            })
                    .setInterpolator(interpolator)
                    .start();
        } else {
            binding.tabLayout.setScaleX(hide ? 0.85f : 1f);
            binding.tabLayout.setScaleY(hide ? 0.85f : 1f);
            binding.tabLayout.setTranslationY(hide ? -15 : 0);
            binding.tabLayout.setAlpha(hide ? 0f : 1f);
            binding.tabLayout.setEnabled(!hide);
        }
    }

    private void hidePlayerInternal(boolean hide) {
        playerHidden = hide;
        binding.miniPlayer.setState(
                hide
                        ? ExpressiveSliderLayout.STATE_HIDDEN
                        : ExpressiveSliderLayout.STATE_COLLAPSED);
    }

    private void dockPlayerInternal(boolean dock, boolean animate) {
        playerDocked = dock;
        int bottomMargin = XUtils.convertToPx(activity, 16f) + bnvHeight - XUtils.getNavigationBarHeight(activity);
        dockedMargin = bottomMargin;

        int targetMargin = dock
                ? XUtils.getNavigationBarHeight(activity)
                : XUtils.convertToPx(activity, 16f) + bnvHeight;

        if (animate) {
            ValueAnimator animator = ValueAnimator.ofInt(binding.miniPlayer.getFloatingMargin("bottom"), targetMargin);
            animator.setInterpolator(interpolator);
            animator.addUpdateListener(animation -> {
                binding.miniPlayer.setFloatingMargins(XUtils.convertToPx(activity, 8f), (int) animation.getAnimatedValue());
            });
            animator.start();
        } else {
            binding.miniPlayer.setFloatingMargins(XUtils.convertToPx(activity, 8f), targetMargin);
        }
    }


    public void onPlayerHidden() {
        dockPlayerInternal(false, false);
        if (bnvHidden && tabsHidden) {
            setLayoutState(LAYOUT_STATE_FULL, "onPlayerHidden");
        } else if (!bnvHidden && tabsHidden) {
            setLayoutState(LAYOUT_STATE_EXPOSE_BNV, "onPlayerHidden");
        } else if (bnvHidden && !tabsHidden) {
            setLayoutState(LAYOUT_STATE_EXPOSE_TABS, "onPlayerHidden");
        } else if (!bnvHidden && !tabsHidden) {
            setLayoutState(LAYOUT_STATE_EXPOSE_TABS_BNV, "onPlayerHidden");
        } else {
            throw new IllegalStateException(
                    "onPlayerHidden was without handling layout state : "
                            + String.valueOf(binding.miniPlayer.getState()));
        }

        activity.getLogicManager().sgfState = activity.getLogicManager().sgfState == LAYOUT_STATE_EXPOSE_BNV? LAYOUT_STATE_FULL : LAYOUT_STATE_EXPOSE_PLAYER_ONLY;
        activity.getLogicManager().srfState = activity.getLogicManager().srfState == LAYOUT_STATE_EXPOSE_BNV? LAYOUT_STATE_FULL : LAYOUT_STATE_EXPOSE_PLAYER_ONLY;

    }

    public void hideComponents(boolean hidePlayer, boolean hideBnv, boolean hideTabs, String fragment) {
        int state;

        if (!hidePlayer) {
            if (!hideBnv && !hideTabs) state = LAYOUT_STATE_EXPOSE_FULL;
            else if (!hideBnv && hideTabs) state = LAYOUT_STATE_EXPOSE_PLAYER;
            else if (hideBnv && !hideTabs) state = LAYOUT_STATE_EXPOSE_PLAYER_TABS;
            else state = LAYOUT_STATE_EXPOSE_PLAYER_ONLY;
        } else {
            if (!hideBnv && !hideTabs) state = LAYOUT_STATE_EXPOSE_TABS_BNV;
            else if (!hideBnv && hideTabs) state = LAYOUT_STATE_EXPOSE_BNV;
            else if (hideBnv && !hideTabs) state = LAYOUT_STATE_EXPOSE_TABS;
            else state = LAYOUT_STATE_FULL;
        }

        if (hidePlayer) {
            activity.getLogicManager().sgfState = activity.getLogicManager().sgfState == LAYOUT_STATE_EXPOSE_PLAYER_ONLY? LAYOUT_STATE_FULL : LAYOUT_STATE_EXPOSE_BNV;
            activity.getLogicManager().srfState = activity.getLogicManager().srfState == LAYOUT_STATE_EXPOSE_PLAYER_ONLY? LAYOUT_STATE_FULL : LAYOUT_STATE_EXPOSE_BNV;
        } else {
            activity.getLogicManager().sgfState = activity.getLogicManager().sgfState == LAYOUT_STATE_EXPOSE_BNV? LAYOUT_STATE_FULL : LAYOUT_STATE_EXPOSE_BNV;
            activity.getLogicManager().srfState = activity.getLogicManager().srfState == LAYOUT_STATE_EXPOSE_PLAYER_ONLY? LAYOUT_STATE_FULL : LAYOUT_STATE_EXPOSE_BNV;
        }

        setLayoutState(state, fragment);
    }

    // --------------- Fragments Viewpager Adapter ----------------- ]

    public class PagerAdapter extends FragmentStateAdapter {

        public PagerAdapter(FragmentActivity activity) {
            super(activity);
        }

        @Override
        public Fragment createFragment(int position) {
            if (position == 0) return new SongsListFragment();
            return new SettingsFragment();
        }

        @Override
        public int getItemCount() {
            return 1;
        }
    }

    // PLAYER ANIMATION AND UPDATES LOGIC -------------

    public void updateColors() {
        if (ColorPaletteUtils.lightColors == null && ColorPaletteUtils.darkColors == null) {
            return;
        }

        Map<String, Integer> colors =
                XUtils.isDarkMode(activity)
                        ? ColorPaletteUtils.darkColors
                        : ColorPaletteUtils.lightColors;
        Map<String, Integer> oldColors =
                XUtils.isDarkMode(activity)
                        ? ColorPaletteUtils.oldDarkColors
                        : ColorPaletteUtils.oldLightColors;

        effectiveOldColors = new HashMap<>(oldColors);

        boolean hasLive = LiveColors.primary != 0;
        int onTertiary = colors.get("onTertiary");
        int tertiary = colors.get("tertiary");
        int oldOnTertiary = hasLive ? LiveColors.onTertiary : effectiveOldColors.get("onTertiary");
        int oldTertiary = hasLive ? LiveColors.tertiary : effectiveOldColors.get("tertiary");
        int surface = isOledTheme ? 0xff000000 : colors.get("surface");
        int oldSurface =
                isOledTheme
                        ? 0xff000000
                        : (hasLive ? LiveColors.surface : effectiveOldColors.get("surface"));
        int surfaceContainer = isOledTheme ? 0xff050505 : colors.get("surfaceContainer");
        int oldSurfaceContainer =
                isOledTheme
                        ? 0xff050505
                        : (hasLive
                           ? LiveColors.surfaceContainer
                           : effectiveOldColors.get("surfaceContainer"));
        int outline = colors.get("outline");
        int oldOutline = hasLive ? LiveColors.outline : effectiveOldColors.get("outline");
        int primary = colors.get("primary");
        int oldPrimary = hasLive ? LiveColors.primary : effectiveOldColors.get("primary");
        int onPrimary = colors.get("onPrimary");
        int oldOnPrimary = hasLive ? LiveColors.onPrimary : effectiveOldColors.get("onPrimary");
        int onSurfaceContainer =
                isOledTheme ? colors.get("onSurface") : colors.get("onSurfaceContainer");
        int oldOnSurfaceContainer =
                isOledTheme
                        ? (hasLive ? LiveColors.onSurface : effectiveOldColors.get("onSurface"))
                        : (hasLive
                           ? LiveColors.onSurfaceContainer
                           : effectiveOldColors.get("onSurfaceContainer"));
        int onSurface = colors.get("onSurface");
        int oldOnSurface = hasLive ? LiveColors.onSurface : effectiveOldColors.get("onSurface");

        binding.gradientView.setColors(surface, onPrimary, onTertiary);

        Drawable nextBg = binding.expandedPlayer.nextButton.getBackground();
        Drawable favBg = binding.expandedPlayer.favoriteButton.getBackground();
        Drawable saveBg = binding.expandedPlayer.saveButton.getBackground();
        Drawable prevBg = binding.expandedPlayer.previousButton.getBackground();

        GradientDrawable d3 =
                (GradientDrawable) binding.expandedPlayer.songInfoText.getBackground();

        MaterialShapeDrawable d = (MaterialShapeDrawable) binding.expandedPlayer.floatingToolbarLayout.getBackground();

        XSeekbar seekbar = binding.expandedPlayer.songSeekbar;

        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(500);
        va.addUpdateListener(
                a -> {
                    float f = (float) a.getAnimatedValue();
                    int iop = XUtils.interpolateColor(oldOnPrimary, onPrimary, f);
                    int ip = XUtils.interpolateColor(oldPrimary, primary, f);
                    int iot = XUtils.interpolateColor(oldOnTertiary, onTertiary, f);
                    int it = XUtils.interpolateColor(oldTertiary, tertiary, f);
                    int is = XUtils.interpolateColor(oldSurface, surface, f);
                    int isc = XUtils.interpolateColor(oldSurfaceContainer, surfaceContainer, f);
                    int io = XUtils.interpolateColor(oldOutline, outline, f);
                    int iosc = XUtils.interpolateColor(oldOnSurfaceContainer, onSurfaceContainer, f);
                    int ios = XUtils.interpolateColor(oldOnSurface, onSurface, f);

                    LiveColors.primary = ip;
                    LiveColors.onPrimary = iop;
                    LiveColors.tertiary = it;
                    LiveColors.onTertiary = iot;
                    LiveColors.surface = is;
                    LiveColors.surfaceContainer = isc;
                    LiveColors.outline = io;
                    LiveColors.onSurface = ios;
                    LiveColors.onSurfaceContainer = iosc;

                    binding.expandedPlayer.toggleView.setShapeColor(iop);
                    binding.expandedPlayer.toggleView.setIconColor(ip);
                    binding.lyricsView.setLyricColor(ios);
                    binding.placeholderLyricsText.setTextColor(ios);

                    binding.expandedPlayer.nextButton.setIconColorFilter(it);
                    binding.expandedPlayer.favoriteButton.setIconColorFilter(it);
                    binding.expandedPlayer.saveButton.setIconColorFilter(it);
                    binding.expandedPlayer.previousButton.setIconColorFilter(it);

                    nextBg.setColorFilter(new PorterDuffColorFilter(iot, PorterDuff.Mode.SRC_IN));
                    favBg.setColorFilter(new PorterDuffColorFilter(iot, PorterDuff.Mode.SRC_IN));
                    saveBg.setColorFilter(new PorterDuffColorFilter(iot, PorterDuff.Mode.SRC_IN));
                    prevBg.setColorFilter(new PorterDuffColorFilter(iot, PorterDuff.Mode.SRC_IN));

                    playerSurface = is;

                    binding.miniPlayer.setSheetBackgroundColor(playerSurface);
                    binding.lyricsContainer.setBackgroundColor(playerSurface);

                    binding.expandedPlayer.songInfoLayout.setColor(isc);

                    binding.collapsedPlayer.musicProgress.setIndicatorColor(ip);
                    seekbar.setColor(ip);

                    binding.collapsedPlayer.action.setIconTint(ColorStateList.valueOf(iop));
                    binding.collapsedPlayer.action.setBackgroundColor(ip);
                    binding.collapsedPlayer.action.setRippleColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(io, 100)));

                    d.setFillColor(ColorStateList.valueOf(isc));

                    binding.expandedPlayer.lyricsButton.setIconTint(ColorStateList.valueOf(isOledTheme? 0xffbdbdbd : iosc));
                    binding.expandedPlayer.lyricsButton.setRippleColor(ColorStateList.valueOf(io));

                    binding.expandedPlayer.repeatModeButton.setIconTint(ColorStateList.valueOf(isOledTheme? 0xffbdbdbd : iosc));
                    binding.expandedPlayer.repeatModeButton.setRippleColor(ColorStateList.valueOf(io));

                    binding.expandedPlayer.artistBigTitle.setTextColor(iosc);
                    binding.expandedPlayer.songBigTitle.setTextColor(ios);

                    binding.collapsedPlayer.title.setTextColor(ios);
                    binding.collapsedPlayer.subtitle.setTextColor(io);

                    binding.expandedPlayer.currentDurationText.setTextColor(iosc);
                    binding.expandedPlayer.totalDurationText.setTextColor(iosc);
                    binding.expandedPlayer.songInfoText.setTextColor(iosc);
                });
        va.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationCancel(Animator animation) {
                        canceled = true;
                    }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!canceled) {
                            effectiveOldColors = new HashMap<>(colors);
                }
            }
        });
        if (colorAnimator != null) {
            colorAnimator.cancel();
        }

        colorAnimator = va;
        va.start();
    }

    public void updateContent(int position, boolean isResuming) {
        if (position == -1) {
            if (activity.getController() == null) return;
            else position = activity.getController().getCurrentMediaItemIndex();
        }
        if (position >= 0 && !RuntimeData.songs.isEmpty() && position < RuntimeData.songs.size()) syncPlayerUI(position, isResuming);
        loadLyrics(RuntimeData.songs.get(position).path);
        if (isResuming && CallbackInterface.service() != null) {
            if (CallbackInterface.service().isPlaying())
                binding.expandedPlayer.toggleView.forcePlayState();
            else binding.expandedPlayer.toggleView.forcePauseState();
        }
    }

    public void syncPlayerUI(int position, boolean isResuming) {
        updateMaxValue(position, isResuming);
        updateCoverPager(position);
        binding.collapsedPlayer.title.setText(RuntimeData.songs.get(position).title);
        binding.collapsedPlayer.subtitle.setText(RuntimeData.songs.get(position).artist);
        if (!isResuming) {
            binding.expandedPlayer.artistBigTitle.animate().alpha(0f).translationX(-20f).setDuration(100).start();
            binding.expandedPlayer.songBigTitle.animate().alpha(0f).translationX(-20f).setDuration(100).start();
            binding.expandedPlayer.totalDurationText.animate().alpha(0f).translationX(-20f).setDuration(100).start();
            binding.expandedPlayer.currentDurationText.animate().alpha(0f).translationX(-20f).setDuration(100).start();
            if (!isResuming) binding.expandedPlayer.songInfoText.animate().alpha(0f).setDuration(100).start();
            handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> {
                updateSongInfoLayout(position, true);
                binding.expandedPlayer.totalDurationText.setTranslationX(20f);
                binding.expandedPlayer.currentDurationText.setTranslationX(20f);
                binding.expandedPlayer.songBigTitle.setTranslationX(20f);
                binding.expandedPlayer.artistBigTitle.setTranslationX(20f);
            }, 110);

        } else {
            updateTexts(position, false);
            updateSongInfoLayout(position, !isResuming);
        }
    }

    public void updateMaxValue(int pos, boolean isRestoring) {
        if (RuntimeData.songs.size() > 0 && activity.getController() != null) {
            int p = activity.getController().getCurrentMediaItemIndex();
            if (pos == -1) return;
            int max = (int) RuntimeData.songs.get(pos == -1 ? p : pos).duration;
            binding.expandedPlayer.songSeekbar.setMax(max);
            binding.collapsedPlayer.musicProgress.setMax(max);
        } else if (isRestoring) {
            int p = CallbackInterface.service().getCurrentPosition();
            if (p == -1) return;
            int max = (int) RuntimeData.songs.get(pos == -1 ? p : pos).duration;
            binding.expandedPlayer.songSeekbar.setMax(max);
            binding.collapsedPlayer.musicProgress.setMax(max);
        }
    }

    public void updateTexts(int pos, boolean isRestoring) {
        if (RuntimeData.songs.size() > 0 && activity.getController() != null) {
            if (pos == -1) return;
            binding.expandedPlayer.totalDurationText.setText(
                    RuntimeData.songs.get(pos).getFormattedDuration());
            binding.expandedPlayer.artistBigTitle.setText(RuntimeData.songs.get(pos).artist);
            binding.expandedPlayer.songBigTitle.setText(RuntimeData.songs.get(pos).title);

        } else if (isRestoring
                && CallbackInterface.service() != null
                && CallbackInterface.service().isAnythingPlaying()) {
            int p = CallbackInterface.service().getCurrentPosition();
            if (p == -1) return;

            binding.expandedPlayer.totalDurationText.setText(
                    RuntimeData.songs.get(p).getFormattedDuration());
            binding.expandedPlayer.artistBigTitle.setText(RuntimeData.songs.get(p).artist);
            binding.expandedPlayer.songBigTitle.setText(RuntimeData.songs.get(p).title);
        }
    }

    private void updateSongInfoLayout(int pos, boolean animate) {
        if (RuntimeData.songs.isEmpty()) return;
        int index = -1;
        if (pos == -1) {
            if (CallbackInterface.service() != null) {
                index = CallbackInterface.service().getCurrentPosition();
                if (index == -1) return;
            } else {
                return;
            }
        } else {
            index = pos;
        }
        final String path;
        try {
            path = RuntimeData.songs.get(index).path;
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
            return;
        }
        final long requestId = ++metadataRequestId;
        metadataExecutor.execute(() -> {
            String mime = "Unknown";
            int kbps = -1;
            String sampleRate = "Unknown";
            try {
                mime = XUtils.getAudioCodec(activity, Uri.fromFile(new File(path)));
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                mmr.setDataSource(path);
                String br = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
                String sr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE);
                mmr.release();
                if (br != null) {
                    kbps = Math.abs(Integer.parseInt(br) / 1000);
                }
                if (sr != null) {
                    int hz = Integer.parseInt(sr);
                    sampleRate = hz >= 1000 ? (hz / 1000f) + " kHz" : hz + " Hz";
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            final String finalMime = mime;
            final int finalKbps = kbps;
            final String finalSampleRate = sampleRate;
            binding.expandedPlayer.songInfoText.post(() -> {
                if (requestId != metadataRequestId) {
                    return;
                }
                String text = finalKbps > 0 ? finalMime + " • " + finalKbps + " kbps • " + finalSampleRate : finalMime + " • " + finalSampleRate;
                binding.expandedPlayer.songInfoText.setText(text);
                float effectiveWidth = Math.min(binding.expandedPlayer.songInfoText.getPaint().measureText(binding.expandedPlayer.songInfoText.getText().toString())  + binding.expandedPlayer.songInfoText.getPaddingLeft() + binding.expandedPlayer.songInfoText.getPaddingRight(), binding.expandedPlayer.songInfoText.getWidth());
                binding.expandedPlayer.songInfoLayout.update(effectiveWidth);
                updateTexts(pos, !animate);
                binding.expandedPlayer.songInfoText.post(() -> {
                    binding.miniPlayer.requestLayout();
                });
                binding.expandedPlayer.artistBigTitle.animate().alpha(1f).translationX(0f).setDuration(120).start();
                binding.expandedPlayer.songBigTitle.animate().alpha(1f).translationX(0f).setDuration(120).start();
                binding.expandedPlayer.currentDurationText.animate().alpha(1f).translationX(0f).setDuration(120).start();
                binding.expandedPlayer.totalDurationText.animate().alpha(1f).translationX(0f).setDuration(120).start();
                if (animate) binding.expandedPlayer.songInfoText.animate().alpha(1f).setDuration(120).start();
            });
        });
    }

    private void updateCoverPager(int index) {
        if (RuntimeData.songs.isEmpty()) return;
        if (activity.isDestroyed() || activity.isFinishing()) return;
        Uri cover = RuntimeData.songs.get(index).getArtworkUri();
        binding.collapsedPlayer.cover.load(cover);
    }

    public void updateTopProgress(float progress) {
        binding.collapsedPlayer.title.setAlpha(Math.max(0f, 1f - progress * 5));
        binding.collapsedPlayer.subtitle.setAlpha(Math.max(0f, 1f - progress * 5));
        binding.collapsedPlayer.action.setAlpha(Math.max(0f, 1f - progress * 5));
        binding.collapsedPlayer.musicProgress.setAlpha(Math.max(0f, 1f - progress * 20));
    }

    public void restoreCoverExpansion() {
        ViewKt.doOnLayout(binding.collapsedPlayer.cover, v -> {
            binding.collapsedPlayer.cover.setExpansionProgress(binding.miniPlayer.getSlideOffset());

            return Unit.INSTANCE;
        });
    }

    public void maybeRestoreUIState() {
        restoreRepeatButton();
        if (viewModel.isDataSaved()) {
            int savedState = viewModel.getLayoutState();

            ViewKt.doOnLayout(binding.bottomNavigation, v -> {
                ViewKt.doOnLayout(binding.tabLayout, v2 -> {
                    binding.miniPlayer.setFloatingMargins(sideMargins, savedState >= 4 ? XUtils.getNavigationBarHeight(activity) : bottomMargin);

                    setLayoutState(savedState, "restore state - viemodel");
                    updateColors();

                    return Unit.INSTANCE;
                });
                return Unit.INSTANCE;
            });

            if (CallbackInterface.service() != null && CallbackInterface.service().isAnythingPlaying()) {
                syncPlayerUI(CallbackInterface.service().getCurrentPosition(), true);
                if (CallbackInterface.mlFrag() != null) CallbackInterface.mlFrag().updateActiveItem(CallbackInterface.service().getCurrentPosition());
                if (CallbackInterface.srFrag() != null) CallbackInterface.srFrag().updateActiveItem(CallbackInterface.service().getCurrentPosition());

                boolean playing = CallbackInterface.service().isPlaying();
                activity.runOnUiThread(() -> {
                    if (CallbackInterface.mlFrag() != null) CallbackInterface.mlFrag().updateVumeter(playing);
                    if (CallbackInterface.srFrag() != null) CallbackInterface.srFrag().updateVumeter(playing);
                });
                if (playing) binding.expandedPlayer.toggleView.forcePlayState();
                binding.expandedPlayer.songSeekbar.setAnimate(playing);
                binding.collapsedPlayer.action.setIconResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
            }

            viewModel.markDataAsSaved(false);
        } else if (CallbackInterface.service() != null && CallbackInterface.service().isAnythingPlaying()) {
            syncPlayerUI(CallbackInterface.service().getCurrentPosition(), true);
            if (CallbackInterface.mlFrag() != null) CallbackInterface.mlFrag().updateActiveItem(CallbackInterface.service().getCurrentPosition());
            if (CallbackInterface.srFrag() != null) CallbackInterface.srFrag().updateActiveItem(CallbackInterface.service().getCurrentPosition());
            boolean playing = CallbackInterface.service().isPlaying();
            activity.runOnUiThread(() -> {
                if (CallbackInterface.mlFrag() != null) CallbackInterface.mlFrag().updateVumeter(playing);
                if (CallbackInterface.srFrag() != null) CallbackInterface.srFrag().updateVumeter(playing);
            });
            if (playing) binding.expandedPlayer.toggleView.forcePlayState();

            binding.expandedPlayer.songSeekbar.setAnimate(playing);
            binding.collapsedPlayer.action.setIconResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);

            ViewKt.doOnLayout(binding.bottomNavigation, v -> {
                ViewKt.doOnLayout(binding.tabLayout, v2 -> {
                    binding.miniPlayer.setFloatingMargins(sideMargins, bottomMargin);
                    setLayoutState(LAYOUT_STATE_EXPOSE_FULL, true, "restore state - service");
                    updateColors();
                    return Unit.INSTANCE;
                });
                return Unit.INSTANCE;
            });
        } else {
            ViewKt.doOnLayout(binding.bottomNavigation, v -> {
                ViewKt.doOnLayout(binding.tabLayout, v2 -> {
                    binding.miniPlayer.setFloatingMargins(sideMargins, bottomMargin);
                    setLayoutState(LAYOUT_STATE_EXPOSE_TABS_BNV, true, "restore state - null");

                    return Unit.INSTANCE;
                });
                return Unit.INSTANCE;
            });
        }
    }

    private void restoreRepeatButton() {
        if (activity == null || activity.getController() == null) return;
        switch (activity.getController().getRepeatMode()) {
            case Player.REPEAT_MODE_ALL -> {
                binding.expandedPlayer.repeatModeButton.setIconResource(R.drawable.ic_repeat);
                binding.expandedPlayer.repeatModeButton.setChecked(true);
            }
            case Player.REPEAT_MODE_ONE -> {
                binding.expandedPlayer.repeatModeButton.setIconResource(R.drawable.ic_repeat_one);
                binding.expandedPlayer.repeatModeButton.setChecked(true);
            }
            case Player.REPEAT_MODE_OFF -> {
                binding.expandedPlayer.repeatModeButton.setIconResource(R.drawable.ic_repeat_off);
                binding.expandedPlayer.repeatModeButton.setChecked(false);
            }
            default -> {
                throw new IllegalStateException("Player repeat mode:" + String.valueOf(activity.getController().getRepeatMode()) + " not handled");
            }
        }
    }

    public void saveState() {
        viewModel.setLastPosition(activity.getController().getCurrentMediaItemIndex());
        computeState();
        viewModel.saveLayoutState(layoutState);
        viewModel.setBNVAsHidden(bnvHidden);
        viewModel.saveBNVPosition(activity.getBinding().bottomNavigation.getSelectedItemId());
    }

    public void loadLyrics(String path) {
        LyricsExtractor.extract(path, lyrics -> {
            if (lyrics != null && !lyrics.isEmpty()) {
                LyricsParser.parse(lyrics, result -> {
                    binding.lyricsView.post(() -> {
                        binding.lyricsView.setLyrics(result.lines());
                        binding.lyricsView.setListener(UIManager.this);

                        MaterialFadeThrough mft = new MaterialFadeThrough();
                        mft.setDuration(300);
                        TransitionManager.beginDelayedTransition(binding.containerRoot);
                        binding.lyricsPlaceholder.setVisibility(View.GONE);
                        binding.lyricsView.setVisibility(View.VISIBLE);
                    });
                });
            } else {
                MaterialFadeThrough mft = new MaterialFadeThrough();
                mft.setDuration(300);
                TransitionManager.beginDelayedTransition(binding.containerRoot);
                binding.lyricsView.setVisibility(View.GONE);
                binding.lyricsPlaceholder.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onSeekRequested(long ms) {
        if (activity.getController() != null) {
            activity.getController().seekTo(ms);
        }
    }

    public void loadSettings() {
        isOledTheme = XUtils.isDarkMode(activity) && DataManager.isOledThemeEnabled();
        if (isOledTheme) binding.gradientView.setVisibility(View.GONE);
        isBlurOn = DataManager.isBlurOn();
        if (XUtils.areBlursOrDynamicColorsSupported() && !isBlurOn) binding.Coordinator.setRenderEffect(null);
        updateFontConfig();
    }

    public void updateLyrics() {
        binding.lyricsView.setStaticScroll(DataManager.getStaticScrollState());
        binding.lyricsView.setUserStaticScroll(DataManager.getUserStaticScrollState());
        binding.lyricsView.setEnableSparkles(DataManager.getUseSparklesState());
        binding.lyricsView.setLyricAnticipation(DataManager.getLyricsAnticipationState());
        binding.gradientView.setVisibility((DataManager.sp.getBoolean("enable_lyrics_gradient", false) && !isOledTheme )? View.VISIBLE : View.GONE);
        binding.lyricsView.setEnableBlurs(DataManager.getLyricsBlurState());
    }

    public void updateFontConfig() {
        binding.lyricsView.setFontConfig(DataManager.getFontConfig());
    }
}
