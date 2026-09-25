package allen.town.podcast.fragment.pref;

import static allen.town.podcast.core.pref.Prefs.PREF_FULL_LOCK_SCREEN;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import allen.town.podcast.theme.util.VersionUtils;
import allen.town.podcast.BuildConfig;
import allen.town.podcast.MyApp;
import allen.town.podcast.R;
import allen.town.podcast.activity.SettingsActivity;
import allen.town.podcast.core.playback.NowPlayingScreen;
import allen.town.podcast.core.pref.Prefs;
import allen.town.podcast.dialog.SkipPrefDialog;
import allen.town.podcast.dialog.PlaySpeedDialog;
import allen.town.podcast.event.settings.AdSkipChangedEvent;
import allen.town.podcast.event.settings.AudioEffectsChangedEvent;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Map;

public class PlaybackPrefFragment extends AbsSettingsFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String PREF_PLAYBACK_SPEED_LAUNCHER = "prefPlaybackSpeedLauncher";
    private static final String PREF_PLAYBACK_REWIND_DELTA_LAUNCHER = "prefPlaybackRewindDeltaLauncher";
    private static final String PREF_PLAYBACK_FAST_FORWARD_DELTA_LAUNCHER = "prefPlaybackFastForwardDeltaLauncher";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_playback);

        setupPlaybackScreen();
        buildSmartMarkAsPlayedPreference();
        buildAdSkipSensitivityPreference();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((SettingsActivity) getActivity()).setTitle(R.string.playback_pref);
        PreferenceManager.getDefaultSharedPreferences(getContext()).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        ((SettingsActivity) getActivity()).setTitle(R.string.playback_pref);
        PreferenceManager.getDefaultSharedPreferences(getContext()).unregisterOnSharedPreferenceChangeListener(this);
    }

    private void setupPlaybackScreen() {
        final Activity activity = getActivity();

        findPreference(PREF_PLAYBACK_SPEED_LAUNCHER).setOnPreferenceClickListener(preference -> {
            PlaySpeedDialog.newGlobalDefaultInstance().show(getChildFragmentManager(), null);
            return true;
        });
        findPreference(PREF_PLAYBACK_REWIND_DELTA_LAUNCHER).setOnPreferenceClickListener(preference -> {
            SkipPrefDialog.showSkipPreference(activity, SkipPrefDialog.SkipDirection.SKIP_REWIND, null);
            return true;
        });
        findPreference(PREF_PLAYBACK_FAST_FORWARD_DELTA_LAUNCHER).setOnPreferenceClickListener(preference -> {
            SkipPrefDialog.showSkipPreference(activity, SkipPrefDialog.SkipDirection.SKIP_FORWARD, null);
            return true;
        });


        //lock screen backgrounds are not supported from Android 11 on
        findPreference(Prefs.PREF_LOCKSCREEN_BACKGROUND)
                .setVisible(BuildConfig.DEBUG || !VersionUtils.hasR());

        updateAdapterColor();
        buildEnqueueLocationPreference();
    }

    void updateAdapterColor(){
        ArrayList nowPlayingScreenList = new ArrayList<NowPlayingScreen>() {{
            add(NowPlayingScreen.Normal);
            add(NowPlayingScreen.Adaptive);
            add(NowPlayingScreen.Circle);
        }};
        findPreference(Prefs.PREF_ADAPTIVE_COLOR_APP).setEnabled(nowPlayingScreenList.contains(Prefs.getNowPlayingScreen()));
    }

    private void buildEnqueueLocationPreference() {
        final Resources res = requireActivity().getResources();
        final Map<String, String> options = new ArrayMap<>();
        {
            String[] keys = res.getStringArray(R.array.enqueue_location_values);
            String[] values = res.getStringArray(R.array.enqueue_location_options);
            for (int i = 0; i < keys.length; i++) {
                options.put(keys[i], values[i]);
            }
        }

        ListPreference pref = requirePreference(Prefs.PREF_ENQUEUE_LOCATION);
        pref.setSummary(res.getString(R.string.pref_enqueue_location_sum, options.get(pref.getValue())));

        pref.setOnPreferenceChangeListener((preference, newValue) -> {
            if (!(newValue instanceof String)) {
                return false;
            }
            String newValStr = (String)newValue;
            pref.setSummary(res.getString(R.string.pref_enqueue_location_sum, options.get(newValStr)));
            return true;
        });
    }

    /**
     * Mirrors {@link #buildEnqueueLocationPreference()}: the list preference shows the chosen
     * entry in its summary, both on open and after every change.
     */
    private void buildAdSkipSensitivityPreference() {
        final Resources res = requireActivity().getResources();
        final Map<String, String> options = new ArrayMap<>();
        {
            String[] keys = res.getStringArray(R.array.ad_skip_sensitivity_values);
            String[] values = res.getStringArray(R.array.ad_skip_sensitivity_options);
            for (int i = 0; i < keys.length; i++) {
                options.put(keys[i], values[i]);
            }
        }

        ListPreference pref = requirePreference(Prefs.PREF_AD_SKIP_SENSITIVITY);
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

    @NonNull
    private <T extends Preference> T requirePreference(@NonNull CharSequence key) {
        // Possibly put it to a common method in abstract base class
        T result = findPreference(key);
        if (result == null) {
            throw new IllegalArgumentException("Preference with key '" + key + "' is not found");

        }
        return result;
    }

    private void buildSmartMarkAsPlayedPreference() {
        final Resources res = getActivity().getResources();

        ListPreference pref = findPreference(Prefs.PREF_SMART_MARK_AS_PLAYED_SECS);
        String[] values = res.getStringArray(R.array.smart_mark_as_played_values);
        String[] entries = new String[values.length];
        for (int x = 0; x < values.length; x++) {
            if(x == 0) {
                entries[x] = res.getString(R.string.pref_smart_mark_as_played_disabled);
            } else {
                int v = Integer.parseInt(values[x]);
                if(v < 60) {
                    entries[x] = res.getQuantityString(R.plurals.time_seconds_quantified, v, v);
                } else {
                    v /= 60;
                    entries[x] = res.getQuantityString(R.plurals.time_minutes_quantified, v, v);
                }
            }
        }
        pref.setEntries(entries);
    }

    @Override
    public void invalidateSettings() {

    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(Prefs.NOW_PLAYING_SCREEN_ID.equals(key)){
            updateAdapterColor();
        }
        if (Prefs.PREF_PLAYBACK_SKIP_SILENCE.equals(key)
                || Prefs.PREF_STEREO_TO_MONO.equals(key)
                || Prefs.PREF_AUDIO_LOUDNESS.equals(key)) {
            // The global default effects changed; a playing podcast without its own follows them.
            EventBus.getDefault().post(AudioEffectsChangedEvent.global());
        }
        if (Prefs.PREF_AD_SKIP_ENABLED.equals(key)
                || Prefs.PREF_AD_SKIP_SENSITIVITY.equals(key)
                || Prefs.PREF_AD_SKIP_ANALYZE_ON_DOWNLOAD.equals(key)
                || Prefs.PREF_AD_SKIP_SHOW_SNACKBAR.equals(key)) {
            // 0 means "the global settings changed", not one feed's switch
            EventBus.getDefault().post(new AdSkipChangedEvent(0));
        }
    }
}
