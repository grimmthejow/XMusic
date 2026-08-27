package com.xapps.media.xmusic.callback;
import androidx.media3.common.MediaItem;
import androidx.media3.session.MediaSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface ServiceCallback {

    default void seekTo(long position) {
        
    }
    
    default void saveState() {
        
    }
    
    default void stop() {
        
    }
    
    default void updateSongs() {
        
    }
    
    default void regenColors(int position) {
        
    }
    
    default long getCurrentProgress() {
        return 0L;
    }
    
    default List<MediaItem> getMediaItems() {
        return new ArrayList<>();
    }
    
    default Map<String, Integer> getDarkColors() {
        return Collections.emptyMap();
    }
    
    default Map<String, Integer> getLightColors() {
        return Collections.emptyMap();
    }

    default boolean isAnythingPlaying() {
        return false;
    }

    default boolean isPlaying() {
        return false;
    }

    default int getCurrentPosition() {
        return -1;
    }

    default MediaSession getSession() {
        return null;
    }

}