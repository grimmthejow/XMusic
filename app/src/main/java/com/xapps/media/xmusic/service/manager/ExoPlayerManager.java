package com.xapps.media.xmusic.service.manager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.ExperimentalApi;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import com.xapps.media.xmusic.data.RuntimeData;
import com.xapps.media.xmusic.models.Song;
import com.xapps.media.xmusic.R;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ExoPlayerManager {

    private final Handler playerHandler;
    private ExoPlayer player;
    private List<MediaItem> mediaItems;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Context context;
    private final Uri fallbackUri;

    @OptIn(markerClass = UnstableApi.class)
    @ExperimentalApi
    public ExoPlayerManager(Context context) {
        fallbackUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.drawable.placeholder);
        
        this.context = context;
        HandlerThread playerThread = new HandlerThread("ExoPlayerThread");
        playerThread.start();
        playerHandler = new Handler(Looper.getMainLooper());
        
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(context)
                                                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                                                .setEnableAudioFloatOutput(true);

        AudioAttributes attrs = new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build();

        player = new ExoPlayer.Builder(context, renderers)
                .setLooper(Looper.getMainLooper())
                .experimentalSetDynamicSchedulingEnabled(false)
                .setAudioAttributes(attrs, true)
                .build();
                
        context.registerReceiver(noisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }

    public ExoPlayer getPlayer() {
        return player;
    }
    
    public Handler getPlayerHandler() {
        return playerHandler;
    }
    
    public void destroy() {
        playerHandler.post(() -> {
            if (player != null) {
                player.release();
                player = null;
            }
        });
        //playerHandler.postDelayed(() -> playerThread.quitSafely(), 500);
        context.unregisterReceiver(noisyReceiver);
    }
    
    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
				playerHandler.post(() -> {
                    if (player.isPlaying()) {
                        player.pause();
                    }
				});
            }
        }
    };
    
    public void updateMediaItems() {
        ArrayList<Song> songs = RuntimeData.songs;
        executor.execute(() -> {
            ArrayList<MediaItem> items = new ArrayList<>(songs.size());

            for (Song song : songs) {
                MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setArtworkUri(song.getArtworkUri() != null? song.getArtworkUri() : fallbackUri)
                .build();

                items.add(
                    new MediaItem.Builder()
                    .setMediaMetadata(metadata)
                    .setUri(Uri.fromFile(new File(song.path)))
                    .build()
                );
            }
            new Handler(Looper.getMainLooper()).post(() -> mediaItems = items);
        });
    }
    
    public List<MediaItem> getMediaItems() {
        return mediaItems;
    }

}