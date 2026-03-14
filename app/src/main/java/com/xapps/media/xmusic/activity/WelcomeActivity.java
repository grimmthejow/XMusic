package com.xapps.media.xmusic.activity;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import android.view.*;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.TextSwitcher;
import android.widget.TextView;
import androidx.activity.BackEventCompat;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.transition.TransitionManager;
import androidx.transition.TransitionSeekController;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.model.KeyPath;
import com.airbnb.lottie.value.LottieValueCallback;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.MaterialFadeThrough;
import com.google.android.material.transition.MaterialSharedAxis;
import com.xapps.media.xmusic.activity.MainActivity;
import com.xapps.media.xmusic.R;
import com.xapps.media.xmusic.helper.SongMetadataHelper;
import com.xapps.media.xmusic.utils.XUtils;
import com.xapps.media.xmusic.common.SongLoadListener;
import com.xapps.media.xmusic.data.DataManager;
import com.xapps.media.xmusic.databinding.ActivityWelcomeBinding;
import com.xapps.media.xmusic.utils.MaterialColorUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class WelcomeActivity extends AppCompatActivity {
    
    private ActivityWelcomeBinding binding;
    private int currentPage = 1;
    private int MAX_PAGE_COUNT = 4;
    private OnBackPressedCallback callback1, callback2, callback3, callback4, nullcallback;
    private boolean notificationsAllowed, audiAccessAllowed, storageReadAllowed;
    private TransitionSeekController seekController;
    private TextView tv;
    private ActivityResultLauncher<String> requestPermissionLauncher;
            
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        getWindow().setNavigationBarContrastEnforced(false);
        binding = ActivityWelcomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        MaterialColorUtils.initColors(this);
        setupLottie();
        checkSDK();
        setupInsets();
        setupClickListeners();
        setupPermsLaunchers();
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(MaterialColorUtils.colorPrimaryContainer);
        bg.setCornerRadius(Float.MAX_VALUE);
        binding.progressBar.setBackground(bg);
    }
    
    public void setupLottie() {
        binding.lottie.addLottieOnCompositionLoadedListener(composition -> {
            binding.lottie.addValueCallback(new KeyPath(".primaryContainer", "**"), LottieProperty.COLOR, frameInfo -> MaterialColorUtils.colorPrimaryContainer);
            binding.lottie.addValueCallback(new KeyPath(".onSecondary", "**"), LottieProperty.COLOR, frameInfo -> MaterialColorUtils.colorOnSecondary);
            binding.lottie.addValueCallback(new KeyPath(".surfaceContainer", "**"), LottieProperty.COLOR, frameInfo -> MaterialColorUtils.colorSurfaceContainer);
            });
    }
    
    @Override
    public void onResume() {
        super.onResume();
        checkPerms();
        binding.screen2Button.setEnabled(XUtils.areAllPermsGranted(this));
    }

    private void setupInsets() {
        XUtils.increaseMargins(binding.topTitle, 0, Math.round(XUtils.getStatusBarHeight(this)*1.5f), 0, 0);
        XUtils.increaseMargins(binding.screen2Text, 0, Math.round(XUtils.getStatusBarHeight(this)*1.5f), 0, 0);
        XUtils.increaseMargins(binding.beginButton, 0, 0, 0, XUtils.getNavigationBarHeight(this));
        XUtils.increaseMargins(binding.screen2Button, 0, 0, XUtils.getNavigationBarHeight(this), XUtils.getNavigationBarHeight(this));
    }

    private void setupClickListeners() {
        binding.startButton.setOnClickListener(v -> {
            if ( true) {
                binding.topWindow.animate().translationYBy(-binding.topWindow.getHeight()).setDuration(200).start();
                MaterialSharedAxis msa = new MaterialSharedAxis(MaterialSharedAxis.Z, true);
                msa.setDuration(800);
                TransitionManager.beginDelayedTransition(binding.coordinator, msa);
                binding.finalScreen.setVisibility(View.GONE);
                binding.part2View.setVisibility(View.VISIBLE);
                
                ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
                executor.execute(() -> {
                    SongMetadataHelper.getAllSongs(this, new SongLoadListener(){
                        @Override
                        public void onStarted(int i) {
                            binding.progressBar.setMax(i);
                        }
                        
                        @Override
                        public void onProgress(java.util.ArrayList<HashMap<String, Object>> songs, int count) {
                            binding.progressBar.setProgressCompat(count, true);
                        }
                
                        @Override
                        public void onComplete(java.util.ArrayList<HashMap<String, Object>> songs) {
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                DataManager.setDataInitialized();
                                Intent i = new Intent();
                                i.setClass(WelcomeActivity.this, MainActivity.class);
                                startActivity(i);
                                finish();
                            }, 1000);
                        }
                    });
                });
            } else {
                Snackbar.make(WelcomeActivity.this, binding.coordinator, "Please allow all necessary permissions first", Snackbar.LENGTH_SHORT).show(); 
            }
        });
            
        binding.joinButton.setOnClickListener(v -> {
            Intent i = new Intent();
            i.setAction(Intent.ACTION_VIEW);
            i.setData(Uri.parse("https://t.me/xmusiccommunity"));
            startActivity(i);
            
            MaterialSharedAxis msa = new MaterialSharedAxis(MaterialSharedAxis.X, true);
            TransitionManager.beginDelayedTransition(binding.coordinator, msa);
            binding.thirdScreen.setVisibility(View.GONE);
            binding.finalScreen.setVisibility(View.VISIBLE);
        });
        
        binding.firstGrantButton.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33) { 
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO);
            } else if (Build.VERSION.SDK_INT >= 30 && Build.VERSION.SDK_INT < 33) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", getPackageName())));
                    startActivity(intent);
                } catch (Exception e) {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        startActivity(intent);
                    } catch (Exception ex) {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    }
                }
    
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            }     
        });
        
        binding.secondGrantButton.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33) { 
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else if (Build.VERSION.SDK_INT <= 29) {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        });
        
        binding.beginButton.setOnClickListener(v -> {
            MaterialSharedAxis msa = new MaterialSharedAxis(MaterialSharedAxis.X, true);
            TransitionManager.beginDelayedTransition(binding.coordinator, msa);
            binding.firstScreen.setVisibility(View.GONE);
            binding.secondScreen.setVisibility(View.VISIBLE);
        });
        
        binding.screen2Button.setOnClickListener(v -> {
            MaterialSharedAxis msa = new MaterialSharedAxis(MaterialSharedAxis.X, true);
            TransitionManager.beginDelayedTransition(binding.coordinator, msa);
            binding.secondScreen.setVisibility(View.GONE);
            binding.thirdScreen.setVisibility(View.VISIBLE);
        });
        
        binding.screen3Button.setOnClickListener(v -> {
            MaterialSharedAxis msa = new MaterialSharedAxis(MaterialSharedAxis.X, true);
            TransitionManager.beginDelayedTransition(binding.coordinator, msa);
            binding.thirdScreen.setVisibility(View.GONE);
            binding.finalScreen.setVisibility(View.VISIBLE);
        });
    }

    public void setupPermsLaunchers() {
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            checkPerms();
        });
    }


    public void checkPerms() {
        if (Build.VERSION.SDK_INT >= 33) {
            boolean b1 = XUtils.checkPermissionAllowed(this, Manifest.permission.READ_MEDIA_AUDIO);
            boolean b2 = XUtils.checkPermissionAllowed(this, Manifest.permission.POST_NOTIFICATIONS);
            audiAccessAllowed = b1;
            binding.firstGrantButton.setEnabled(!b1);
            binding.firstGrantButton.setText(b1? "Granted" : "Grant");
            notificationsAllowed = b2;
            binding.secondGrantButton.setEnabled(!b2);
            binding.secondGrantButton.setText(b2? "Granted" : "Grant");
        } else if (30 <= Build.VERSION.SDK_INT && Build.VERSION.SDK_INT <= 32) {
            boolean b3 = Environment.isExternalStorageManager();
            binding.firstGrantButton.setEnabled(!b3);
            binding.firstGrantButton.setText(b3? "Granted" : "Grant");
        } else {
            boolean b3 = XUtils.checkPermissionAllowed(this, Manifest.permission.READ_EXTERNAL_STORAGE);
            storageReadAllowed = b3;
            binding.firstGrantButton.setEnabled(!b3);
            binding.firstGrantButton.setText(b3? "Granted" : "Grant");
            
        }
    }

    public void checkSDK() {
        if (Build.VERSION.SDK_INT <= 29) {
            binding.firstTitle.setText("Allow reading storage");
            binding.firstDesc.setText("Needed to find media on your device");
            binding.secondTitle.setText("Allow writing to storage");
            binding.secondDesc.setText("Needed to manage your songs");
        } else if (30 <= Build.VERSION.SDK_INT && Build.VERSION.SDK_INT <= 32) {
            binding.firstTitle.setText("allow all files access");
            binding.firstDesc.setText("Needed to read and manage your songs");
            binding.firstItem.setBackground(ContextCompat.getDrawable(this, R.drawable.checkable_background_round));
            binding.secondItem.setVisibility(View.GONE);
        } else {
            binding.firstTitle.setText("Allow media access");
            binding.firstDesc.setText("Needed to find media on your device");
            binding.secondTitle.setText("Allow notification permission");
            binding.secondDesc.setText("Needed to make Playback notification accessible via lock screen");
        }
    }
}