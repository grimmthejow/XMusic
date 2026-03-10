package com.xapps.media.xmusic.helper;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import com.kyant.taglib.Picture;
import com.kyant.taglib.TagLib;
import com.xapps.media.xmusic.common.SongLoadListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class SongMetadataHelper {

    private static final String CACHE_DIR_NAME = "covers";
    private static ArrayList<HashMap<String, Object>> songsData = new ArrayList<>();

    public static void getAllSongs(Context context, SongLoadListener listener) {

        if (!songsData.isEmpty()) {
            if (listener != null) listener.onComplete(songsData);
            return;
        }

        ArrayList<HashMap<String, Object>> songListMap = new ArrayList<>();

        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ALBUM_ARTIST,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.BITRATE,
                MediaStore.Audio.Media.SIZE
        };

        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                MediaStore.Audio.Media.DATE_ADDED + " DESC"
        );

        if (cursor == null) {
            if (listener != null) listener.onComplete(songListMap);
            return;
        }

        int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
        int pathIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
        int titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
        int artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
        int albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
        int albumIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
        int albumArtistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST);
        int yearIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR);
        int trackIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK);
        int durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
        int dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);
        int dateModifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED);
        int mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE);
        int bitrateIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.BITRATE);
        int sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);

        while (cursor.moveToNext()) {

            long songId = cursor.getLong(idIndex);
            String path = cursor.getString(pathIndex);
            String title = cursor.getString(titleIndex);
            String artist = cursor.getString(artistIndex);
            String album = cursor.getString(albumIndex);
            long albumId = cursor.getLong(albumIdIndex);
            String albumArtist = cursor.getString(albumArtistIndex);
            int year = cursor.getInt(yearIndex);
            int track = cursor.getInt(trackIndex);
            long duration = cursor.getLong(durationIndex);
            long dateAdded = cursor.getLong(dateAddedIndex);
            long dateModified = cursor.getLong(dateModifiedIndex);
            String mimeType = cursor.getString(mimeIndex);
            int bitrate = cursor.getInt(bitrateIndex);
            long size = cursor.getLong(sizeIndex);

            HashMap<String, Object> map = new HashMap<>();

            map.put("path", path);
            map.put("id", songId);
            map.put("title", title != null && !title.isEmpty() ? title : "Unknown Title");
            map.put("author", artist != null && !artist.isEmpty() ? artist : "Unknown Artist");
            map.put("album", album);
            map.put("albumId", albumId);
            map.put("albumArtist", albumArtist);
            map.put("year", year);
            map.put("track", track);
            map.put("duration", duration > 0 ? millisecondsToDuration(duration) : "00:00");
            map.put("total", String.valueOf((int) duration));
            map.put("dateAdded", dateAdded);
            map.put("dateModified", dateModified);
            map.put("mimeType", mimeType);
            map.put("bitrate", bitrate);
            map.put("size", size);

            map.put("thumbnail", getSongCover(context, path, songId));

            map.put("searchKey", (
                    "{t}" + map.get("title") +
                    "{/t}{a}" + map.get("author") +
                    "{/a}{al}" + map.get("album") +
                    "{/al}{aa}" + map.get("albumArtist") +
                    "{/aa}"
            ).toLowerCase(Locale.ROOT));

            songListMap.add(map);

            if (listener != null) {
                listener.onProgress(songListMap, songListMap.size());
            }
        }

        cursor.close();

        songsData = songListMap;

        if (listener != null) {
            listener.onComplete(songListMap);
        }
    }

    public static String getSongCover(Context context, String path, long songId) {
        String cached = getCachedCoverPath(context, path);
        if (cached != null) return cached;

        try {
            Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId);
            Uri albumArtUri = Uri.withAppendedPath(uri, "albumart");
            InputStream is = context.getContentResolver().openInputStream(albumArtUri);
            if (is != null) {
                Bitmap b = BitmapFactory.decodeStream(is);
                is.close();
                if (b != null) {
                    String cachePath = saveCoverToCache(context, path, b);
                    b.recycle();
                    return cachePath;
                }
            }
        } catch (Exception ignored) {}

        ParcelFileDescriptor pfd = null;
        try {
            pfd = ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
            int fd = pfd.detachFd();
            Picture cover = TagLib.getFrontCover(fd);
            if (cover != null && cover.getData() != null) {
                Bitmap b = BitmapFactory.decodeByteArray(cover.getData(), 0, cover.getData().length);
                if (b != null) {
                    String cachePath = saveCoverToCache(context, path, b);
                    b.recycle();
                    return cachePath;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (pfd != null) {
                try { pfd.close(); } catch (IOException ignored) {}
            }
        }

        return null;
    }

    public static String getCachedCoverPath(Context context, String songFilePath) {
        String hashedFileName = hashFilePath(songFilePath);
        File cacheDir = new File(context.getCacheDir(), CACHE_DIR_NAME);
        if (!cacheDir.exists()) cacheDir.mkdirs();
        File coverFile = new File(cacheDir, hashedFileName + ".jpg");
        if (coverFile.exists()) return coverFile.getAbsolutePath();
        return null;
    }

    private static String saveCoverToCache(Context context, String songFilePath, Bitmap bitmap) {
        String hashedFileName = hashFilePath(songFilePath);
        File cacheDir = new File(context.getCacheDir(), CACHE_DIR_NAME);
        if (!cacheDir.exists()) cacheDir.mkdirs();
        File coverFile = new File(cacheDir, hashedFileName + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(coverFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            return coverFile.getAbsolutePath();
        } catch (IOException ignored) {}
        return null;
    }

    private static String hashFilePath(String filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(filePath.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(filePath.hashCode());
        }
    }

    public static String millisecondsToDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, remainingSeconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSeconds);
        }
    }

    public static void clearCachedList() {
        songsData = new ArrayList<>();
    }
}