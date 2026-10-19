package dev.russell.focuspodcast.fragment.pref;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.collection.ArrayMap;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceManager;

import org.greenrobot.eventbus.EventBus;

import java.util.Map;

import dev.russell.focuspodcast.R;
import dev.russell.focuspodcast.activity.SettingsActivity;
import dev.russell.focuspodcast.core.pref.Prefs;
import dev.russell.focuspodcast.event.settings.AdSkipChangedEvent;

/**
 * The global ad-skip defaults. Each podcast can override "Detect ads" in its own settings, so
 * the other options here matter even while the global switch is off.
 */
public class AdSkipPrefFragment extends AbsSettingsFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_ad_skip);
        buildAdSkipSensitivityPreference();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((SettingsActivity) getActivity()).setTitle(R.string.pref_ad_skip_category);
        PreferenceManager.getDefaultSharedPreferences(getContext()).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        PreferenceManager.getDefaultSharedPreferences(getContext()).unregisterOnSharedPreferenceChangeListener(this);
    }

    /** The list preference shows the chosen entry in its summary, both on open and after every change. */
    private void buildAdSkipSensitivityPreference() {
        final Resources res = requireActivity().getResources();
        final Map<String, String> options = new ArrayMap<>();
        String[] keys = res.getStringArray(R.array.ad_skip_sensitivity_values);
        String[] values = res.getStringArray(R.array.ad_skip_sensitivity_options);
        for (int i = 0; i < keys.length; i++) {
            options.put(keys[i], values[i]);
        }

        ListPreference pref = findPreference(Prefs.PREF_AD_SKIP_SENSITIVITY);
        if (pref == null) {
            throw new IllegalArgumentException("Preference with key '"
                    + Prefs.PREF_AD_SKIP_SENSITIVITY + "' is not found");
        }
        pref.setSummary(res.getString(R.string.pref_ad_skip_sensitivity_sum, options.get(pref.getValue())));
        pref.setOnPreferenceChangeListener((preference, newValue) -> {
            if (!(newValue instanceof String)) {
                return false;
            }
            pref.setSummary(res.getString(R.string.pref_ad_skip_sensitivity_sum,
                    options.get((String) newValue)));
            return true;
        });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (Prefs.PREF_AD_SKIP_ENABLED.equals(key)
                || Prefs.PREF_AD_SKIP_SENSITIVITY.equals(key)
                || Prefs.PREF_AD_SKIP_ANALYZE_ON_DOWNLOAD.equals(key)
                || Prefs.PREF_AD_SKIP_SHOW_SNACKBAR.equals(key)) {
            // 0 means "the global settings changed", not one feed's switch
            EventBus.getDefault().post(new AdSkipChangedEvent(0));
        }
    }

    @Override
    public void invalidateSettings() {

    }
}
