package com.xapps.media.xmusic.activity;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.*;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.xapps.media.xmusic.R;
import com.xapps.media.xmusic.activity.manager.LogicManager;
import com.xapps.media.xmusic.activity.manager.UIManager;
import com.xapps.media.xmusic.callback.ActivityCallback;
import com.xapps.media.xmusic.callback.CallbackInterface;
import com.xapps.media.xmusic.common.SongLoadListener;
import com.xapps.media.xmusic.data.DataManager;
import com.xapps.media.xmusic.data.RuntimeData;
import com.xapps.media.xmusic.databinding.ActivityRootBinding;
import com.xapps.media.xmusic.helper.SongMetadataHelper;
import com.xapps.media.xmusic.models.Song;
import com.xapps.media.xmusic.utils.MaterialColorUtils;

import com.xapps.media.xmusic.utils.XUtils;
import java.lang.Override;
import java.util.ArrayList;

public class RootActivity extends BaseActivity implements ActivityCallback {
    private ActivityRootBinding binding;
    private UIManager uiManager;
    private LogicManager logicManager;
    private MediaController mediaController;

    private boolean isReady = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this).setKeepOnScreenCondition(() -> isReady);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!XUtils.areAllPermsGranted(this)) {
                startActivity(new Intent(this, WelcomeActivity.class));
                finish();
            }
            isReady = false;
        }, savedInstanceState == null? 800 : 1);
        EdgeToEdge.enable(this, 
            SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        );
        XUtils.applyDynamicColors(this, DataManager.isOledThemeEnabled());
        if (XUtils.isDarkMode(this) && DataManager.isOledThemeEnabled())getTheme().applyStyle(R.style.ThemeOverlay_XMusic_OLED, true);
        MaterialColorUtils.initColors(this);
        super.onCreate(savedInstanceState);
        binding = ActivityRootBinding.inflate(getLayoutInflater());
        passData();
        setContentView(binding.getRoot());
        init();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (CallbackInterface.activity() != this) CallbackInterface.setActivityCallback(this);
        if (mediaController == null) logicManager.initController(
                this,
                controller -> {
                    mediaController = controller;
                    binding.expandedPlayer.songSeekbar.setAnimate(controller.isPlaying());
                },
                e -> showInfoDialog("Error", 0, e.toString(), "OK", binding.Coordinator),
                this::restoreStateIfPossible);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if (!RuntimeData.songs.isEmpty()) uiManager.updateContent(-1, true);
    }
    
    @Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
   	 super.onConfigurationChanged(newConfig);
        uiManager.restoreCoverExpansion();
	}
    
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        uiManager.viewModel.markDataAsSaved(true);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        CallbackInterface.clearActivityCallback(this);
        if (mediaController != null) mediaController.release();
        mediaController = null;
    }

    private void init() {
        uiManager = new UIManager(this);
        uiManager.initUI();
        logicManager = new LogicManager(this, uiManager);
        logicManager.initLogic();
    }

    public ActivityRootBinding getBinding() {
        return binding;
    }

    public void setSong(int position) {
        logicManager.playSong(position);
        uiManager.hideComponents(false, uiManager.bnvHidden, uiManager.tabsHidden, "mlf");
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        logicManager.updateVumeters(isPlaying);
        binding.expandedPlayer.songSeekbar.setAnimate(isPlaying);
        binding.collapsedPlayer.action.setIconResource(isPlaying? R.drawable.ic_pause : R.drawable.ic_play);
    }

    public MediaController getController() {
        return mediaController;
    }
    
    public LogicManager getLogicManager() {
        return logicManager;
    }
    
    public UIManager getUIManager() {
        return uiManager;
    }

    public void updateSongsQueue(ArrayList<Song> songs) {
        if (CallbackInterface.service() != null) CallbackInterface.service().updateSongs();
    }

    public void restoreStateIfPossible() {
        uiManager.maybeRestoreUIState();
    }

    public void loadSettings() {
        uiManager.loadSettings();
    }

    @Override
    public void onColorsChanged() {
        runOnUiThread(() -> uiManager.updateColors());
    }

    @Override
    public void onSongChanged(int position) {
        runOnUiThread(() -> {
            uiManager.updateContent(position, false);
        });
        if (CallbackInterface.mlFrag() != null) CallbackInterface.mlFrag().updateActiveItem(position);
        if (CallbackInterface.srFrag() != null) CallbackInterface.srFrag().updateActiveItem(position);
    }

    @Override
    public void onProgressChanged(long progress) {
        logicManager.handleProgress(progress);
    }

    @Override
    public void updateState() {
        runOnUiThread(() -> {
            uiManager.saveState();
        });
    }

    @Override
    public void passData() {
        if (CallbackInterface.subFrag() != null) CallbackInterface.subFrag().onBindingReady(binding);
    }

    public void updateLyrics() {
        uiManager.updateLyrics();
    }
}
