package com.xapps.media.xmusic.lyric;

import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import com.kyant.taglib.TagLib;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LyricsExtractor {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();  
    private static final Handler MAIN = new Handler(Looper.getMainLooper());  
    private static long currentRequestId = 0;

    public interface Callback {  
        void onLyrics(String lyrics);  
    }  

    public static void extract(String audioPath, Callback callback) {  
        final long requestId = ++currentRequestId;
        EXECUTOR.execute(() -> {  
            String result = extractInternal(audioPath);  
            MAIN.post(() -> {
                if (requestId == currentRequestId) {
                    callback.onLyrics(result);
                }
            });  
        });  
    }  

    private static String extractInternal(String audioPath) {  
        int bestScore = Integer.MIN_VALUE;  
        String best = null;  
        String embedded = extractEmbedded(audioPath);  
        if (embedded != null) {  
            int score = score(embedded, true);  
            bestScore = score;  
            best = embedded;  
        }  
        File audio = new File(audioPath);  
        File dir = audio.getParentFile();  
        if (dir == null) return best;  
        String base = strip(audio.getName());  
        File[] files = new File[]{  
                new File(dir, base + ".ttml"),  
                new File(dir, base + ".elrc"),  
                new File(dir, base + ".lrc"),  
                new File(dir, base + ".txt")  
        };  
        for (File f : files) {  
            if (!f.exists() || !f.isFile()) continue;  
            String text = read(f);  
            if (text == null || text.isEmpty()) continue;  
            int score = score(text, false);  
            if (score > bestScore) {  
                bestScore = score;  
                best = text;  
            }  
        }  
        return best;  
    }  

    private static String extractEmbedded(String path) {
        ParcelFileDescriptor fd = null;
        try {
            fd = ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
            ParcelFileDescriptor dup = fd.dup();
            String[] lyrics = TagLib.getMetadataPropertyValues(dup.detachFd(), "LYRICS");
            if (lyrics != null && lyrics.length > 0) {
                return lyrics[0];
            }
            return null;
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                if (fd != null) fd.close();
            } catch (Exception ignored) {}
        }
    }

    private static int score(String text, boolean embedded) {  
        int score = embedded ? 1000 : 0;  
        if (looksWordSynced(text)) score += 200;  
        else if (looksLineSynced(text)) score += 100;  
        score += Math.min(text.length() / 50, 100);  
        return score;  
    }  

    private static boolean looksWordSynced(String s) {  
        return s.contains("<tt")  
                || s.contains("<p ")  
                || s.contains("<span")  
                || s.matches("(?s).*\\[\\d+:\\d+\\.\\d+].*\\[\\d+:\\d+\\.\\d+].*");  
    }  

    private static boolean looksLineSynced(String s) {  
        return s.matches("(?s).*\\[\\d{1,2}:\\d{2}(\\.\\d{1,3})?].*");  
    }  

    private static String read(File f) {  
        try (FileInputStream in = new FileInputStream(f)) {  
            byte[] buf = new byte[(int) f.length()];  
            int r = in.read(buf);  
            if (r <= 0) return null;  
            return new String(buf, StandardCharsets.UTF_8);  
        } catch (Exception e) {  
            return null;  
        }  
    }  

    private static String strip(String name) {  
        int i = name.lastIndexOf('.');  
        return i > 0 ? name.substring(0, i) : name;  
    }
}
