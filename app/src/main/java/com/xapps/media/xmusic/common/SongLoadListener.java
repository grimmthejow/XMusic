package com.xapps.media.xmusic.common;

import com.xapps.media.xmusic.models.Song;
import java.util.ArrayList;
import java.util.function.Consumer;

public interface SongLoadListener {

    default void onStarted(int totalSongs) {}

    default void onProgress(ArrayList<Song> songs, int count) {}

    default void onComplete(ArrayList<Song> songs) {}
    
    default void onError(Exception e) {}

    static SongLoadListener on(Consumer<ArrayList<Song>> complete, Consumer<Exception> error) {
        return new SongLoadListener() {
            @Override
            public void onComplete(ArrayList<Song> songs) {
                complete.accept(songs);
            }

            @Override
            public void onError(Exception e) {
                error.accept(e);
            }
        };
    }
}
