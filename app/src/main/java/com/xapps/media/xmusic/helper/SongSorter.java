package com.xapps.media.xmusic.helper;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SongSorter {

    private static final ExecutorService sortExecutor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface SortListener {
        void onSortComplete(ArrayList<HashMap<String, Object>> sortedList);
    }

    public enum SortBy {
        TITLE("title"),
        ARTIST("author"),
        ALBUM("album"),
        ALBUM_ARTIST("albumArtist"),
        YEAR("year"),
        TRACK("track"),
        DURATION("total"),
        DATE_ADDED("dateAdded"),
        DATE_MODIFIED("dateModified"),
        SIZE("size"),
        BITRATE("bitrate");

        final String key;
        SortBy(String key) { this.key = key; }
    }

    public static void sort(ArrayList<HashMap<String, Object>> inputList, SortBy criteria, boolean ascending, SortListener listener) {
        sortExecutor.execute(() -> {
            ArrayList<HashMap<String, Object>> sortedList = new ArrayList<>(inputList);

            Collections.sort(sortedList, (map1, map2) -> {
                Object val1 = map1.get(criteria.key);
                Object val2 = map2.get(criteria.key);

                if (val1 == null || val2 == null) return 0;

                int result;

                if (val1 instanceof Number && val2 instanceof Number) {
                    result = Double.compare(((Number) val1).doubleValue(), ((Number) val2).doubleValue());
                } else if (criteria == SortBy.DURATION) {
                    try {
                        long d1 = Long.parseLong(String.valueOf(val1));
                        long d2 = Long.parseLong(String.valueOf(val2));
                        result = Long.compare(d1, d2);
                    } catch (Exception e) {
                        result = 0;
                    }
                } else {
                    result = val1.toString().compareToIgnoreCase(val2.toString());
                }

                return ascending ? result : -result;
            });

            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onSortComplete(sortedList);
                }
            });
        });
    }
}
