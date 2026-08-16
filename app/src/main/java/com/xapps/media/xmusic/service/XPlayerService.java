package com.xapps.media.xmusic.service;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.ExperimentalApi;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaSession;

import com.xapps.media.xmusic.R;
import com.xapps.media.xmusic.callback.ActivityCallback;
import com.xapps.media.xmusic.callback.CallbackInterface;
import com.xapps.media.xmusic.callback.ServiceCallback;
import com.xapps.media.xmusic.data.DataManager;
import com.xapps.media.xmusic.data.RuntimeData;
import com.xapps.media.xmusic.models.Song;
import com.xapps.media.xmusic.service.manager.ExoPlayerManager;
import com.xapps.media.xmusic.service.manager.SessionManager;
import com.xapps.media.xmusic.service.resume.ResumeSong;
import com.xapps.media.xmusic.service.resume.ResumeState;
import com.xapps.media.xmusic.stats.StatsAudioAnalyzer;
import com.xapps.media.xmusic.utils.ColorPaletteUtils;
import com.xapps.media.xmusic.utils.Log;
import com.xapps.media.xmusic.utils.MaterialColorUtils;
import com.xapps.media.xmusic.utils.XUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class XPlayerService extends MediaLibraryService implements ServiceCallback {
    private ExoPlayerManager playerManager;
    private Player player;
    private Handler playerHandler;
    private SessionManager sessionManager;
    private StatsAudioAnalyzer statsAnalyzer;
    private volatile boolean handlerRunning;
    private volatile boolean saveHandlerRunning;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile long currentProgress;
    private volatile int currentPosition;
    private List<MediaItem> mediaItems;

    private final Context playerContext = this;

    public Map<String, Integer> lightColors;
    public Map<String, Integer> darkColors;

    private volatile boolean isPlaying, isIdle;

    @OptIn(markerClass = {ExperimentalApi.class, UnstableApi.class})
    @Override
    public void onCreate() {
        super.onCreate();
        CallbackInterface.setServiceCallback(this);
        playerManager = new ExoPlayerManager(this);
        player = playerManager.getPlayer();
        playerHandler = playerManager.getPlayerHandler();
        sessionManager = new SessionManager(this, playerManager);
        statsAnalyzer = new StatsAudioAnalyzer(getApplicationContext());

        DefaultMediaNotificationProvider cnp = new DefaultMediaNotificationProvider(this);
        cnp.setSmallIcon(R.drawable.service_icon);
        setMediaNotificationProvider(cnp);
        setupPlayerListeners();
        playerManager.updateMediaItems();
    }

    @Override
    public MediaLibraryService.MediaLibrarySession onGetSession(
            MediaSession.ControllerInfo controllerInfo) {
        return sessionManager.getSession();
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onTaskRemoved(Intent i) {
        if (isIdle) pauseAllPlayersAndStopSelf();
    }

    @Override
    public void onDestroy() {
        sessionManager.release();
        playerManager.destroy();
        statsAnalyzer.release();
        stopUpdates();
        stopPeriodicSave();
        CallbackInterface.clearServiceCallback(this);
        super.onDestroy();
    }

    @Override
    public void updateSongs() {
        playerManager.updateMediaItems();
    }

    private void setupPlayerListeners() {
        playerHandler.post(
                () -> {
                    player.addListener(
                            new Player.Listener() {
                                @Override
                                public void onPlaybackStateChanged(int state) {
                                    ActivityCallback activityCallback =
                                            CallbackInterface.activity();
                                    if (activityCallback != null)
                                        activityCallback.onPlaybackStateChanged(player.isPlaying());

                                    if (state == Player.STATE_READY) {
                                        // genColors(player.getCurrentMediaItemIndex());
                                        startUpdates();
                                    }

                                    if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
                                        isIdle = (state == Player.STATE_IDLE);
                                        saveResumeState();
                                        stopPeriodicSave();
                                    }
                                    
                                    if (activityCallback != null) CallbackInterface.activity().updateState();
                                }

                                @Override
                                public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                                    if (mediaItem == null || mediaItem.localConfiguration == null) {
                                        statsAnalyzer.stopAnalysis();
                                    } else {
                                        if (!RuntimeData.songs.isEmpty() && player.getCurrentMediaItemIndex() < RuntimeData.songs.size()) {
                                            statsAnalyzer.startAnalysis(RuntimeData.songs.get(player.getCurrentMediaItemIndex()));
                                        }
                                    }
                                    if (CallbackInterface.activity() != null) CallbackInterface.activity().onSongChanged(mediaItem == null? -1 :player.getCurrentMediaItemIndex());
                                    if (player.getPlaybackState() != Player.STATE_IDLE || mediaItem != null) {
                                        genColors(player.getCurrentMediaItemIndex());
                                    }
                                    if (player.getMediaItemCount() > 0) saveResumeState();
                                }

                                @Override
                                public void onIsPlayingChanged(boolean playing) {
                                    currentPosition = player.getCurrentMediaItemIndex();
                                    isPlaying = playing;
                                    ActivityCallback activityCallback =
                                            CallbackInterface.activity();
                                    if (activityCallback != null)
                                        activityCallback.onPlaybackStateChanged(player.isPlaying());

                                    if (playing) {
                                        statsAnalyzer.resumeAnalysis();
                                        startPeriodicSave();
                                    } else {
                                        statsAnalyzer.pauseAnalysis();
                                        stopPeriodicSave();
                                        saveResumeState();
                                    }
                                }
                                
                                @Override
                                public void onPlayerError(PlaybackException error) {
                                    android.util.Log.e("XMusicError", "Player Exception Caught", error);
                                    Throwable cause = error.getCause();
                                    if (cause != null) {
                                        android.util.Log.e("XMusicError", "Underlying Cause", cause);
                                    }
                                }
                            });
                });
    }

    private void genColors(int index) {
        executor.execute(
                () -> {
                    Bitmap transparentBitmap =
                            BitmapFactory.decodeResource(getResources(), R.drawable.transparent);
                    Bitmap bmp;
                    if (RuntimeData.songs.isEmpty()) {
                        bmp = transparentBitmap;
                    } else {
                        Uri thumb = RuntimeData.songs.get(index).getArtworkUri();
                        boolean exists = false;
                        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(thumb, "r")) {
                            exists = pfd != null;
                        } catch (Exception ignored) {
                        }
                        bmp = exists ? loadBitmapFromPath(thumb) : transparentBitmap;
                    }

                    if (DataManager.areStableColors()) {
                        ColorPaletteUtils.generateFromColor(MaterialColorUtils.colorPrimary, (light, dark) -> {
                            ActivityCallback activityCallback = CallbackInterface.activity();
                            if (activityCallback != null)
                                activityCallback.onColorsChanged();
                        });
                    } else {
                        ColorPaletteUtils.generateFromBitmap(bmp, (light, dark) -> {
                            ActivityCallback activityCallback = CallbackInterface.activity();
                            if (activityCallback != null) activityCallback.onColorsChanged();
                        });
                    }
                });
    }

    private Bitmap loadBitmapFromPath(Uri uri) {
        InputStream in = null;
        try {
            in = getContentResolver().openInputStream(uri);
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void saveResumeState() {
        if (RuntimeData.songs == null || RuntimeData.songs.isEmpty()) return;
        if (player == null || player.getPlaybackState() == Player.STATE_IDLE || player.getMediaItemCount() == 0) return;
        playerHandler.post(() -> {
            ResumeState state = buildResumeState();
            executor.execute(() -> {
                try (FileOutputStream fos = openFileOutput("resume_state.dat", MODE_PRIVATE);
                     ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                     oos.writeObject(state);
                } catch (Exception e) {
                    Log.e("RESUME_STATE-SAVE", "SAVE FAILED", e);
                }
            });
        });
    }

    private ResumeState buildResumeState() {
        ResumeState state = new ResumeState();
        state.shuffle = player.getShuffleModeEnabled();
        state.repeatMode = player.getRepeatMode();
        state.currentIndex = player.getCurrentMediaItemIndex();
        state.positionMs = player.getCurrentPosition();
        state.speed = player.getPlaybackParameters().speed;

        for (Song song : RuntimeData.songs) {
            state.songs.add(new ResumeSong(song.title, song.artist, song.path, song.getArtworkUri() != null ? song.getArtworkUri().toString() : null));
        }
        return state;
    }

    @Override
    public long getCurrentProgress() {
        return currentProgress;
    }

    @Override
    public List<MediaItem> getMediaItems() {
        return playerManager.getMediaItems();
    }

    @Override
    public Map<String, Integer> getLightColors() {
        return lightColors;
    }

    @Override
    public Map<String, Integer> getDarkColors() {
        return darkColors;
    }

    @Override
    public boolean isAnythingPlaying() {
        return player.getMediaItemCount() > 0 && player.getCurrentMediaItemIndex() != -1;
    }

    @Override
    public boolean isPlaying() {
        return isPlaying;
    }

    @Override
    public void regenColors(int position) {
        if (position < 0 || position >= player.getMediaItemCount()) return;
        genColors(position);
    }

    @Override
    public void saveState() {
        saveResumeState();
    }

    @Override
    public int getCurrentPosition() {
        return currentPosition;
    }

    private final Runnable progressUpdater =
            new Runnable() {
                @Override
                public void run() {
                    long currentTime = System.currentTimeMillis();
                    currentProgress = player.getCurrentPosition();
                    if (CallbackInterface.activity() != null) {
                        CallbackInterface.activity().onProgressChanged(currentProgress);
                    }

                    playerHandler.postDelayed(this, 10);
                }
            };

    private final Runnable periodicSaveRunnable =
            new Runnable() {
                @Override
                public void run() {
                    if (player.getMediaItemCount() > 0) saveResumeState();
                    playerHandler.postDelayed(this, 5000);
                }
            };

    private void startUpdates() {
        if (handlerRunning) return;
        if (playerHandler != null) playerHandler.post(progressUpdater);
        handlerRunning = true;
    }

    private void stopUpdates() {
        if (!handlerRunning) return;
        if (playerHandler != null) playerHandler.removeCallbacks(progressUpdater);
        handlerRunning = false;
    }

    private void startPeriodicSave() {
        if (saveHandlerRunning) return;
        if (playerHandler != null) playerHandler.postDelayed(periodicSaveRunnable, 10000);
        saveHandlerRunning = true;
    }

    private void stopPeriodicSave() {
        if (!saveHandlerRunning) return;
        if (playerHandler != null) playerHandler.removeCallbacks(periodicSaveRunnable);
        saveHandlerRunning = false;
    }
}
