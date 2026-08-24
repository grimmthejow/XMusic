package com.xapps.media.xmusic.activity.manager;

import android.content.ComponentName;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.SeekBar;

import androidx.activity.BackEventCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.ViewKt;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.transition.TransitionManager;
import androidx.transition.TransitionSeekController;

import com.google.android.material.search.SearchView;
import com.google.android.material.transition.MaterialFadeThrough;
import com.xapps.media.xmusic.R;
import com.xapps.media.xmusic.activity.RootActivity;
import com.xapps.media.xmusic.activity.controller.ActivityMediaController;
import com.xapps.media.xmusic.callback.CallbackInterface;
import com.xapps.media.xmusic.data.DataManager;
import com.xapps.media.xmusic.data.RuntimeData;
import com.xapps.media.xmusic.databinding.ActivityRootBinding;
import com.xapps.media.xmusic.service.XPlayerService;
import com.xapps.media.xmusic.utils.XUtils;
import com.xapps.media.xmusic.widget.ExpressiveSliderLayout;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import kotlin.Unit;

public class LogicManager {
    private final RootActivity activity;
    private final ActivityRootBinding binding;
    private final UIManager uiManager;
    private MediaController mediaController;
    private SessionToken sessionToken;
    private ActivityMediaController controller;

    public int mlfState = UIManager.LAYOUT_STATE_EXPOSE_TABS_BNV;
    public int srfState = UIManager.LAYOUT_STATE_EXPOSE_BNV;
    public int sgfState = UIManager.LAYOUT_STATE_EXPOSE_BNV;

    private boolean isUserSeeking;

    private boolean validCallback = false;

    private OnBackPressedCallback lyricsCallback;
    private TransitionSeekController seekController;

    public LogicManager(RootActivity activity, UIManager uiManager) {
        this.activity = activity;
        this.binding = activity.getBinding();
        this.uiManager = uiManager;
    }

    public void initLogic() {
        setupListeners();
        setupCallbacks();
    }

    private void setupListeners() {
        binding.collapsedPlayer.cover.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                binding.collapsedPlayer.cover.captureCollapsedBounds();
            }
        });

        binding.maximumSizeView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {

                int size = binding.maximumSizeView.getMaximumSize();

                View v2 = binding.maximumSizeView;
                int leftNow = v2.getLeft();
                int topNow = v2.getTop();
                int x = leftNow + (v2.getWidth() - size) / 2;

                binding.collapsedPlayer.cover.setExpandedBounds(x, topNow, x + size, topNow + size);

                binding.collapsedPlayer.cover.setExpansionProgress(Math.max(0f, binding.miniPlayer.getSlideOffset()));
            }
        });

        binding.expandedPlayer.songSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                binding.expandedPlayer.currentDurationText.setText(XUtils.millisecondsToDuration(seekBar.getProgress()));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> isUserSeeking = false, 100);
                mediaController.seekTo(seekBar.getProgress());
            }
        });

        binding.expandedPlayer.songSeekbar.setOnClickListener(v -> {});

        binding.expandedPlayer.previousButton.setOnHoldListener(R.drawable.ic_rewind_10, () -> {
            mediaController.seekTo(mediaController.getCurrentPosition() - 10000);
        });

        binding.expandedPlayer.nextButton.setOnHoldListener(R.drawable.ic_fast_forward_10, () -> {
            mediaController.seekTo(mediaController.getCurrentPosition() + 10000);
        });

        binding.expandedPlayer.previousButton.setOnClickListener(v -> {
            mediaController.seekToPrevious();
        });

        binding.expandedPlayer.nextButton.setOnClickListener(v -> {
            mediaController.seekToNext();
        });

        binding.expandedPlayer.toggleView.setExtraOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View _view) {
                if (!binding.expandedPlayer.toggleView.isAnimating()) {
                    mediaController.pause();
                    binding.expandedPlayer.songSeekbar.setAnimate(false);
                } else {
                    mediaController.play();
                    binding.expandedPlayer.songSeekbar.setAnimate(true);
                }
            }
        });

        binding.collapsedPlayer.action.setOnClickListener(v -> {
            if (mediaController.isPlaying()) {
                mediaController.pause();
            } else {
                mediaController.play();
            }
            binding.collapsedPlayer.action.setIconResource(mediaController.isPlaying()? R.drawable.ic_pause : R.drawable.ic_play);
        });

        MaterialFadeThrough transition = new MaterialFadeThrough();
        transition.setDuration(500);

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            uiManager.viewModel.saveBNVPosition(activity.getBinding().bottomNavigation.getSelectedItemId());
            if (CallbackInterface.srFrag() == null) return false;
            if (CallbackInterface.srFrag().getSearchViewState() == SearchView.TransitionState.SHOWING) return false;
            int id = item.getItemId();
            CallbackInterface.srFrag().hideSearchView();
            if (id == R.id.menuHomeFragment) {
                TransitionManager.beginDelayedTransition(binding.Coordinator, transition);
                binding.searchCard.setVisibility(View.GONE);
                binding.settingsCard.setVisibility(View.GONE);
                binding.rootCard.setVisibility(View.VISIBLE);
                binding.tabLayout.setEnabled(true);
                binding.tabLayout.setVisibility(View.VISIBLE);

                if (CallbackInterface.srFrag() != null) CallbackInterface.srFrag().freeze(true);
                if (CallbackInterface.sgFrag() != null) CallbackInterface.sgFrag().freeze(true);
                if (CallbackInterface.mlFrag() != null) CallbackInterface.mlFrag().freeze(false);

                return true;
            } else if (id == R.id.menuSearchFragment) {
                TransitionManager.beginDelayedTransition(binding.Coordinator, transition);
                binding.searchCard.setVisibility(View.VISIBLE);
                binding.rootCard.setVisibility(View.GONE);
                binding.tabLayout.setVisibility(View.INVISIBLE);
                binding.tabLayout.setEnabled(false);
                binding.settingsCard.setVisibility(View.GONE);

                if (CallbackInterface.srFrag() != null) CallbackInterface.srFrag().freeze(false);
                if (CallbackInterface.sgFrag() != null) CallbackInterface.sgFrag().freeze(true);
                if (CallbackInterface.mlFrag() != null) CallbackInterface.mlFrag().freeze(true);

                return true;
            } else if (id == R.id.menuSettingsFragment) {
                TransitionManager.beginDelayedTransition(binding.Coordinator, transition);
                binding.searchCard.setVisibility(View.GONE);
                binding.rootCard.setVisibility(View.GONE);
                binding.tabLayout.setVisibility(View.INVISIBLE);
                binding.tabLayout.setEnabled(false);
                binding.settingsCard.setVisibility(View.VISIBLE);

                if (CallbackInterface.srFrag() != null) CallbackInterface.srFrag().freeze(true);
                if (CallbackInterface.sgFrag() != null) CallbackInterface.sgFrag().freeze(false);
                if (CallbackInterface.mlFrag() != null) CallbackInterface.mlFrag().freeze(true);

                return true;
            }

            return false;
        });

        ViewKt.doOnLayout(binding.bottomNavigation, v -> {
            binding.bottomNavigation.setSelectedItemId(uiManager.viewModel.loadBNVPosition());
            uiManager.viewModel.saveBNVPosition(binding.bottomNavigation.getSelectedItemId());

            return Unit.INSTANCE;
        });

        binding.expandedPlayer.lyricsButton.setOnClickListener(v -> {
            boolean checked = binding.expandedPlayer.lyricsButton.isChecked();
            if (binding.lyricsContainer.getVisibility() != View.GONE && !(binding.lyricsContainer.getVisibility() == View.VISIBLE && binding.lyricsContainer.getAlpha() == 1f)) {
                binding.expandedPlayer.lyricsButton.setChecked(!checked);
                return;
            }

            if (checked) {
                binding.miniPlayer.setDraggable(false);
                if (DataManager.getKeepScreenAwakeState()) activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                binding.lyricsContainer.animate().alpha(1f).translationY(0f).setDuration(300L).withStartAction(() -> {
                    if (binding.lyricsContainer.getParent() == null) binding.miniPlayer.addView(binding.lyricsContainer);
                    binding.lyricsContainer.setVisibility(View.VISIBLE);
                }).start();
            } else {
                if (DataManager.getKeepScreenAwakeState()) activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                binding.miniPlayer.setDraggable(true);
                binding.lyricsContainer.animate().alpha(0f).translationY(150f).setDuration(300L).withEndAction(() -> {
                    binding.lyricsContainer.setVisibility(View.GONE);
                    if (binding.lyricsContainer.getParent() != null) binding.miniPlayer.removeView(binding.lyricsContainer);
                }).start();
            }

            lyricsCallback.setEnabled(checked);
        });
    }

    private void setupCallbacks() {
        binding.miniPlayer.setupPredictiveBack(activity);
        binding.miniPlayer.addSliderCallback(new ExpressiveSliderLayout.SliderCallback() {
            @Override
            public void onStateChanged(int state) {
                binding.miniPlayer.getPredictiveBackCallback().setEnabled(!(state == ExpressiveSliderLayout.STATE_COLLAPSED || state == ExpressiveSliderLayout.STATE_HIDDEN));
                if (state == ExpressiveSliderLayout.STATE_HIDDEN) {
                    uiManager.onPlayerHidden();
                    mediaController.stop();
                    mediaController.clearMediaItems();
                }
            }

            @Override
            public void onSwipe(boolean toRight) {
                if (!toRight) mediaController.seekToNext();
                else mediaController.seekToPrevious();
            }

            @Override
            public void onSlide(float offset) {
                binding.layoutScrim.setAlpha(Math.max(0f, offset) * 0.7f);
                uiManager.updateTopProgress(Math.max(0f, offset));
                updateImageSize(offset);
                binding.collapsedPlayer.cover.setExpansionProgress(Math.max(0f, offset));
            }
        });



        lyricsCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackStarted(BackEventCompat backEvent) {
                validCallback = binding.lyricsContainer.getAlpha() == 1f;
            }

            @Override
            public void handleOnBackProgressed(BackEventCompat backEvent) {
                if (!validCallback) return;

                binding.lyricsContainer.setAlpha(1f - backEvent.getProgress());
                binding.lyricsContainer.setTranslationY(150f*backEvent.getProgress());
            }

            @Override
            public void handleOnBackPressed() {
                if (!validCallback && XUtils.predictiveBackSupported()) return;
                binding.lyricsContainer.animate().alpha(0f).translationY(150f).setDuration((XUtils.predictiveBackSupported()? 150L : 300L)).withEndAction(() -> {
                    binding.lyricsContainer.setVisibility(View.GONE);
                    if (binding.lyricsContainer.getParent() != null) binding.miniPlayer.removeView(binding.lyricsContainer);
                }).start();
                lyricsCallback.setEnabled(false);
                binding.expandedPlayer.lyricsButton.setChecked(false);
                binding.miniPlayer.setDraggable(true);

                if (DataManager.getKeepScreenAwakeState()) activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }

            @Override
            public void handleOnBackCancelled() {
                binding.lyricsContainer.animate().alpha(1f).translationY(0f).setDuration(100L).withStartAction(() -> binding.lyricsContainer.setVisibility(View.VISIBLE)).start();
            }
        };

        activity.getOnBackPressedDispatcher().addCallback(activity, lyricsCallback);
    }

    public void initController(FragmentActivity activity, Consumer<MediaController> onReady, Consumer<Throwable> onError, Runnable onRestore) {

        if (sessionToken == null) {
            sessionToken = new SessionToken(activity, new ComponentName(activity, XPlayerService.class));
        }

        controller = new ActivityMediaController(activity, sessionToken);

        controller.initialize(c -> {
            mediaController = c;
            controller.setupListener((RootActivity) activity);
            onReady.accept(c);
        }, e -> onError.accept(e), onRestore);
    }

    public void playSong(int position) {
        if (mediaController.getPlaybackState() == Player.STATE_BUFFERING) return;

        String songPath = RuntimeData.songs.get(position).path;
        uiManager.loadLyrics(songPath);
        if (!samePlaylistByPath(mediaController, CallbackInterface.service().getMediaItems())) {
            mediaController.setMediaItems(CallbackInterface.service().getMediaItems(), position, 0);
            mediaController.play();
        } else {
            mediaController.seekTo(position, 0);
            mediaController.play();
        }
        binding.expandedPlayer.toggleView.forcePlayState();
    }

    private static boolean samePlaylistByPath(MediaController controller, List<MediaItem> serviceItems) {
        int count = controller.getMediaItemCount();
        if (count != serviceItems.size()) return false;

        for (int i = 0; i < count; i++) {
            MediaItem cItem = controller.getMediaItemAt(i);
            MediaItem sItem = serviceItems.get(i);

            String cPath = cItem.localConfiguration.uri.getPath();
            String sPath = sItem.localConfiguration.uri.getPath();

            if (!Objects.equals(cPath, sPath)) return false;
        }
        return true;
    }

    public void updateVumeters(boolean isPlaying) {
        activity.runOnUiThread(() -> {
            if (CallbackInterface.mlFrag() != null) CallbackInterface.mlFrag().updateVumeter(isPlaying);
            if (CallbackInterface.srFrag() != null) CallbackInterface.srFrag().updateVumeter(isPlaying);
        });
    }

    private void updateImageSize(float offset) {
        float clampedOffset = Math.max(0f, offset);
    }

    public void handleProgress(long progress) {
        if (true) activity.runOnUiThread(() -> updateProgress(progress));
        activity.runOnUiThread(() -> binding.lyricsView.updateLyricsProgress((int) progress, false));
    }

    public void updateProgress(long position) {
        binding.collapsedPlayer.musicProgress.setProgressCompat((int) position, true);
        if (!isUserSeeking) binding.expandedPlayer.songSeekbar.setProgress((int) position, false);
    }
}
