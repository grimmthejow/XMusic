package com.xapps.media.xmusic.service.manager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.CommandButton;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaSession;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionCommands;
import androidx.media3.session.SessionResult;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.xapps.media.xmusic.activity.RootActivity;
import com.xapps.media.xmusic.data.DataManager;
import com.xapps.media.xmusic.R;
import com.xapps.media.xmusic.service.resume.ResumeSong;
import com.xapps.media.xmusic.service.resume.ResumeState;
import com.xapps.media.xmusic.utils.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public class SessionManager {
    private MediaLibraryService.MediaLibrarySession session;
    private final ExoPlayerManager manager;
    private final ExoPlayer player;
    private final Player.Commands playerCommands = new Player.Commands.Builder().addAllCommands().build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Uri fallbackUri;
    private final Context context;
        
    public SessionManager(Context context, ExoPlayerManager manager) {
        this.manager = manager;
        player = manager.getPlayer();
        MediaLibraryService.MediaLibrarySession.Builder builder = new MediaLibraryService.MediaLibrarySession.Builder(context, manager.getPlayer(), new MediaSessionCallback());
        Intent intent = new Intent(context, RootActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent sessionActivity = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        builder.setId("XMusicMediaSession").setSessionActivity(sessionActivity);
        session = builder.build();
        fallbackUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.drawable.placeholder);
        this.context = context;
    }

    public class MediaSessionCallback implements MediaLibraryService.MediaLibrarySession.Callback {
        private String loopMode = DataManager.getLatestRepeatMode();
        private String shuffleMode = DataManager.getLatestShuffleMode();
     
        SessionCommand loopCommand = new SessionCommand(loopMode, Bundle.EMPTY);
        CommandButton loopButton = new CommandButton.Builder(getLoopMedia3Icon(loopMode))
                                    .setDisplayName("repeat")
                                    .setSessionCommand(loopCommand)
                                    .setSlots(CommandButton.SLOT_FORWARD)
                                    .build();
                                    
        SessionCommand shuffleCommand = new SessionCommand(shuffleMode, Bundle.EMPTY);
        CommandButton shuffleButton = new CommandButton.Builder(getShuffleMedia3Icon(shuffleMode))
                                        .setDisplayName("shuffle")
                                        .setSessionCommand(shuffleCommand)
                                        .setSlots(CommandButton.SLOT_FORWARD_SECONDARY)
                                        .build();
        List<CommandButton> commandsList = ImmutableList.of(loopButton, shuffleButton);
        SessionCommands sessionCommands = SessionCommands.EMPTY.buildUpon()
                                            .add(new SessionCommand("LOOP_ALL", Bundle.EMPTY))
                                            .add(new SessionCommand("LOOP_SINGLE", Bundle.EMPTY))
                                            .add(new SessionCommand("LOOP_OFF", Bundle.EMPTY))
                                            .add(new SessionCommand("SHUFFLE_ON", Bundle.EMPTY))
                                            .add(new SessionCommand("SHUFFLE_OFF", Bundle.EMPTY))
                                            .build();
                                            
        @NonNull
        @Override
        public MediaSession.ConnectionResult onConnect(@NonNull MediaSession mediaSession, @NonNull MediaSession.ControllerInfo controllerInfo) {
            manager.getPlayerHandler().post(() -> {
                switch (loopMode) {
                    case "LOOP_OFF" -> {
                        player.setRepeatMode(Player.REPEAT_MODE_OFF);
                        player.setPauseAtEndOfMediaItems(true);
                    }
                    case "LOOP_SINGLE" -> player.setRepeatMode(Player.REPEAT_MODE_ONE);
                    case "LOOP_ALL" -> {
                        player.setPauseAtEndOfMediaItems(false);
                        player.setRepeatMode(Player.REPEAT_MODE_ALL);
                    }
                }
                player.setShuffleModeEnabled(shuffleMode.equals("SHUFFLE_ON"));
            });
            return MediaSession.ConnectionResult.accept(MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon().add(new SessionCommand("LOOP_ALL", Bundle.EMPTY))
                                            .add(new SessionCommand("LOOP_SINGLE", Bundle.EMPTY))
                                            .add(new SessionCommand("LOOP_OFF", Bundle.EMPTY))
                                            .add(new SessionCommand("SHUFFLE_ON", Bundle.EMPTY))
                                            .add(new SessionCommand("SHUFFLE_OFF", Bundle.EMPTY)).build(), playerCommands);
        }
            
        @Override
        public void onPostConnect(MediaSession mediaSession, @NonNull MediaSession.ControllerInfo controllerInfo) {
            mediaSession.setAvailableCommands(controllerInfo, MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon().add(new SessionCommand("LOOP_ALL", Bundle.EMPTY))
                                            .add(new SessionCommand("LOOP_SINGLE", Bundle.EMPTY))
                                            .add(new SessionCommand("LOOP_OFF", Bundle.EMPTY))
                                            .add(new SessionCommand("SHUFFLE_ON", Bundle.EMPTY))
                                            .add(new SessionCommand("SHUFFLE_OFF", Bundle.EMPTY)).build(), playerCommands);
            mediaSession.setCustomLayout(controllerInfo, commandsList);
        }
            
        @NonNull
        @Override
        public ListenableFuture<SessionResult> onCustomCommand(@NonNull MediaSession mediaSession, @NonNull MediaSession.ControllerInfo controllerInfo, @NonNull SessionCommand sessionCommand, @NonNull Bundle bundle) {
            manager.getPlayerHandler().post(() -> {
                loopMode = switch (sessionCommand.customAction) {
                    case "LOOP_ALL" -> {
                        player.setRepeatMode(Player.REPEAT_MODE_ONE);
                        yield "LOOP_SINGLE"; 
                    }
                    case "LOOP_SINGLE" -> {
                        player.setPauseAtEndOfMediaItems(true);
                        player.setRepeatMode(Player.REPEAT_MODE_OFF);
                        yield "LOOP_OFF";
                    }
                    case "LOOP_OFF" -> {
                        player.setPauseAtEndOfMediaItems(false);
                        player.setRepeatMode(Player.REPEAT_MODE_ALL);
                        yield "LOOP_ALL"; 
                    }
                    default -> loopMode;
                    };
                
                shuffleMode = switch (sessionCommand.customAction) {
                    case "SHUFFLE_ON" -> {
                        player.setShuffleModeEnabled(false);
                        yield "SHUFFLE_OFF";
                    }
                    case "SHUFFLE_OFF" -> {
                        player.setShuffleModeEnabled(true);
                        yield "SHUFFLE_ON"; 
                    }
                    default -> shuffleMode;
                    };

                loopCommand = new SessionCommand(loopMode, Bundle.EMPTY);
                loopButton = new CommandButton.Builder(getLoopMedia3Icon(loopMode))
                    .setDisplayName("repeat")
                    .setSessionCommand(loopCommand)
                    .setSlots(CommandButton.SLOT_FORWARD)
                    .build();
                shuffleCommand = new SessionCommand(shuffleMode, Bundle.EMPTY);
                shuffleButton = new CommandButton.Builder(getShuffleMedia3Icon(shuffleMode))
                                    .setDisplayName("shuffle")
                                    .setSessionCommand(shuffleCommand)
                                    .setSlots(CommandButton.SLOT_FORWARD_SECONDARY)
                                    .build();
                commandsList = ImmutableList.of(loopButton, shuffleButton);
                DataManager.saveLatestRepeatMode(loopMode);
                DataManager.saveLatestShuffleMode(shuffleMode);
                mediaSession.setCustomLayout(controllerInfo, commandsList);
            });

            return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
        }
            
            
        @NonNull
        @Override
        public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onPlaybackResumption(@NonNull MediaSession mediaSession, @NonNull MediaSession.ControllerInfo controller, boolean isForPlayback) {
            SettableFuture<MediaSession.MediaItemsWithStartPosition> future = SettableFuture.create();
            executor.execute(() -> {
                try {
                    MediaSession.MediaItemsWithStartPosition items = restorePlaylist();
                    future.set(items);
                } catch (Exception e) {
                    future.setException(e);
                }
            });

            return future;
        }
    }
    
    public MediaLibraryService.MediaLibrarySession getSession() {
        return session;
    }

    private int getLoopMedia3Icon(String mode) {
        return switch (mode) {
            case "LOOP_OFF" -> CommandButton.ICON_REPEAT_OFF;
            case "LOOP_SINGLE" -> CommandButton.ICON_REPEAT_ONE;
            default -> CommandButton.ICON_REPEAT_ALL;
        };
    }

    private int getShuffleMedia3Icon(String mode) {
        return switch (mode) {
            case "SHUFFLE_ON" -> CommandButton.ICON_SHUFFLE_ON;
            default -> CommandButton.ICON_SHUFFLE_OFF;
        };
    }

    private ResumeState loadResumeState() {
        try (
        FileInputStream fis = context.openFileInput("resume_state.dat");
        ObjectInputStream ois = new ObjectInputStream(fis)
        ) {
            return (ResumeState) ois.readObject();
        } catch (Exception e) {
            return null;
        }
    }
	
	private MediaSession.MediaItemsWithStartPosition restorePlaylist() {
        ResumeState state = loadResumeState();

        if (state == null || state.songs.isEmpty()) {
            Log.d("RESUMPTION", "No state was found");
            return new MediaSession.MediaItemsWithStartPosition(
                new ArrayList<>(),
                0,
                0
            );
        }

        ArrayList<MediaItem> items = new ArrayList<>();

        for (ResumeSong song : state.songs) {
            MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setArtworkUri(
                    song.artworkUri != null
                    ? Uri.parse(song.artworkUri)
                    : fallbackUri
                )
                .build();

            items.add(
            new MediaItem.Builder()
                .setMediaMetadata(metadata)
                .setUri(Uri.fromFile(new File(song.path)))
                .build()
            );
        }
        Log.d("RESUMPTION", "state found : index "+String.valueOf(state.currentIndex)+" | position : "+String.valueOf(state.positionMs));

        return new MediaSession.MediaItemsWithStartPosition(
            items,
            state.currentIndex,
            state.positionMs
        );
    }

    public void release() {
        if (session != null) {
            session.release();
            session = null;
        }
        if (executor != null) executor.shutdownNow();
    }
}
