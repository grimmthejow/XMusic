package com.xapps.media.xmusic.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.view.*;
import androidx.annotation.*;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import com.xapps.media.xmusic.activity.RootActivity;
import com.xapps.media.xmusic.common.SettingsItem;
import com.xapps.media.xmusic.databinding.ActivityMainBinding;
import com.xapps.media.xmusic.databinding.FragmentExperimentBinding;
import com.xapps.media.xmusic.models.BottomSheetBehavior;
import com.xapps.media.xmusic.utils.Log;
import com.xapps.media.xmusic.utils.XUtils;
import com.xapps.media.xmusic.widget.ExpressiveSliderLayout;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.xapps.media.xmusic.R;

public class ExperimentsFragment extends SubPrefsFragment {

    private RootActivity activity;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected List<SettingsItem> provideItems() {
        activity = (RootActivity) getActivity();
        List<SettingsItem> items = new ArrayList<>();
        items.add(new SettingsItem(SettingsItem.TYPE_HEADER, "h1", "Miscellaneous", "", null));
        items.add(new SettingsItem(SettingsItem.TYPE_NAV, "crash_app", "Crash the app", "Click to instantly crash XMusic", null));
        items.add(new SettingsItem(SettingsItem.TYPE_NAV, "export_logs", "Export app logs", "This collect all XMusic logs and allow to share them as a file", null));
        items.add(new SettingsItem(SettingsItem.TYPE_NAV, "show_bs", "Collapse Bottom sheet", "This will set miniplayer State to STATE_COLLAPSED", null));
        // items.add(new SettingsItem(SettingsItem.TYPE_NAV, "test_frag", "Open test fragment", "Test some new components under testing", new TestFragment()));
        
        return items;
    }

    @Override
    protected void onItemSelected(SettingsItem item) {
        switch (item.id) {
            case "crash_app" :
                throw new RuntimeException("Oh no! XMusic crashed :(");
            case "export_logs" :
                XUtils.showMessage(getActivity(), "Exporting logs...");
                exportLogs(getActivity());
                break;
            case "show_bs" :
                activity.getBinding().miniPlayer.setState(ExpressiveSliderLayout.STATE_COLLAPSED);
                break;
            case "test_frag" :
                openFragment(item.destinationFragment);
                break;
        }
    }

    private void exportLogs(Activity c) {
        executorService.execute(() -> {
            try {
                java.lang.Process p = new ProcessBuilder()
                    .command("logcat", "-dball", "--uid=" + android.os.Process.myUid())
                    .redirectErrorStream(true)
                    .start();

                File dir = new File(c.getCacheDir(), "logs");
                dir.mkdirs();

                File out = new File(dir, "xmusic_log.txt");
				if (out.exists())
                    out.delete();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                     FileWriter writer = new FileWriter(out)) {
                    writer.write("SDK: " + Build.VERSION.SDK_INT + "\n");
                    writer.write("Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n\n");

                    String line;
                    while ((line = reader.readLine()) != null) {
                        writer.write(line);
                        writer.write('\n');
                    }
                }

                p.waitFor();
                p.destroy();

                Uri uri = FileProvider.getUriForFile(
                    c,
                    c.getPackageName() + ".provider",
                    out
                );

                c.runOnUiThread(() -> {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent, "Export logs"));
                });

            } catch (Exception e) {
                Log.e("XMusic", "Log export failed", e);
            }
        });
    }

    private void openFragment(Fragment f) {
        requireActivity()
        .getSupportFragmentManager()
        .beginTransaction()
        .setReorderingAllowed(true)
        .replace(R.id.settings_frag, f)
        .addToBackStack(null)
        .commit();
    }

    @Override
    public String getFragmentTitle() {
        return "Advanced";
    }
}
