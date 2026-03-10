package com.xapps.media.xmusic.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.recyclerview.widget.RecyclerView;

import com.xapps.media.xmusic.helper.SongMetadataHelper;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CoverLoader {

   /* private static final int THREAD_COUNT =
            Math.max(1, Runtime.getRuntime().availableProcessors() / 2);

    private static final ExecutorService EXECUTOR =
            Executors.newFixedThreadPool(THREAD_COUNT);

    private static final Set<String> IN_FLIGHT =
            Collections.synchronizedSet(new HashSet<>());

    private CoverLoader() {}

    public static void request(
            Context context,
            String songPath,
            int position,
            RecyclerView.Adapter<?> adapter
    ) {
        if (songPath == null || IN_FLIGHT.contains(songPath)) return;

        IN_FLIGHT.add(songPath);

        EXECUTOR.execute(() -> {
            try {
                SongMetadataHelper.getSongCover(
                        context,
                        songPath,
                        null,
                        -1
                );
            } finally {
                IN_FLIGHT.remove(songPath);
                if (adapter != null) {
                    new Handler(Looper.getMainLooper()).post(
                            () -> adapter.notifyItemChanged(position)
                    );
                }
            }
        });
    }*/
}