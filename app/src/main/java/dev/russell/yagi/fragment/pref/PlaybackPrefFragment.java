package dev.russell.yagi.fragment.pref;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import dev.russell.yagi.R;
import dev.russell.yagi.activity.SettingsActivity;
import dev.russell.yagi.core.pref.Prefs;
import dev.russell.yagi.dialog.SkipPrefDialog;
import dev.russell.yagi.dialog.PlaySpeedDialog;
import dev.russell.yagi.event.settings.AudioEffectsChangedEvent;

import org.greenrobot.eventbus.EventBus;

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
        buildClearVoicePreference();
        buildVolumeLevelingPreference();
        buildVideoEpisodesPreference();
    }

    /** The summary spells out what the chosen default does. */
    private void buildVideoEpisodesPreference() {
        ListPreference pref = requirePreference(Prefs.PREF_VIDEO_EPISODES_AS);
        updateVideoEpisodesSummary(pref, Prefs.isPlayVideoAsVideo());
        pref.setOnPreferenceChangeListener((preference, newValue) -> {
            updateVideoEpisodesSummary(pref, !Prefs.VIDEO_EPISODES_AS_AUDIO.equals(newValue));
            return true;
        });
    }

    private static void updateVideoEpisodesSummary(ListPreference pref, boolean asVideo) {
        pref.setSummary(asVideo ? R.string.video_episodes_as_video_sum : R.string.video_episodes_as_audio_sum);
    }

    private void buildClearVoicePreference() {
        ListPreference pref = findPreference(Prefs.PREF_CLEAR_VOICE);
        if (pref == null) {
            return;
        }
        ClearVoicePreferences.updateSummary(pref, Prefs.getClearVoiceLevel());
        pref.setOnPreferenceChangeListener((preference, newValue) -> {
            int level = ClearVoicePreferences.parse(newValue);
            if (level < 0) {
                return false;
            }
            ClearVoicePreferences.updateSummary(pref, level);
            return true;
        });
    }

    private void buildVolumeLevelingPreference() {
        ListPreference pref = findPreference(Prefs.PREF_VOLUME_LEVELING);
        if (pref == null) {
            return;
        }
        VolumeLevelingPreferences.updateSummary(pref, Prefs.getVolumeLeveling());
        pref.setOnPreferenceChangeListener((preference, newValue) -> {
            int mode = VolumeLevelingPreferences.parse(newValue);
            if (mode < 0) {
                return false;
            }
            VolumeLevelingPreferences.updateSummary(pref, mode);
            return true;
        });
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

        buildEnqueueLocationPreference();
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
        if (Prefs.PREF_PLAYBACK_SKIP_SILENCE.equals(key)
                || Prefs.PREF_STEREO_TO_MONO.equals(key)
                || Prefs.PREF_CLEAR_VOICE.equals(key)
                || Prefs.PREF_VOLUME_LEVELING.equals(key)) {
            // The global default effects changed; a playing podcast without its own follows them.
            EventBus.getDefault().post(AudioEffectsChangedEvent.global());
        }
    }
}
