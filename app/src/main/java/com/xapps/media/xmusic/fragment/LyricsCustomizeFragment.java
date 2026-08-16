package com.xapps.media.xmusic.fragment;

import com.xapps.media.xmusic.activity.RootActivity;
import com.xapps.media.xmusic.common.SettingsItem;
import com.xapps.media.xmusic.data.DataManager;
import com.xapps.media.xmusic.utils.XUtils;

import java.util.ArrayList;
import java.util.List;

public class LyricsCustomizeFragment extends BasePrefsFragment {
     private RootActivity activity;

    @Override
    protected List<SettingsItem> provideItems() {
        activity = (RootActivity) getActivity();
        List<SettingsItem> settings = new ArrayList<>();

        settings.add(new SettingsItem(SettingsItem.TYPE_HEADER, "h1", "Lyrics font", "", null));
        settings.add(new SettingsItem(SettingsItem.TYPE_NAV, "font_size", "Font size", "Choose how big lyrics text should be", null));
        settings.add(new SettingsItem(SettingsItem.TYPE_SWITCH, "use_system_font", "Use System font", "Whether to use the app's font or the system's for rendering lyrics", null));
        settings.add(new SettingsItem(SettingsItem.TYPE_NAV, "font_weight", "Font weight", "Choose the font weight that suits you", null));
        settings.add(new SettingsItem(SettingsItem.TYPE_SWITCH, "rounded_font", "Round lyrics letters", "Make the lyrics letters' edges round", null));
        settings.add(new SettingsItem(SettingsItem.TYPE_HEADER, "h2", "Lyrics Appearance", "", null));
        settings.add(new SettingsItem(SettingsItem.TYPE_SWITCH, "lyrics_gradient_background", "Enable lyrics gradient", "Display a beautiful gradient background behind the lyrics, might impact performance", null));
        settings.add(new SettingsItem(SettingsItem.TYPE_SWITCH, "lyrics_blur", "Blur Lyrics", "Apply a blur effect for inactive lyrics lines, might impact performance", null));
        settings.add(new SettingsItem(SettingsItem.TYPE_SWITCH, "lyrics_sparkles", "Enable Lyrics Sparkles", "Apply a Sparkling effect to the lyrics while being sung", null));
        settings.add(new SettingsItem(SettingsItem.TYPE_HEADER, "h3", "Lyrics Behavior", "", null));
        settings.add(new SettingsItem(SettingsItem.TYPE_SWITCH, "lyrics_anticipation", "Enable lyrics anticipation", "Lyrics will always go to next line before it's sung", null));
        settings.add(new SettingsItem(SettingsItem.TYPE_SWITCH, "lyrics_elastic_scroll", "Enable Staggered Scrolling", "Make the lyrics lines scroll in a delayed way for better immersion", null));
        settings.add(new SettingsItem(SettingsItem.TYPE_SWITCH, "lyrics_elastic_manual_scroll", "Staggered manual scrolling", "Apply the staggered scroll effect to user manual scrolling, might feel weird", null));

        return settings;
    }

    @Override
    public String getFragmentTitle() {
        return "Customize Lyrics";
    }

    @Override
    protected void onItemSelected(SettingsItem item) {
        switch (item.id) {
            default -> {
                XUtils.showMessage(getActivity(), "Feature to be added soon");
            }
        }
    }

    @Override
    protected void onSwitchChanged(SettingsItem item, boolean value) {
        super.onSwitchChanged(item, value);
        switch (item.id) {
            case "lyrics_elastic_scroll", "lyrics_elastic_manual_scroll", "lyrics_sparkles" -> {
                activity.updateLyrics();
            }
            default -> {
                XUtils.showMessage(getActivity(), "Feature to be added soon");
            }
        }
    }
}
