package com.xapps.media.xmusic.fragment;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.xapps.media.xmusic.R;
import com.xapps.media.xmusic.activity.MainActivity;
import com.xapps.media.xmusic.common.SettingsItem;
import com.xapps.media.xmusic.data.DataManager;
import com.xapps.media.xmusic.databinding.LayoutToggleCustomizeDialogBinding;
import com.xapps.media.xmusic.service.PlayerService;
import com.xapps.media.xmusic.utils.ColorPaletteUtils;
import com.xapps.media.xmusic.utils.MaterialColorUtils;
import com.xapps.media.xmusic.utils.XUtils;
import com.xapps.media.xmusic.widget.NewPlayerToggle;
import java.util.ArrayList;
import java.util.List;

public class NowPlayingEditFragment extends BasePrefsFragment {

    private MainActivity activity;
    
    @Override
    protected List<SettingsItem> provideItems() {
        activity = (MainActivity) getActivity();
        List<SettingsItem> items = new ArrayList<>();

        items.add(new SettingsItem(
            SettingsItem.TYPE_HEADER,
            "",
            "Components","", 
            null
        ));

        /*items.add(new SettingsItem(
            SettingsItem.TYPE_NAV,
            "seekbar_customize",
            "Seekbar",
            "Customize your seekbar with a set of tweaks",
            null
        ));*/
        
        items.add(new SettingsItem(
            SettingsItem.TYPE_SWITCH,
            "stable_colors",
            "Use Dynamic Colors for Now Playing UI",
            "All components in the Now Playing interface will use App Colors instead of Album Art's",
            null
        ));
        
        items.add(new SettingsItem(
            SettingsItem.TYPE_NAV,
            "toggle_customize",
            "Play/Pause toggle",
            "Customize states shapes and animation speed",
            null
        ));

        return items;
    }

    @Override
    protected void onNavigate(SettingsItem item) {
        switch (item.id) {
            case "toggle_customize" :
            int oldStartShape = DataManager.sp.getInt("player_toggle_start_shape", NewPlayerToggle.SHAPE_SQUARE);
            int oldEndShape = DataManager.sp.getInt("player_toggle_target_shape", NewPlayerToggle.SHAPE_COOKIE_12);
                LayoutToggleCustomizeDialogBinding dbinding = LayoutToggleCustomizeDialogBinding.inflate(getLayoutInflater());
                MaterialAlertDialogBuilder builder =
                    new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Customize toggle shapes")
                    .setView(dbinding.getRoot())
                    .setPositiveButton("Save", (dialog, which) -> {
                        activity.getBinding().toggleView.reloadShapes();
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        DataManager.sp.edit().putInt("player_toggle_start_shape", oldStartShape).putInt("player_toggle_target_shape", oldEndShape).apply();
                    });
                dbinding.startDropdown.setText(dbinding.startDropdown.getAdapter().getItem(oldStartShape).toString(), false);
                dbinding.endDropdown.setText(dbinding.endDropdown.getAdapter().getItem(oldEndShape).toString(), false);
                dbinding.previewToggle.startAnimation();
                dbinding.previewToggle.setIconColor(MaterialColorUtils.colorPrimary);
                dbinding.previewToggle.setShapeColor(MaterialColorUtils.colorPrimaryContainer);
                dbinding.startDropdown.setOnItemClickListener((parent, view, position, id) -> {
                    dbinding.previewToggle.setStartShape(position);
                    dbinding.previewToggle.reloadShapes();
                });
                dbinding.endDropdown.setOnItemClickListener((parent, view, position, id) -> {
                    dbinding.previewToggle.setTargetShape(position);
                    dbinding.previewToggle.reloadShapes();
                    dbinding.previewToggle.maxProgress();
                });
                builder.setOnDismissListener(dialog -> {
                    if (XUtils.areBlursOrDynamicColorsSupported() && DataManager.isBlurOn()) XUtils.animateBlur(getBinding().coordinator, false, 50);
                });
                builder.setOnCancelListener(dialog -> {
                    DataManager.sp.edit().putInt("player_toggle_start_shape", oldStartShape).putInt("player_toggle_target_shape", oldEndShape).apply();
                });
                if (XUtils.areBlursOrDynamicColorsSupported() && DataManager.isBlurOn()) XUtils.animateBlur(getBinding().coordinator, true, 300);
                builder.create().show();
            break;
            
            default :
            break;
        }
        /*try {
        Fragment f = item.destinationFragment;
        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_frag, f)
                .addToBackStack(null)
                .commit();
        } catch (Exception ignored) {}*/
    }

    @Override
    protected void onSwitchChanged(SettingsItem item, boolean value) {
        super.onSwitchChanged(item, value);
        switch (item.id) {
            case "stable_colors":
                activity.getService().sendUpdate(false);
            break;
        }
    }
}
