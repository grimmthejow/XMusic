package com.xapps.media.xmusic.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import com.xapps.media.xmusic.common.SongLoadListener;
import com.xapps.media.xmusic.helper.SongMetadataHelper;
import com.xapps.media.xmusic.models.Song;
import com.xapps.media.xmusic.utils.XUtils;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= 31) SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        executor.execute(() -> SongMetadataHelper.getAllSongs(this, new SongLoadListener() {
            @Override
            public void onComplete(ArrayList<Song> list) {
                if (!XUtils.areAllPermsGranted(SplashActivity.this)) {
                    startActivity(new Intent(SplashActivity.this, WelcomeActivity.class));
                } else {
                    startActivity(new Intent(SplashActivity.this, RootActivity.class));
                }
                finish();
            }
        }));
    }
}
