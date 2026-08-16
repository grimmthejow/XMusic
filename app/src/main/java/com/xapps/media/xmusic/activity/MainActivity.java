package com.xapps.media.xmusic.activity;

import android.animation.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import androidx.activity.*;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.transition.*;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.search.SearchView;
import com.google.android.material.transition.MaterialFadeThrough;
import com.google.common.util.concurrent.ListenableFuture;
import com.xapps.media.xmusic.R;
import com.xapps.media.xmusic.activity.controller.ActivityMediaController;
import com.xapps.media.xmusic.activity.manager.BottomSheetBehaviorManager;
import com.xapps.media.xmusic.callback.ActivityCallback;
import com.xapps.media.xmusic.callback.CallbackInterface;
import com.xapps.media.xmusic.common.PlaybackControlListener;
import com.xapps.media.xmusic.common.SongLoadListener;
import com.xapps.media.xmusic.data.*;
import com.xapps.media.xmusic.databinding.ActivityMainBinding;
import com.xapps.media.xmusic.fragment.*;
import com.xapps.media.xmusic.helper.BnvRadiusHelper;
import com.xapps.media.xmusic.helper.ServiceCallback;
import com.xapps.media.xmusic.helper.SongMetadataHelper;
import com.xapps.media.xmusic.helper.SongSorter;
import com.xapps.media.xmusic.helper.ViewPlacementHelper;
import com.xapps.media.xmusic.lyric.*;
import com.xapps.media.xmusic.models.*;
import com.xapps.media.xmusic.service.PlayerService;
import com.xapps.media.xmusic.service.XPlayerService;
import com.xapps.media.xmusic.utils.*;
import com.xapps.media.xmusic.viewmodel.MainActivityViewModel;
import com.xapps.media.xmusic.widget.ExpressiveSliderLayout;
import com.xapps.media.xmusic.widget.ModularScrimView;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import kotlin.*;

public class MainActivity extends AppCompatActivity implements ServiceCallback, PlaybackControlListener, ActivityCallback {

    public MusicListFragment musicListFragment;
    public SearchFragment searchFragment;
    private SettingsFragment settingsFragment;
    private ActivityMainBinding binding;
    private MediaController mediaController;
    private final ExecutorService metadataExecutor = Executors.newSingleThreadExecutor();
    private volatile long metadataRequestId;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private ArrayList<HashMap<String, Object>> songsMap = new ArrayList<>();
    private Context context = this;
    public Handler handler, backgroundHandler;
    public boolean isRestoring, wasAdjusted, seekbarFree, isBnvHidden, isPlayerHidden, isColorAnimated, isAnimated, isBsInvisible, isOledTheme, isResuming, bound, isBlurOn, isCallbackValid, isCreated = false;
    private ListenableFuture<MediaController> controllerFuture;
    private SessionToken sessionToken;
    private PlayerService service;
    private MainActivityViewModel viewmodel;
    public BottomSheetBehavior innerBottomSheetBehavior;
    public BottomSheetBehavior bottomSheetBehavior;
    private HandlerThread handlerThread = new HandlerThread("BackgroundThread");
    public SquigglyProgress progressDrawable;
    public int statusBarHeight, navBarHeight, bsbHeight, bottomSheetColor, tmpColor, playerSurface;
	public int bnvHeight;
    private long lastClick;
    public float currentSlideOffset, tmpY;
    public OnBackPressedCallback callback, callback2, callback3;
    private TransitionSeekController controller;
    private CustomTarget<Drawable> coverTarget;
    private ValueAnimator colorAnimator;
    private Map<String, Integer> effectiveOldColors = new HashMap<>();
    public BnvRadiusHelper helper;
    
    private BottomSheetBehaviorManager bsbManager;
    
    public UIState playerState = UIState.HIDDEN;
	
	private ActivityMediaController mController;
    
    public enum UIState {
        HIDDEN,
        DOCKED,
        COLLAPSED,
        MOVING,
        EXPANDED
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        saveUIState();
        super.onSaveInstanceState(outState);
    }

    @Override
	protected void onCreate(Bundle bundle) {
        isCreated = true;
        EdgeToEdge.enable(this);
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        XUtils.updateTheme();
        XUtils.applyDynamicColors(this, DataManager.isOledThemeEnabled());
        if (XUtils.isDarkMode(this) && DataManager.isOledThemeEnabled())getTheme().applyStyle(R.style.ThemeOverlay_XMusic_OLED, true);
        super.onCreate(bundle);
		binding = ActivityMainBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());
        int resourceId = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        int resourceId2 = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
		if (resourceId > 0 && resourceId2 > 0) {
            statusBarHeight = context.getResources().getDimensionPixelSize(resourceId2);
            navBarHeight = context.getResources().getDimensionPixelSize(resourceId);
		}
        loadSongs();
		initialize();
	}
    
    @Override
	protected void onResume() {
        CallbackInterface.setActivityCallback(this);
        binding.currentSongTitle.resetMarquee();
        binding.currentSongArtist.resetMarquee();
        binding.artistBigTitle.resetMarquee();
        binding.songBigTitle.resetMarquee();
	    super.onResume();
        isResuming = true;
        if (mediaController != null) {
            if (mediaController.getMediaItemCount() > 0) binding.xlyricsView.onProgress((int) mediaController.getCurrentPosition());
            updateProgress(mediaController.getCurrentPosition());
            syncPlayerUI(mediaController.getCurrentMediaItemIndex());
            binding.getRoot().post(() -> updateColors());
            if (!mediaController.isPlaying() && binding.toggleView.isAnimating()) {
                binding.toggleView.stopAnimation();
                progressDrawable.setAnimate(false);
            } else if (mediaController.isPlaying() && !binding.toggleView.isAnimating()) {
                binding.toggleView.startAnimation();
                progressDrawable.setAnimate(true);
            }
        }
        isResuming = false;
    }
    
    @Override
	public void onPause() {
        CallbackInterface.clearActivityCallback(this);
		super.onPause();
	}
    
    @Override
    public void onStart() {
        super.onStart();
        if (!XUtils.isDarkMode(this)) setDarkStatusBar(getWindow(), true);
        CallbackInterface.setActivityCallback(this);
        if (sessionToken == null) sessionToken = new SessionToken(context, new ComponentName(context, XPlayerService.class));
		mController = new ActivityMediaController(this, sessionToken);
		mController.initialize(controller -> {
			mediaController = controller;
			progressDrawable.setAnimate(mediaController.isPlaying());
            setupControllerListener();
		}, e -> showInfoDialog("Error", 0, e.toString(), "OK"), this::restoreStateIfPossible);
        
    }
    
    public void updateAdapters(int position, boolean isPlaying) {
        musicListFragment.updateActiveItem(position);
        if (searchFragment != null) searchFragment.updateActiveItem(position);
    }
    
    public void setupControllerListener() {
		//mController.setupListener(this);
    }
    
    @Override
    public void onDestroy() {
        CallbackInterface.clearActivityCallback(this);
        saveUIState();
        super.onDestroy();
        ServiceCallback.Hub.set(null);
        mediaController.release();
		mediaController = null;
    }
    
    private void initialize() {
        viewmodel = new ViewModelProvider(this).get(MainActivityViewModel.class);
        handler = new Handler(Looper.getMainLooper());
        //bottomSheetBehavior = BottomSheetBehavior.from(binding.miniPlayerBottomSheet);
		ViewKt.doOnLayout(binding.bottomNavigation, v -> {
			binding.bottomNavigation.setSelectedItemId(viewmodel.loadBNVPosition());
            bnvHeight = binding.bottomNavigation.getHeight() - XUtils.getNavigationBarHeight(context);
			XUtils.increaseMargins(binding.musicProgress, 0, 0, 0, navBarHeight);
			binding.expressiveBottomSheet.setPeekHeight(binding.coversPager.getHeight() + XUtils.convertToPx(context, 18f) /*+ binding.bottomNavigation.getHeight()/*bottomSheetBehavior.getPeekHeight() + + navBarHeight*/);
            binding.expressiveBottomSheet.setSheetBackgroundColor(MaterialColorUtils.colorSurfaceContainer);
            int topRadius = binding.expressiveBottomSheet.getPeekHeight() / 2;
            int bottomRadius = XUtils.convertToPx(context, 4f);
            binding.expressiveBottomSheet.setFloatingCornerRadii(topRadius, topRadius, bottomRadius, bottomRadius);
            binding.expressiveBottomSheet.setFloatingMargins(XUtils.convertToPx(context, 12f), binding.bottomNavigation.getHeight() - XUtils.getNavigationBarHeight(context) + XUtils.convertToPx(context, 4f));
            
            new ViewPlacementHelper(binding.bnvScrim, binding.Coordinator).setHeight(binding.bottomNavigation.getHeight() + binding.coversPager.getHeight()*3).setVerticalConstraint(ViewPlacementHelper.Vertical.BOTTOM_TO_BOTTOM, 0).attach();
            binding.bnvScrim.setSolidColor(0x99000000);
            binding.bnvScrim.setDirection(ModularScrimView.Direction.BOTTOM_TO_TOP);
            binding.bnvScrim.setShapeMode(ModularScrimView.ShapeMode.LINEAR);
            
            helper = new BnvRadiusHelper(binding.bottomNavigation);
            helper.animateTopCorners(topRadius, 300);
            XUtils.increaseMargins(binding.bottomNavigation, 0, 0, 0, 0);
            
			return Unit.INSTANCE;
		});
        //bottomSheetBehavior.setHideable(true);
        binding.expressiveBottomSheet.setState(ExpressiveSliderLayout.STATE_HIDDEN);
        handlerThread.start();
        Looper looper = handlerThread.getLooper();
        backgroundHandler = new Handler(looper);
        MaterialColorUtils.initColors(this);
        setupUI();
		setupListeners();
        setupCallbacks();
        bsbManager = new BottomSheetBehaviorManager(this);
        bsbManager.setupCallbacks();
    }
    
    private void setupUI() {
        binding.currentSongTitle.setTypeface(binding.currentSongTitle.getTypeface(), Typeface.BOLD);
        binding.expressiveBottomSheet.setupPredictiveBack(this);
        progressDrawable = new SquigglyProgress();
        progressDrawable.setWaveLength(100);
		progressDrawable.setLineAmplitude(8);
		progressDrawable.setPhaseSpeed(25);
		progressDrawable.setStrokeWidth(XUtils.convertToPx(this, 4f));
		progressDrawable.setTransitionEnabled(true);
		progressDrawable.setAnimate(true);
		progressDrawable.setTint(MaterialColorUtils.colorPrimary);
        binding.songSeekbar.setProgressDrawable(progressDrawable);
		bottomSheetColor = MaterialColorUtils.colorSurfaceContainer;
        binding.extendableLayout.setPadding(0, 0, 0, navBarHeight);
        XUtils.setMargins(binding.coversPager, 0, XUtils.getStatusBarHeight(this)*5, 0, 0);
        bsbHeight = binding.expressiveBottomSheet.getPeekHeight();
        loadSettings();
	}
    
    public void setSong(int position, boolean ignored) {
        if (mediaController.getPlaybackState() == Player.STATE_BUFFERING) return;
        
        floatBnv(true);
        
        if (position == mediaController.getCurrentMediaItemIndex()) {
            mediaController.seekTo(position, 0);
            mediaController.play();
            binding.expressiveBottomSheet.setState(ExpressiveSliderLayout.STATE_COLLAPSED);
        }

        String songPath = RuntimeData.songs.get(position).path;
		loadLyrics(songPath);
        if (!samePlaylistByPath(mediaController, CallbackInterface.service().getMediaItems())) {
            mediaController.setMediaItems(CallbackInterface.service().getMediaItems(), position, 0);
            mediaController.play();
        } else {
            mediaController.seekTo(position, 0);
            mediaController.play();
        }
        binding.toggleView.forcePlayState();
        //mediaController.setPlayWhenReady(true);
    }
    
    private ValueAnimator bnvAnimator;
private boolean isBaseCaptured = false;
private int basePaddingBottom;
private int baseMarginBottom;
private boolean floating = false;

public void floatBnv(boolean show) {
    if (floating == show) return;
    floating = show;
    if (bnvAnimator != null) {
        bnvAnimator.cancel();
    }

    final View v = binding.bottomNavigation;
    ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();

    if (!isBaseCaptured) {
        basePaddingBottom = v.getPaddingBottom();
        baseMarginBottom = lp.bottomMargin;
        isBaseCaptured = true;
    }

    float start = show ? 0f : 1f;
    float end = show ? 1f : 0f;

    bnvAnimator = ValueAnimator.ofFloat(start, end);
    bnvAnimator.setDuration(300);

    bnvAnimator.addUpdateListener(a -> {
        float f = (float) a.getAnimatedValue();

        int newPadding = (int) (basePaddingBottom * (1f - f));
        int newMargin = (int) (baseMarginBottom + basePaddingBottom * f);

        v.setPadding(
                v.getPaddingLeft(),
                v.getPaddingTop(),
                v.getPaddingRight(),
                newPadding
        );

        lp.bottomMargin = newMargin;
        v.setLayoutParams(lp);
    });

    bnvAnimator.start();
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
    
    public void updateProgress(long position) {
        binding.musicProgress.setProgressCompat((int) position, true);
        binding.songSeekbar.setProgress((int) position, false);
        binding.currentDurationText.setText(XUtils.millisecondsToDuration(position));
    }
	
	private boolean exists;
    
    private void updateCoverPager(int index) {
        if (RuntimeData.songs.isEmpty()) return;
		if (isDestroyed() || isFinishing()) return;
        
        Uri cover = RuntimeData.songs.get(index).getArtworkUri();
        if (coverTarget != null) {
            Glide.with(this).clear(coverTarget);
        }

		backgroundHandler.post(() -> {
            exists = false;
			try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(cover, "r")) {
                exists = pfd != null;
            } catch (Exception ignored) {
				
            }
			coverTarget = new CustomTarget<Drawable>() {
            @Override
            public void onResourceReady(Drawable drawable, Transition<? super Drawable> transition) {
                crossfadeDrawable(binding.coversPager, drawable, 200);
            }

            @Override
            public void onLoadCleared(Drawable placeholder) {
                binding.coversPager.setImageDrawable(placeholder);
            }
        };
			
            handler.post(() -> {
				Glide.with(this)
            .asDrawable()
            .load(exists? cover : R.drawable.placeholder)
            .apply(new RequestOptions()
            .override(binding.miniPlayerBottomSheet.getWidth(), binding.miniPlayerBottomSheet.getWidth())
            .centerCrop()
            .priority(Priority.NORMAL))
            .into(coverTarget);
			});
		});

    }
    
    private void crossfadeDrawable(ImageView iv, Drawable next, int duration) {
        TransitionDrawable td = new TransitionDrawable(new Drawable[]{
            new ColorDrawable(Color.TRANSPARENT),
            next
        });
        td.setCrossFadeEnabled(true);
        iv.setImageDrawable(td);
        td.startTransition(duration);
    }
    
    public void updateSongsQueue(ArrayList<Song> s) {
        if (CallbackInterface.service() != null) CallbackInterface.service().updateSongs();
    }
    
    public void saveUIState() {
        viewmodel.markDataAsSaved(true);
        viewmodel.setBNVAsHidden(isBnvHidden);
        viewmodel.saveBNVPosition(binding.bottomNavigation.getSelectedItemId());
        if (mediaController != null) viewmodel.setLastPosition(mediaController.getCurrentMediaItemIndex());
    }
    
    private void restoreStateIfPossible() {
        if (!isCreated) return;
        isCreated = false;
        seekbarFree = true;
		Fragment f = getSupportFragmentManager().findFragmentById(R.id.settings_frag);
		boolean validshow = !(f.getClass() == SettingsFragment.class);
        if (viewmodel.isDataSaved()) {
            ColorPaletteUtils.darkColors = CallbackInterface.service().getDarkColors();
            ColorPaletteUtils.lightColors = CallbackInterface.service().getLightColors();
            isRestoring = true;
            int index = mediaController.getCurrentMediaItemIndex();
            if (index >= 0 && ColorPaletteUtils.lightColors != null && ColorPaletteUtils.darkColors != null) {
                updateColors();
            }
            if (mediaController.getMediaItemCount() > 0) {
				if (mediaController.isPlaying()) {
					binding.toggleView.forcePauseState();
					binding.toggleView.forcePlayState();
				} else {
					binding.toggleView.forcePauseState();
					binding.toggleView.forcePlayState();
				}
                updateAdapters(index, mediaController.isPlaying());
                updateProgress(mediaController.getCurrentPosition());
                updateColors();
                if (mediaController.getPlaybackState() == Player.STATE_READY) {
                    String songPath = RuntimeData.songs.get(mediaController.getCurrentMediaItemIndex()).path;
                    loadLyrics(songPath);
                }
                binding.bottomNavigation.postDelayed(() -> {
					syncPlayerUI(mediaController.getCurrentMediaItemIndex());
                    binding.expressiveBottomSheet.setState(ExpressiveSliderLayout.STATE_COLLAPSED);
                    isBnvHidden = true;
                    HideBNV(validshow);
                }, 100);
            } else {
                binding.bottomNavigation.postDelayed(() -> {
					syncPlayerUI(mediaController.getCurrentMediaItemIndex());
                    isBnvHidden = true;
                    HideBNV(validshow);
                }, 100);
            }
        } else if (CallbackInterface.service().isAnythingPlaying()) {
            ColorPaletteUtils.darkColors = CallbackInterface.service().getDarkColors();
            ColorPaletteUtils.lightColors = CallbackInterface.service().getLightColors();
			if (!CallbackInterface.service().isPlaying()) {
				binding.toggleView.forcePauseState();
			} else {
				binding.toggleView.forcePlayState();
			}
            updateColors();
            String songPath = RuntimeData.songs.get(mediaController.getCurrentMediaItemIndex()).path;
            loadLyrics(songPath);
            updateAdapters(mediaController.getMediaItemCount() > 0? mediaController.getCurrentMediaItemIndex() : -1, mediaController.isPlaying());
            binding.bottomNavigation.postDelayed(() -> {
				syncPlayerUI(mediaController.getCurrentMediaItemIndex());
                binding.expressiveBottomSheet.setState(ExpressiveSliderLayout.STATE_COLLAPSED);
                isBnvHidden = true;
                HideBNV(validshow);
            }, 100);
            updateProgress(mediaController.getContentPosition());
        } else {
            binding.bottomNavigation.postDelayed(() -> {
                isBnvHidden = true;
                HideBNV(false);
            }, 100);
        }
        isRestoring = false;
    }
    
    private void setupListeners() {
        binding.toggleView.setExtraOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				if (!binding.toggleView.isAnimating()) {
                    mediaController.pause();
                    progressDrawable.setAnimate(false);
				} else {
                    mediaController.play();
                    progressDrawable.setAnimate(true);
				}
			}
		});
        
        binding.miniPlayerBottomSheet.setOnClickListener(v -> {
            if (binding.expressiveBottomSheet.getState() != ExpressiveSliderLayout.STATE_EXPANDED) binding.expressiveBottomSheet.setState(ExpressiveSliderLayout.STATE_EXPANDED);
            binding.musicProgress.setAlpha(0f);
        });
        
        binding.favoriteButton.setOnClickListener(v -> {
            
        });
        
        binding.saveButton.setOnClickListener(v -> {
            
        });
        
        binding.lyricsButton.setOnClickListener(v -> {
            boolean b = binding.lyricsButton.isChecked();
            innerBottomSheetBehavior.setDraggable(!b);
            bottomSheetBehavior.setDraggable(!b);
            if (binding.lyricsContainer.getVisibility() != View.GONE && !(binding.lyricsContainer.getVisibility() == View.VISIBLE && binding.lyricsContainer.getAlpha() == 1f)) {
                binding.lyricsButton.setChecked(!b);
                return;
            }
            callback3.setEnabled(b);
            callback.setEnabled(!b);
            binding.lyricsContainer.setClickable(b);
            binding.lyricsContainer.setFocusable(b);
            binding.lyricsContainer.setFocusableInTouchMode(b);
            if (b) {
                binding.extendableLayout.animate().translationY(innerBottomSheetBehavior.getPeekHeight()).setDuration(300).start();
                binding.lyricsContainer.setAlpha(0f);
                binding.lyricsContainer.setScaleX(1.1f);
                binding.lyricsContainer.setScaleY(1.1f);
                binding.lyricsContainer.animate().alpha(1f).setDuration(150).withStartAction(() -> {
                    binding.lyricsContainer.setVisibility(View.VISIBLE);
                }).start();
                binding.lyricsContainer.animate().scaleY(1f).scaleX(1f).setDuration(140).start();
            } else {
                binding.lyricsContainer.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                    binding.lyricsContainer.setVisibility(View.GONE);
                }).start();
                binding.lyricsContainer.animate().scaleY(1.1f).scaleX(1.1f).setDuration(140).start();
            }
        });
        
        binding.songSeekbar.setOnClickListener(v -> {
            
        });

        binding.nextButton.setOnClickListener(v -> {
            mediaController.seekToNext();
        });
        binding.previousButton.setOnClickListener(v -> {
            mediaController.seekToPrevious();
        });

        binding.songSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    binding.currentDurationText.setText(XUtils.millisecondsToDuration((int)progress));
                    binding.musicProgress.setProgress(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                seekbarFree = false;
                progressDrawable.setAnimate(false);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mediaController.isPlaying()) progressDrawable.setAnimate(true);
                backgroundHandler.postDelayed(() -> {
					seekbarFree = true;
				}, 125);
                mediaController.seekTo(seekBar.getProgress());
            }
        });
        
            
        MaterialFadeThrough transition = new MaterialFadeThrough();
        transition.setDuration(500);

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            if (searchFragment.binding.searchView.getCurrentTransitionState() == SearchView.TransitionState.SHOWING) return false;
            int id = item.getItemId();
            searchFragment.binding.searchView.hide();
            if (id == R.id.menuHomeFragment) {
				if (musicListFragment != null) musicListFragment.freeze(false);
				if (settingsFragment != null) settingsFragment.freeze(true);
				if (searchFragment != null) searchFragment.freeze(true);
				TransitionManager.beginDelayedTransition(binding.Coordinator, transition);
                binding.searchFrag.setVisibility(View.GONE);
                binding.settingsFrag.setVisibility(View.GONE);
                binding.mainFrag.setVisibility(View.VISIBLE);
                return true;
            } else if (id == R.id.menuSearchFragment) {
				if (musicListFragment != null) musicListFragment.freeze(true);
				if (settingsFragment != null) settingsFragment.freeze(true);
				if (searchFragment != null) searchFragment.freeze(false);
				TransitionManager.beginDelayedTransition(binding.Coordinator, transition);
                binding.searchFrag.setVisibility(View.VISIBLE);
                binding.mainFrag.setVisibility(View.GONE);
                binding.settingsFrag.setVisibility(View.GONE);
                return true;
            } else if (id == R.id.menuSettingsFragment) {
				if (musicListFragment != null) musicListFragment.freeze(true);
				if (settingsFragment != null) settingsFragment.freeze(false);
				if (searchFragment != null) searchFragment.freeze(true);
				TransitionManager.beginDelayedTransition(binding.Coordinator, transition);
                binding.searchFrag.setVisibility(View.GONE);
                binding.mainFrag.setVisibility(View.GONE);
                binding.settingsFrag.setVisibility(View.VISIBLE);
                return true;
            }

            return false;
        });
    }
    
    private void setupCallbacks() {
        
        
        callback3 = new OnBackPressedCallback(false) {
            @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            @Override
            public void handleOnBackStarted(BackEventCompat backEvent) {
            }

            @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            @Override
            public void handleOnBackProgressed(BackEventCompat backEvent) {
                binding.xlyricsView.setScaleY(1f - 0.1f*backEvent.getProgress());
                binding.xlyricsView.setScaleX(1f - 0.1f*backEvent.getProgress());
            }

            @Override
            public void handleOnBackPressed() {
                binding.lyricsContainer.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                    binding.lyricsContainer.setVisibility(View.GONE);
                    binding.lyricsContainer.setAlpha(1f);
                    binding.xlyricsView.setTranslationY(0f);
                    binding.xlyricsView.setScaleY(1f);
                    binding.xlyricsView.setScaleX(1f);
                    
                }).start();
                binding.xlyricsView.animate().translationY(300f).setDuration(140).start();
                // bottomSheetBehavior.setDraggable(true); TODO : add setDraggable() in ExpressiveSliderLayout
                innerBottomSheetBehavior.setDraggable(true);
                callback3.setEnabled(false);
                callback.setEnabled(true);
                binding.lyricsButton.setChecked(false);
                binding.extendableLayout.animate().translationY(0).setDuration(300).start();
            }

            @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            @Override
            public void handleOnBackCancelled() {
                binding.xlyricsView.animate().alpha(1f).setDuration(100).start();
                binding.xlyricsView.animate().scaleY(1f).scaleX(1f).setDuration(100).start();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback3);
        
        callback2 = new OnBackPressedCallback(false) {
            @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            @Override
            public void handleOnBackStarted(BackEventCompat backEvent) {
            }

            @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            @Override
            public void handleOnBackProgressed(BackEventCompat backEvent) {
                binding.secondCoordinator.setScaleX(1f-0.1f*backEvent.getProgress());
                binding.secondCoordinator.setScaleY(1f-0.1f*backEvent.getProgress());
                binding.secondCoordinator.setTranslationY((binding.secondCoordinator.getHeight()*0.05f)*backEvent.getProgress());
            }

            @Override
            public void handleOnBackPressed() {
                binding.miniPlayerDetailsLayout.setAlpha(0);
                binding.secondCoordinator.animate().scaleX(1f).setDuration(200).start();
                binding.secondCoordinator.animate().scaleY(1f).setDuration(200).start();
                binding.secondCoordinator.animate().translationY(0).setDuration(200).start();
                innerBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                callback2.setEnabled(false);
            }

            @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            @Override
            public void handleOnBackCancelled() {
                binding.secondCoordinator.animate().scaleX(1f).setDuration(300).start();
                binding.secondCoordinator.animate().translationY(0).setDuration(200).start();
                binding.secondCoordinator.animate().scaleY(1f).setDuration(300).start();
                innerBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        };

        getOnBackPressedDispatcher().addCallback(this, callback2);
        callback = new OnBackPressedCallback(false) {
            @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            @Override
            public void handleOnBackStarted(BackEventCompat backEvent) {
                isCallbackValid = binding.expressiveBottomSheet.getState() == ExpressiveSliderLayout.STATE_EXPANDED;
            }

            @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            @Override
            public void handleOnBackProgressed(BackEventCompat backEvent) {
                if (isCallbackValid) bottomSheetBehavior.setScrollOffset(1f - 0.4f*backEvent.getProgress());
            }

            @Override
            public void handleOnBackPressed() {
                if (isCallbackValid) {
                    boolean b = binding.lyricsButton.isChecked();
                    if (b) binding.lyricsButton.performClick();
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    callback.setEnabled(false);
                    isCallbackValid = false;
                }
            }

            @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            @Override
            public void handleOnBackCancelled() {
                if (isCallbackValid) bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        };

        //getOnBackPressedDispatcher().addCallback(this, callback);
    }

    public void loadSongs() {
        executor.execute(() -> {
                SongMetadataHelper.getAllSongs(context, new SongLoadListener(){
                    @Override
                    public void onProgress(ArrayList<Song> songs, int count) {
                    
                    }
                    
                    @Override
                    public void onComplete(ArrayList<Song> songs) {
                        SongSorter.sort(songs, DataManager.getSongFilterType(), DataManager.isDescendingOrder(), sorted -> {
                            RuntimeData.songs = sorted;
                            PlayerService.songs = sorted;

                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (!sorted.isEmpty()) {
                                wasAdjusted = true;

                                    if (PlayerService.isPlaying) {
                                        updateCoverPager(PlayerService.currentPosition);
                                        binding.toggleView.startAnimation();
                                        syncPlayerUI(PlayerService.currentPosition);
                                    }
                                } else {
                                    XUtils.showMessage(context, "no songs found");
                                }
                            });
                        });
                    }
                });
           
        });
    }

    public void syncPlayerUI(int position) {
        updateMaxValue(position);
        updateCoverPager(position);

        if (!isResuming) {
            binding.artistBigTitle.animate().alpha(0f).translationX(-20f).setDuration(100).start();
            binding.songBigTitle.animate().alpha(0f).translationX(-20f).setDuration(100).start();
            binding.totalDurationText.animate().alpha(0f).translationX(-20f).setDuration(100).start();
            binding.currentDurationText.animate().alpha(0f).translationX(-20f).setDuration(100).start();

            handler = new Handler(Looper.getMainLooper());

            handler.postDelayed(() -> {
                updateTexts(position);
                updateSongInfoLayout(position);

                binding.totalDurationText.setTranslationX(20f);
                binding.currentDurationText.setTranslationX(20f);
                binding.songBigTitle.setTranslationX(20f);
                binding.artistBigTitle.setTranslationX(20f);
            }, 110);

            handler.postDelayed(() -> {
                binding.artistBigTitle.animate().alpha(1f).translationX(0f).setDuration(120).start();
                binding.songBigTitle.animate().alpha(1f).translationX(0f).setDuration(120).start();
                binding.currentDurationText.animate().alpha(1f).translationX(0f).setDuration(120).start();
                binding.totalDurationText.animate().alpha(1f).translationX(0f).setDuration(120).start();
            }, 120);

        } else {
            updateTexts(position);
            updateSongInfoLayout(position);
        }
    }
    
    private void updateSongsQueue() {
        
    }
    
    public ActivityMainBinding getBinding() {
		return binding;
	}
    
    public void showInfoDialog(String title, int icon, String Desc, String button) {
        MaterialAlertDialogBuilder m = new MaterialAlertDialogBuilder(this);
        m.setTitle(title);
        if (icon != 0) m.setIcon(icon);
        m.setMessage(Desc);
        m.setPositiveButton(button, (dialog, which) -> {
            dialog.dismiss();
        });
        m.setOnDismissListener(dialog -> {
            if (XUtils.areBlursOrDynamicColorsSupported() && DataManager.isBlurOn()) XUtils.animateBlur(binding.Coordinator, false, 50);
        });
        m.show();
        if (XUtils.areBlursOrDynamicColorsSupported() && DataManager.isBlurOn()) XUtils.animateBlur(binding.Coordinator, true, 300);
        
    }
    
    public void updateTexts(int pos) {
        if (RuntimeData.songs.size() > 0 && mediaController != null) {
            int p = mediaController.getCurrentMediaItemIndex();

            binding.totalDurationText.setText(RuntimeData.songs.get(pos == -1 ? p : pos).getFormattedDuration());
            binding.artistBigTitle.setText(RuntimeData.songs.get(pos == -1 ? p : pos).artist);
            binding.songBigTitle.setText(RuntimeData.songs.get(pos == -1 ? p : pos).title);
            binding.currentSongTitle.setText(RuntimeData.songs.get(pos == -1 ? p : pos).title);
            binding.currentSongArtist.setText(RuntimeData.songs.get(pos == -1 ? p : pos).artist);
    
        } else if (isRestoring || PlayerService.isPlaying) {
            int p = viewmodel.loadLastPosition();
    
            binding.totalDurationText.setText(RuntimeData.songs.get(pos == -1 ? p : pos).getFormattedDuration());
            binding.artistBigTitle.setText(RuntimeData.songs.get(pos == -1 ? p : pos).artist);
            binding.songBigTitle.setText(RuntimeData.songs.get(pos == -1 ? p : pos).title);
            binding.currentSongTitle.setText(RuntimeData.songs.get(pos == -1 ? p : pos).title);
            binding.currentSongArtist.setText(RuntimeData.songs.get(pos == -1 ? p : pos).artist);
    
            isRestoring = false;
        }
    }
    
    private void updateSongInfoLayout(int pos) {
        if (RuntimeData.songs.isEmpty()) return;

        int p = mediaController != null
            ? mediaController.getCurrentMediaItemIndex()
            : viewmodel.loadLastPosition();

        int index = pos == -1 ? p : pos;

        final String path;

        try {
            path = RuntimeData.songs.get(index).path;
        } catch (IndexOutOfBoundsException e) {
            return;
        }

        final long requestId = ++metadataRequestId;

        /*binding.songInfoText.animate()
            .alpha(0f)
            .setDuration(100)
            .start();*/

        metadataExecutor.execute(() -> {

            String mime = "Unknown";
            int kbps = -1;
            String sampleRate = "Unknown";

            try {
                mime = XUtils.getAudioCodec(
                    this,
                    Uri.fromFile(new File(path))
                );

                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                mmr.setDataSource(path);

                String br = mmr.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_BITRATE
                );

                String sr = mmr.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_SAMPLERATE
                );

                mmr.release();

                if (br != null) {
                    kbps = Math.abs(Integer.parseInt(br) / 1000);
                }

                if (sr != null) {
                int hz = Integer.parseInt(sr);
                    sampleRate = hz >= 1000
                        ? (hz / 1000f) + " kHz"
                        : hz + " Hz";
                }

            } catch (Exception ignored) { }

            final String finalMime = mime;
            final int finalKbps = kbps;
            final String finalSampleRate = sampleRate;

            binding.songInfoText.post(() -> {

                if (requestId != metadataRequestId) {
                    return;
                }

                String text = finalKbps > 0
                    ? finalMime + " • " + finalKbps + " kbps • " + finalSampleRate
                    : finalMime + " • " + finalSampleRate;

                binding.songInfoText.setText(text);
                /*binding.songInfoText.setAlpha(0f);
                binding.songInfoText.animate()
                    .alpha(1f)
                    .setDuration(120)
                    .start();*/
            });
        });
    }
    
    public void updateMaxValue(int pos) {
        if (RuntimeData.songs.size() > 0 && mediaController != null) {
            int p = mediaController.getCurrentMediaItemIndex();
			if (pos == -1 && p == -1) return;
            int max = (int) RuntimeData.songs.get(pos == -1? p : pos).duration;
            binding.songSeekbar.setMax(max);
            binding.musicProgress.setMax(max);
        } else if (isRestoring || PlayerService.isPlaying) {
            int p = PlayerService.currentPosition;
			if (pos == -1 && p == -1) return;
            int max = (int) RuntimeData.songs.get(pos == -1? p : pos).duration;
            binding.songSeekbar.setMax(max);
            binding.musicProgress.setMax(max);
            isRestoring = false;
        }
    }
    
    public void HideBNV(boolean hide) {
    if (isBnvHidden == hide || binding == null) return;
    isBnvHidden = hide;
    android.view.animation.Interpolator interpolator = new android.view.animation.PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f);
    
    ViewKt.doOnLayout(binding.bottomNavigation, v -> {
        if (hide) {
            binding.bottomNavigation.animate().alpha(0.5f).translationY(binding.bottomNavigation.getHeight() + XUtils.getNavigationBarHeight(context)).setDuration(300).setInterpolator(interpolator).start();
            
            /*android.animation.ValueAnimator marginAnim = android.animation.ValueAnimator.ofInt(0, binding.bottomNavigation.getHeight());
            marginAnim.addUpdateListener(animation -> binding.expressiveBottomSheet.setFloatingMargins(XUtils.convertToPx(context, 12f), binding.bottomNavigation.getHeight() - binding.bottomNavigation.getPaddingBottom() + XUtils.convertToPx(context, 4f) - (int) animation.getAnimatedValue()));
            marginAnim.setDuration(300).setInterpolator(interpolator);
            marginAnim.start();*/

            if (MusicListFragment.fab != null) MusicListFragment.fab.hide();
        } else {
            /*android.animation.ValueAnimator marginAnim = android.animation.ValueAnimator.ofInt(binding.expressiveBottomSheet.getFloatingMargin("bottom"), binding.bottomNavigation.getHeight() + (floating? XUtils.getNavigationBarHeight(context) : 0) + XUtils.convertToPx(context, 4f));
            marginAnim.addUpdateListener(animation -> binding.expressiveBottomSheet.setFloatingMargins(XUtils.convertToPx(context, 12f), (int) animation.getAnimatedValue()));
            marginAnim.setDuration(300).setInterpolator(interpolator);
            marginAnim.start();*/

            binding.bottomNavigation.animate().translationY(0).alpha(1f).setDuration(300).setInterpolator(interpolator).start();
            if (MusicListFragment.fab != null) MusicListFragment.fab.show();
        }
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(hide? XUtils.convertToPx(context, 4f) : binding.expressiveBottomSheet.getPeekHeight() / 2f, hide? binding.expressiveBottomSheet.getPeekHeight() / 2f : XUtils.convertToPx(context, 4f));
        animator.setDuration(300);
        animator.setInterpolator(interpolator);
        animator.addUpdateListener(animation -> {
            float currentValue = (float) animation.getAnimatedValue();
            binding.expressiveBottomSheet.setFloatingCornerRadii(binding.expressiveBottomSheet.getPeekHeight() / 2f, binding.expressiveBottomSheet.getPeekHeight() / 2f, currentValue, currentValue);
        });
        animator.start();

        return Unit.INSTANCE;
    });
}

        
    public void HideBNVOnly(boolean hide) {
        if (isBnvHidden == hide || binding == null) return;
        isBnvHidden = hide;
        android.view.animation.Interpolator interpolator = new PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f);
		ViewKt.doOnLayout(binding.bottomNavigation, v -> {
		    if (hide) {
                binding.bottomNavigation.animate().alpha(0.5f).translationY(binding.bottomNavigation.getHeight()).setDuration(300).setInterpolator(interpolator).start();
			    if (MusicListFragment.fab != null) MusicListFragment.fab.hide();
            } else {
                int extraInt = XUtils.convertToPx(context, 25);
                if (binding.expressiveBottomSheet.getState() != ExpressiveSliderLayout.STATE_EXPANDED) { 
                }
                binding.bottomNavigation.animate().translationY(0).alpha(1f).setDuration(300).setInterpolator(interpolator).start();
			   // if (MusicListFragment.fab != null) MusicListFragment.fab.show();
            }
			return Unit.INSTANCE;
		});
    }
    
    public void HidePlayer(boolean b) {
        android.view.animation.Interpolator interpolator = new PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f);
        if (isPlayerHidden == b || binding == null) return;
        isPlayerHidden = b;
        //bottomSheetBehavior.setDraggable(!b); TODO : add setDraggable() in ExpressiveSliderLayout
        HideBNVOnly(b);
        ViewKt.doOnLayout(binding.bottomNavigation, v -> {
            if (b) {
                binding.miniPlayerBottomSheet.animate().translationY(binding.bottomNavigation.getHeight()*2).setDuration(300).setInterpolator(interpolator).start();
            } else {
                binding.miniPlayerBottomSheet.animate().translationY(isBnvHidden? binding.bottomNavigation.getHeight() : 0).setDuration(300).setInterpolator(interpolator).start();
            }
            return Unit.INSTANCE;
		});
    }
    
    public boolean isBNVHidden() {
        return isBnvHidden;
    }

    public void updateColors() {
        if (ColorPaletteUtils.lightColors == null && ColorPaletteUtils.darkColors == null) return;
        
        Map<String, Integer> colors = XUtils.isDarkMode(context) ? ColorPaletteUtils.darkColors : ColorPaletteUtils.lightColors;
        Map<String, Integer> oldColors = XUtils.isDarkMode(context) ? ColorPaletteUtils.oldDarkColors : ColorPaletteUtils.oldLightColors;
        
        effectiveOldColors = new HashMap<>(oldColors);
        
        boolean hasLive = LiveColors.primary != 0;
        int onTertiary = colors.get("onTertiary");
        int tertiary = colors.get("tertiary");
        int oldOnTertiary = hasLive ? LiveColors.onTertiary : effectiveOldColors.get("onTertiary");
        int oldTertiary   = hasLive ? LiveColors.tertiary   : effectiveOldColors.get("tertiary");
        int surface = isOledTheme ? 0xff000000 : colors.get("surface");
        int oldSurface = isOledTheme ? 0xff000000 : (hasLive ? LiveColors.surface : effectiveOldColors.get("surface"));
        int surfaceContainer = isOledTheme ? 0xff050505 : colors.get("surfaceContainer");
        int oldSurfaceContainer = isOledTheme ? 0xff050505 : (hasLive ? LiveColors.surfaceContainer : effectiveOldColors.get("surfaceContainer"));
        int outline = colors.get("outline");
        int oldOutline = hasLive ? LiveColors.outline : effectiveOldColors.get("outline");
        int primary = colors.get("primary");
        int oldPrimary = hasLive ? LiveColors.primary : effectiveOldColors.get("primary");
        int onPrimary = colors.get("onPrimary");
        int oldOnPrimary = hasLive ? LiveColors.onPrimary : effectiveOldColors.get("onPrimary");
        int onSurfaceContainer = isOledTheme? colors.get("onSurface") : colors.get("onSurfaceContainer");
        int oldOnSurfaceContainer = isOledTheme ? (hasLive ? LiveColors.onSurface : effectiveOldColors.get("onSurface")) : (hasLive ? LiveColors.onSurfaceContainer : effectiveOldColors.get("onSurfaceContainer"));
        int onSurface = colors.get("onSurface");
        int oldOnSurface = hasLive ? LiveColors.onSurface : effectiveOldColors.get("onSurface");
        
        binding.mesh.setColors(surface, onPrimary, onTertiary);
        
        Drawable nextBg = binding.nextButton.getBackground();
        Drawable favBg  = binding.favoriteButton.getBackground();
        Drawable saveBg = binding.saveButton.getBackground();
        Drawable prevBg = binding.previousButton.getBackground();
        
        Drawable d = binding.extendableLayout.getBackground();
        GradientDrawable gd = (GradientDrawable) d;
        
        GradientDrawable d2 = (GradientDrawable) binding.dragHandle.getBackground();
        
        GradientDrawable d3 = (GradientDrawable) binding.songInfoLayout.getBackground();
        
        SeekBar seekbar = binding.songSeekbar;
        
        
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(500);
        va.addUpdateListener(a -> {
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
            
            binding.toggleView.setShapeColor(iop);
            binding.toggleView.setIconColor(ip);
            binding.xlyricsView.setLyricColor(ios, io);
			binding.placeholderLyricsText.setTextColor(ios);
			
			/*
            
            binding.nextButton.setColorFilter(it, PorterDuff.Mode.SRC_IN);
            binding.favoriteButton.setColorFilter(it, PorterDuff.Mode.SRC_IN);
            binding.saveButton.setColorFilter(it, PorterDuff.Mode.SRC_IN);
            binding.previousButton.setColorFilter(it, PorterDuff.Mode.SRC_IN);*/
            
            nextBg.setColorFilter(new PorterDuffColorFilter(iot, PorterDuff.Mode.SRC_IN));
            favBg.setColorFilter(new PorterDuffColorFilter(iot, PorterDuff.Mode.SRC_IN));
            saveBg.setColorFilter(new PorterDuffColorFilter(iot, PorterDuff.Mode.SRC_IN));
            prevBg.setColorFilter(new PorterDuffColorFilter(iot, PorterDuff.Mode.SRC_IN));
        
            playerSurface = is;
            
            if (binding.expressiveBottomSheet.getState() == ExpressiveSliderLayout.STATE_EXPANDED) binding.expressiveBottomSheet.setSheetBackgroundColor(playerSurface);
            binding.lyricsContainer.setBackgroundColor(playerSurface);
            
            d3.setColor(isc);
            
            gd.setColor(innerBottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED? isc : is);
            
            d2.setColor(isOledTheme? 0xffbdbdbd : io);
            
            seekbar.setThumbTintList(ColorStateList.valueOf(ip)); 
            progressDrawable.setTint(ip);
            
            binding.lyricsButton.setIconTint(ColorStateList.valueOf(isOledTheme? 0xffbdbdbd : iosc));
            binding.lyricsButton.setRippleColor(ColorStateList.valueOf(io));
            
            binding.artistBigTitle.setTextColor(iosc);
            binding.songBigTitle.setTextColor(ios);
            binding.currentDurationText.setTextColor(iosc);
            binding.totalDurationText.setTextColor(iosc);
            binding.songInfoText.setTextColor(iosc);
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

    @Override
    public void onServiceEvent(int callbackType) {
        if (handler == null) return;
        handler.post(() -> {
            if (callbackType == ServiceCallback.CALLBACK_COLORS_UPDATE) {
                updateColors();
            } else if (callbackType == ServiceCallback.CALLBACK_PROGRESS_UPDATE && seekbarFree) {
                updateProgress(RuntimeData.currentProgress);
                if (mediaController != null) {
                    binding.xlyricsView.onProgress((int) RuntimeData.currentProgress);
                }
            } else if (callbackType == ServiceCallback.CALLBACK_VUMETER_UPDATE && mediaController != null) {
                updateVumeters(PlayerService.isPlaying);
            }
        });
    }
    
    @Override
    public void onColorsChanged() {
        runOnUiThread(() -> updateColors());
    }
    
    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        runOnUiThread(() -> {
            updateVumeters(isPlaying);
            /*if (isPlaying) {
                binding.toggleView.forcePlayState();
            } else {
                binding.toggleView.forcePauseState();
            }*/
        });
    }
    
    @Override
    public void onProgressChanged(long progress) {
        if (seekbarFree) runOnUiThread(() -> updateProgress(progress));
        runOnUiThread(() -> binding.xlyricsView.onProgress((int) progress));
    }
    
    public void updateVumeters(boolean b) {
        searchFragment.updateVumeter(b);
        musicListFragment.updateVumeter(b);
    }

    public MediaController getController() {
        return mediaController;
    }

    public void setMusicListFragmentInstance(MusicListFragment f) {
        musicListFragment = f;
    }
    
    public void setSearchFragmentInstance(SearchFragment f) {
        searchFragment = f;
        if (mediaController != null && mediaController.getMediaItemCount() > 0) searchFragment.updateActiveItem(mediaController.getCurrentMediaItemIndex());
    }
    
    public void setSettingsFragmentInstance(SettingsFragment f) {
        settingsFragment = f;
    }

    public void setDarkStatusBar(Window window, boolean dark) {
		WindowInsetsControllerCompat controller =
        WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(dark);
    }

    public void loadSettings() {
        isOledTheme = XUtils.isDarkMode(this) && DataManager.isOledThemeEnabled();
        if (isOledTheme) binding.mesh.setVisibility(View.GONE);
        isBlurOn = DataManager.isBlurOn();
        if (XUtils.areBlursOrDynamicColorsSupported() && !isBlurOn) binding.fragmentsContainer.setRenderEffect(null);
		binding.xlyricsView.updateActiveStates();
		binding.mesh.setVisibility(DataManager.sp.getBoolean("enable_lyrics_gradient", false)? View.VISIBLE : View.GONE);
    }
    
    @Override
    public void onSeekRequested(long ms) {
        if (mediaController != null) {
            mediaController.seekTo(ms);
        }
    }
	
	public void loadLyrics(String path) {
		LyricsExtractor.extract(path, lyrics -> {
            if (lyrics != null && !lyrics.isEmpty()) {
                LyricsParser.parse(lyrics, result -> {
                    binding.xlyricsView.post(() -> {
                        binding.xlyricsView.setLyrics(result.lines());
                        binding.xlyricsView.setListener(MainActivity.this);
						
						MaterialFadeThrough mft = new MaterialFadeThrough();
						mft.setDuration(300);
						TransitionManager.beginDelayedTransition(binding.frame);
						binding.lyricsPlaceholder.setVisibility(View.GONE);
						binding.xlyricsView.setVisibility(View.VISIBLE);
                    });
                });
            } else {
                MaterialFadeThrough mft = new MaterialFadeThrough();
				mft.setDuration(300);
				TransitionManager.beginDelayedTransition(binding.frame);
				binding.xlyricsView.setVisibility(View.GONE);
				binding.lyricsPlaceholder.setVisibility(View.VISIBLE);
            }
        });
	}
}
