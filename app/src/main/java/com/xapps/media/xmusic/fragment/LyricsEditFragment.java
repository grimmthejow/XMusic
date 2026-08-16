package com.xapps.media.xmusic.fragment;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.*;
import androidx.annotation.*;
import androidx.core.content.res.ResourcesCompat;
import com.google.android.material.slider.LabelFormatter;
import com.xapps.media.xmusic.activity.MainActivity;
import com.xapps.media.xmusic.activity.RootActivity;
import com.xapps.media.xmusic.data.DataManager;
import com.xapps.media.xmusic.databinding.*;
import com.xapps.media.xmusic.R;

public class LyricsEditFragment extends BaseFragment {

    private ActivityMainBinding activityBinding;
    private FragmentLyricsEditBinding binding;
    
    private RootActivity activity;
    
    private int width = 100;
    private int weight = 500;
    private int optical = 18;
    private int grade = 0;
    private int roundness = 0;
    private float slant = 0.0f;

    @Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		binding = FragmentLyricsEditBinding.inflate(inflater, container, false);
        activity = (RootActivity) getActivity();
        init();
        return binding.getRoot();
	}

    private void init() {
        setupFonts();
		setupSliders();
		setupListeners();
    }
	
	private void setupListeners() {
		binding.opticalSlider.addOnChangeListener((s, v, fromUser) -> {
            optical = (int) v;
            applyConfigs();
        });

        binding.widthSlider.addOnChangeListener((s, v, fromUser) -> {
            width = (int) v;
            applyConfigs();
        });

        binding.weightSlider.addOnChangeListener((s, v, fromUser) -> {
            weight = (int) v;
            applyConfigs();
        });

        binding.gradeSlider.addOnChangeListener((s, v, fromUser) -> {
            grade = (int) v;
            applyConfigs();
        });

        binding.roundnessSlider.addOnChangeListener((s, v, fromUser) -> {
            roundness = (int) v;
            applyConfigs();
        });

        binding.slantSlider.addOnChangeListener((s, v, fromUser) -> {
            slant = v;
            applyConfigs();
        });
	}
	
	private void setupSliders() {
		binding.opticalSlider.setValue(18f);
		binding.opticalSlider.setValueFrom(6f);
		binding.opticalSlider.setValueTo(144f);
		binding.opticalSlider.setStepSize(1f);
		binding.opticalSlider.setLabelBehavior(LabelFormatter.LABEL_FLOATING);
		binding.widthSlider.setValue(100f);
		binding.widthSlider.setValueFrom(25f);
		binding.widthSlider.setValueTo(150f);
		binding.widthSlider.setStepSize(1f);
		binding.widthSlider.setLabelBehavior(LabelFormatter.LABEL_FLOATING);
		binding.weightSlider.setValue(500f);
		binding.weightSlider.setValueFrom(1f);
		binding.weightSlider.setValueTo(1000f);
		binding.weightSlider.setStepSize(1f);
		binding.weightSlider.setLabelBehavior(LabelFormatter.LABEL_FLOATING);
		binding.gradeSlider.setValue(0f);
		binding.gradeSlider.setValueFrom(0f);
		binding.gradeSlider.setValueTo(100f);
		binding.gradeSlider.setStepSize(1f);
		binding.gradeSlider.setLabelBehavior(LabelFormatter.LABEL_FLOATING);
		binding.roundnessSlider.setValue(0f);
		binding.roundnessSlider.setValueFrom(0f);
		binding.roundnessSlider.setValueTo(100f);
		binding.roundnessSlider.setStepSize(1f);
		binding.roundnessSlider.setLabelBehavior(LabelFormatter.LABEL_FLOATING);
		binding.slantSlider.setValue(0f);
		binding.slantSlider.setValueFrom(-5f);
		binding.slantSlider.setValueTo(0f);
		binding.slantSlider.setStepSize(0.1f);
		binding.slantSlider.setLabelBehavior(LabelFormatter.LABEL_FLOATING);
	}

    private void setupFonts() {
        Typeface customFont = ResourcesCompat.getFont(getContext(), R.font.gsans_flex_full);
        binding.mainLine.setTypeface(Typeface.create(customFont, Typeface.NORMAL));
        binding.romajiLine.setTypeface(Typeface.create(customFont, Typeface.NORMAL));
        binding.mainLine.setFontFeatureSettings("'liga' 0, 'clig' 0");
        binding.romajiLine.setFontFeatureSettings("'liga' 0, 'clig' 0");
		applyConfigs();
    }

    private void applyConfigs() {
		String fontConfig = new StringBuilder()
            .append("'wdth' ").append(width).append(", ")
            .append("'wght' ").append(weight).append(", ")
            .append("'opsz' ").append(optical).append(", ")
            .append("'GRAD' ").append(grade).append(", ")
            .append("'ROND' ").append(roundness).append(", ")
            .append("'slnt' ").append(slant)
        .toString();
		DataManager.setFontConfig(fontConfig);
        if (activity.getUIManager() != null) activity.getUIManager().updateFontConfig();
        binding.mainLine.setFontVariationSettings(fontConfig);
        binding.romajiLine.setFontVariationSettings(fontConfig);
	}
}