package com.xapps.media.xmusic.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.transition.TransitionManager;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.model.KeyPath;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.MaterialSharedAxis;
import com.xapps.media.xmusic.R;
import com.xapps.media.xmusic.utils.XUtils;
import com.xapps.media.xmusic.databinding.ActivityWelcomeBinding;
import com.xapps.media.xmusic.utils.MaterialColorUtils;

public class WelcomeActivity extends AppCompatActivity {
    
    private ActivityWelcomeBinding binding;
    private boolean notificationsAllowed, audiAccessAllowed, storageReadAllowed;
    private ActivityResultLauncher<String> requestPermissionLauncher;
            
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this, 
            SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        );
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
        XUtils.setMargins(binding.screen2Button, 0, 0, XUtils.convertToPx(this, 16f), XUtils.getNavigationBarHeight(this));
    }

    private void setupClickListeners() {
        binding.startButton.setOnClickListener(v -> {
            if (allPermsAllowed()) {
                Intent i = new Intent();
                i.setClass(WelcomeActivity.this, RootActivity.class);
                startActivity(i);
                finish();
            } else {
                Snackbar.make(WelcomeActivity.this, binding.coordinator, getString(R.string.welcome_permissions_error), Snackbar.LENGTH_SHORT).show(); 
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
            } else if (Build.VERSION.SDK_INT >= 30) {
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
            } else {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
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

    private boolean allPermsAllowed() {
        if (Build.VERSION.SDK_INT >= 33) {
            return audiAccessAllowed && notificationsAllowed;
        } else if (30 <= Build.VERSION.SDK_INT) {
            return Environment.isExternalStorageManager();
        } else {
            return storageReadAllowed;
        }
    }

    public void setupPermsLaunchers() {
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> checkPerms());
    }

    public void checkPerms() {
        if (Build.VERSION.SDK_INT >= 33) {
            boolean b1 = XUtils.checkPermissionAllowed(this, Manifest.permission.READ_MEDIA_AUDIO);
            boolean b2 = XUtils.checkPermissionAllowed(this, Manifest.permission.POST_NOTIFICATIONS);
            audiAccessAllowed = b1;
            binding.firstGrantButton.setEnabled(!b1);
            binding.firstGrantButton.setText(b1? getString(R.string.granted) : getString(R.string.grant));
            notificationsAllowed = b2;
            binding.secondGrantButton.setEnabled(!b2);
            binding.secondGrantButton.setText(b2? getString(R.string.granted) : getString(R.string.grant));
        } else if (30 <= Build.VERSION.SDK_INT) {
            boolean b3 = Environment.isExternalStorageManager();
            binding.firstGrantButton.setEnabled(!b3);
            binding.firstGrantButton.setText(b3? getString(R.string.granted) : getString(R.string.grant));
        } else {
            boolean b3 = XUtils.checkPermissionAllowed(this, Manifest.permission.READ_EXTERNAL_STORAGE);
            binding.firstGrantButton.setEnabled(!b3);
            binding.firstGrantButton.setText(b3? getString(R.string.granted) : getString(R.string.grant));
            boolean b4 = XUtils.checkPermissionAllowed(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            binding.secondGrantButton.setEnabled(!b4);
            binding.secondGrantButton.setText(b4? getString(R.string.granted) : getString(R.string.grant));
            storageReadAllowed = b3 && b4;
        }
    }

    @SuppressLint("SetTextI18n")
    public void checkSDK() {
        if (Build.VERSION.SDK_INT <= 29) {
            binding.firstTitle.setText(getString(R.string.welcome_permission_read_storage_title));
            binding.firstDesc.setText(getString(R.string.welcome_permission_read_storage_desc));
            binding.secondTitle.setText(getString(R.string.welcome_permission_write_storage_title));
            binding.secondDesc.setText(getString(R.string.welcome_permission_write_storage_desc));
        } else if (Build.VERSION.SDK_INT <= 32) {
            binding.firstTitle.setText(getString(R.string.welcome_permission_all_files_title));
            binding.firstDesc.setText(getString(R.string.welcome_permission_all_files_desc));
            binding.firstItem.setBackground(ContextCompat.getDrawable(this, R.drawable.checkable_background_round));
            binding.secondItem.setVisibility(View.GONE);
        } else {
            binding.firstTitle.setText(getString(R.string.welcome_permission_media_access_title));
            binding.firstDesc.setText(getString(R.string.welcome_permission_media_access_desc));
            binding.secondTitle.setText(getString(R.string.welcome_permission_notification_title));
            binding.secondDesc.setText(getString(R.string.welcome_permission_notification_desc));
        }
    }
}