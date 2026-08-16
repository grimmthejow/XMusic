package com.xapps.media.xmusic.fragment;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewKt;
import androidx.transition.ChangeBounds;
import androidx.transition.Fade;
import androidx.transition.TransitionManager;
import androidx.transition.TransitionSet;
import com.rtugeek.android.colorseekbar.thumb.DefaultThumbDrawer;
import com.xapps.media.xmusic.activity.RootActivity;
import com.xapps.media.xmusic.data.DataManager;
import com.xapps.media.xmusic.databinding.FragmentAppearanceBinding;
import com.xapps.media.xmusic.utils.XUtils;
import com.xapps.media.xmusic.R;
import kotlin.Unit;

public class AppearanceFragment extends SubFragment {
    
    private FragmentAppearanceBinding binding;
    private RootActivity activity;
        
    @NonNull
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		binding = FragmentAppearanceBinding.inflate(inflater, container, false);
        activity = (RootActivity) getActivity();
		setupUI();
        setupListeners();
		return binding.getRoot();
	}

    private void setupUI() {
        DefaultThumbDrawer dtd = new DefaultThumbDrawer(XUtils.convertToPx(getActivity(), 35), Color.WHITE, Color.WHITE);
        dtd.setRingBorderSize(XUtils.convertToPx(getActivity(), 1.5f));
        binding.colorSeekBar.setThumbDrawer(dtd);
        binding.iconSwitch.setChecked(DataManager.isNewIconEnabled());
        binding.colorSeekBar.setProgress(DataManager.getProgress());
        binding.firstSwitch.setChecked(XUtils.areBlursOrDynamicColorsSupported() && DataManager.isDynamicColorsOn());
        binding.blurSwitch.setChecked(XUtils.areBlursOrDynamicColorsSupported() && DataManager.isBlurOn());
        binding.oledSwitch.setChecked(DataManager.isOledThemeEnabled());
        binding.secondSwitch.setChecked(XUtils.areBlursOrDynamicColorsSupported() && DataManager.isCustomColorsOn());
        binding.colorSeekBar.setVisibility(XUtils.areBlursOrDynamicColorsSupported() && DataManager.isCustomColorsOn()? View.VISIBLE : View.GONE);
        binding.applyButton.setVisibility(XUtils.areBlursOrDynamicColorsSupported() && DataManager.isCustomColorsOn()? View.VISIBLE : View.GONE);
        binding.colorSeekBar.setEnabled(XUtils.areBlursOrDynamicColorsSupported() && DataManager.isDynamicColorsOn());
        binding.secondSwitch.setEnabled(XUtils.areBlursOrDynamicColorsSupported() && DataManager.isDynamicColorsOn());
        binding.secondPref.setEnabled(XUtils.areBlursOrDynamicColorsSupported() && DataManager.isDynamicColorsOn());
        binding.applyButton.setEnabled(XUtils.areBlursOrDynamicColorsSupported() && DataManager.isDynamicColorsOn());
        binding.secondContent.setAlpha(XUtils.areBlursOrDynamicColorsSupported() && DataManager.isDynamicColorsOn()? 1f : 0.5f);
        binding.colorSeekBar.setAlpha(XUtils.areBlursOrDynamicColorsSupported() && DataManager.isDynamicColorsOn()? 1f : 0.5f);
        binding.blurPref.setEnabled(XUtils.areBlursOrDynamicColorsSupported());
        binding.blurPref.setAlpha(XUtils.areBlursOrDynamicColorsSupported()? 1f : 0.5f);
        binding.firstPref.setEnabled(XUtils.areBlursOrDynamicColorsSupported());
        binding.firstPref.setAlpha(XUtils.areBlursOrDynamicColorsSupported()? 1f : 0.5f);
        binding.applyButton.setEnabled(false);
		binding.collapsingtoolbar.setScrimAnimationDuration(0);
        switch (DataManager.getThemeMode()) {
            case 0:
                binding.systemTheme.setChecked(true);
                break;
            case 1:
                binding.darkTheme.setChecked(true);
                break;
            case 2:
                binding.lightTheme.setChecked(true);
                break;
        }
        
        if (Build.VERSION.SDK_INT < 31) {
            
        }
        ViewKt.doOnLayout(activity.getBinding().bottomNavigation, v -> {
            binding.mainContainer.setPadding(binding.mainContainer.getPaddingRight(), binding.mainContainer.getPaddingTop(), binding.mainContainer.getPaddingLeft(), binding.mainContainer.getPaddingLeft());
            return Unit.INSTANCE;
        });
        
    }

    private void setupListeners() {
        binding.toolbar.setNavigationOnClickListener(v -> {
            getActivity().getOnBackPressedDispatcher().onBackPressed();
			activity.getUIManager().hideBnv(false);
        });
        binding.secondPref.setOnClickListener(v -> {
            binding.secondSwitch.setChecked(!binding.secondSwitch.isChecked());
            DataManager.setCustomColorsEnabled(binding.secondSwitch.isChecked());
            activity.getUIManager().saveState();
            getActivity().recreate();
        });
        binding.blurPref.setOnClickListener(v -> {
            binding.blurSwitch.setChecked(!binding.blurSwitch.isChecked());
            DataManager.setBlurOn(binding.blurSwitch.isChecked());
            activity.loadSettings();
        });
        binding.oldePref.setOnClickListener(v -> {
            binding.oledSwitch.setChecked(!binding.oledSwitch.isChecked());
            DataManager.setOledTheme(binding.oledSwitch.isChecked());
            activity.getUIManager().saveState();
            getActivity().recreate();
        });
        binding.firstPref.setOnClickListener(v -> {
            binding.firstSwitch.setChecked(!binding.firstSwitch.isChecked());
            DataManager.setDynamicColorsEnabled(binding.firstSwitch.isChecked());
            activity.getUIManager().saveState();
            getActivity().recreate();
        });
        binding.colorSeekBar.setOnColorChangeListener((progress, color) -> {
            binding.applyButton.setEnabled(binding.colorSeekBar.getProgress() != DataManager.getCustomColor());
        });
        binding.icon1.setOnClickListener(v -> {
            activity.showInfoDialog("Experimental feature", R.drawable.ic_test_tube, "This is a feature that's still under testing and might be unstable or buggy for some users.", "OK", activity.getBinding().Coordinator);
        });
        binding.systemTheme.setOnClickListener(v -> {
            activity.getUIManager().saveState();
            DataManager.setThemeMode(0);
            XUtils.setThemeMode("auto");
        });
        binding.darkTheme.setOnClickListener(v -> {
            activity.getUIManager().saveState();
            DataManager.setThemeMode(1);
            XUtils.setThemeMode("dark");
        });
        binding.lightTheme.setOnClickListener(v -> {
            activity.getUIManager().saveState();
            DataManager.setThemeMode(2);
            XUtils.setThemeMode("light");
        });
        binding.applyButton.setOnClickListener(v -> {
            DataManager.setProgress(binding.colorSeekBar.getProgress());
            DataManager.setCustomColor(XUtils.normalizeColor(binding.colorSeekBar.getColor()));
            activity.getUIManager().saveState();
            getActivity().recreate();
        });
        binding.iconPref.setOnClickListener(v -> {
            binding.iconSwitch.setChecked(!binding.iconSwitch.isChecked());
            DataManager.setNewIconEnabled(binding.iconSwitch.isChecked());
            setNewIconEnabled(binding.iconSwitch.isChecked());
        });
    }

    private void showViews(boolean b) {
        ViewGroup root = binding.coordinator;
        TransitionSet set = new TransitionSet()
        .addTransition(new Fade(Fade.OUT))
        .addTransition(new ChangeBounds())
        .setInterpolator(new AccelerateDecelerateInterpolator())
        .setDuration(250);

        TransitionManager.beginDelayedTransition(root, set);
        if (b) {
            binding.applyButton.setVisibility(View.VISIBLE);
            binding.colorSeekBar.setVisibility(View.VISIBLE);
        } else {
            binding.colorSeekBar.setVisibility(View.GONE);
            binding.applyButton.setVisibility(View.GONE);
        }
    }
    
    private void setNewIconEnabled(boolean b) {
        PackageManager pm = getActivity().getPackageManager();


        pm.setComponentEnabledSetting(
            new ComponentName(getActivity(), b? "com.xapps.media.xmusic.LauncherAlt" : "com.xapps.media.xmusic.LauncherDefault"),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        );

        pm.setComponentEnabledSetting(
            new ComponentName(getActivity(), b? "com.xapps.media.xmusic.LauncherDefault" : "com.xapps.media.xmusic.LauncherAlt"),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        );
    }

}
