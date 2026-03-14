package com.xapps.media.xmusic.common;

import java.util.ArrayList;
import java.util.HashMap;

public interface SongLoadListener {

    default void onStarted(int totalSongs) {}

    default void onProgress(ArrayList<HashMap<String, Object>> songs, int count) {}

    default void onComplete(ArrayList<HashMap<String, Object>> songs) {}
}