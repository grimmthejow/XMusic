package com.xapps.media.xmusic.fragment;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.transition.ChangeBounds;
import androidx.transition.Fade;
import androidx.transition.TransitionManager;
import androidx.transition.TransitionSet;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.MaterialSharedAxis;
import com.rtugeek.android.colorseekbar.thumb.DefaultThumbDrawer;
import com.rtugeek.android.colorseekbar.thumb.ThumbDrawer;
import com.xapps.media.xmusic.activity.MainActivity;
import com.xapps.media.xmusic.data.DataManager;
import com.xapps.media.xmusic.databinding.FragmentSettingsBinding;
import com.xapps.media.xmusic.utils.XUtils;
import com.xapps.media.xmusic.R;

public class SettingsFragment extends BaseFragment {
    
    private FragmentSettingsBinding binding;
    private MainActivity activity;
        
    @NonNull
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		binding = FragmentSettingsBinding.inflate(inflater, container, false);
        activity = (MainActivity) getActivity();
        activity.setSettingsFragmentInstance(this);
		setupUI();
        setupListeners();
		return binding.getRoot();
	}

    private void setupUI() {
        binding.collapsingtoolbar.setExpandedTitleTextAppearance(R.style.ExpandedAppBarTextStyle);
        TextView t = (TextView) binding.toolbar.getChildAt(0);
        t.setTypeface(ResourcesCompat.getFont(getActivity(), R.font.google_sans_flex));
        t.setFontVariationSettings("'ROND' 100, 'wght' 500");
    }
    
    private void setupListeners() {
        binding.firstCategory.setOnClickListener(v -> {
            getActivity().getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.settings_frag, new AppearanceFragment())
            .addToBackStack("root")
            .commit();
        });
        
        binding.secondCategory.setOnClickListener(v -> {
            getActivity().getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.settings_frag, new NowPlayingEditFragment())
            .addToBackStack("root")
            .commit();
        });
        
        binding.infoCategory.setOnClickListener(v -> {
            getActivity().getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.settings_frag, new AboutFragment())
            .addToBackStack("root")
            .commit();
        });
    }

}