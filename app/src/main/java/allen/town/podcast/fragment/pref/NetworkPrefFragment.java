package allen.town.podcast.fragment.pref;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.preference.PreferenceManager;
import allen.town.podcast.R;
import allen.town.podcast.activity.SettingsActivity;
import allen.town.podcast.core.pref.Prefs;
import allen.town.podcast.core.util.download.AutoUpdateManager;
import allen.town.podcast.dialog.ProxyDialog;



public class NetworkPrefFragment extends AbsSettingsFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String PREF_SCREEN_AUTODL = "prefAutoDownloadSettings";
    private static final String PREF_PROXY = "prefProxy";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_network);
        setupNetworkScreen();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((SettingsActivity) getActivity()).setTitle(R.string.network_pref);
        PreferenceManager.getDefaultSharedPreferences(getContext()).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        PreferenceManager.getDefaultSharedPreferences(getContext()).unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        setParallelDownloadsText(Prefs.getParallelDownloads());
    }

    private void setupNetworkScreen() {
        findPreference(PREF_SCREEN_AUTODL).setOnPreferenceClickListener(preference -> {
            ((SettingsActivity) getActivity()).openScreen(R.xml.pref_auto_download);
            return true;
        });

        findPreference(Prefs.PREF_PARALLEL_DOWNLOADS)
                .setOnPreferenceChangeListener(
                        (preference, o) -> {
                            if (o instanceof Integer) {
                                setParallelDownloadsText((Integer) o);
                            }
                            return true;
                        }
                );
        // validate and set correct value: number of downloads between 1 and 50 (inclusive)
        findPreference(PREF_PROXY).setOnPreferenceClickListener(preference -> {
            ProxyDialog dialog = new ProxyDialog(getActivity());
            dialog.show();
            return true;
        });
    }

    private void setParallelDownloadsText(int downloads) {
        final Resources res = getActivity().getResources();
        String s = res.getString(R.string.parallel_downloads, downloads);
        findPreference(Prefs.PREF_PARALLEL_DOWNLOADS).setSummary(s);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if ("pref_mobile_update_types".equals(key) && getContext() != null) {
            // the multi-select writes the pref directly; the periodic refresh worker bakes
            // the metered/unmetered constraint in at schedule time, so reschedule it
            AutoUpdateManager.restartUpdateAlarm(getContext().getApplicationContext());
        }
    }

    @Override
    public void invalidateSettings() {

    }
}


