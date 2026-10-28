package dev.russell.focuspodcast.fragment.pref;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import org.greenrobot.eventbus.EventBus;

import dev.russell.focuspodcast.common.common.prefs.supportv7.ATESwitchPreference;
import dev.russell.focuspodcast.R;
import dev.russell.focuspodcast.core.feed.util.AudioEffectUtils;
import dev.russell.focuspodcast.event.playback.TitleChangeEvent;
import dev.russell.focuspodcast.model.feed.Feed;
import dev.russell.focuspodcast.model.feed.FeedPreferences;

/**
 * A podcast's Audio effects screen: Voice, Volume leveling, Skip silence and Stereo to mono, the
 * same controls in the same order as Settings and the player's Audio Effects dialog. The controls
 * always show the values in effect. The podcast follows the global defaults until one of them is changed here (or
 * in the player): the first change copies the defaults into the podcast before applying itself,
 * exactly like the dialog. While the podcast has its own effects, "Reset to default" makes it
 * follow the defaults again and "Set as default" makes its effects the defaults first.
 */
public class AudioEffectFragment extends AbsSettingsFragment {
    private static final String PREF_FEED_SKIP_SILENCE = "feed_skip_silence";
    private static final String PREF_FEED_MONO = "feed_mono_pref";
    private static final String PREF_FEED_CLEAR_VOICE = "feed_clear_voice_pref";
    private static final String PREF_FEED_VOLUME_LEVELING = "feed_volume_leveling_pref";
    private static final String PREF_RESET_TO_DEFAULT = "feed_effects_reset_to_default";
    private static final String PREF_SET_AS_DEFAULT = "feed_effects_set_as_default";
    private FeedPreferences feedPreferences;
    private Feed feed;

    public AudioEffectFragment(FeedPreferences feedPreferences, Feed feed) {
        this.feedPreferences = feedPreferences;
        this.feed = feed;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // The switches show one feed's stored values, so they must not persist to (or restore
        // from) the app-wide SharedPreferences, whose keys every feed would share.
        getPreferenceManager().setPreferenceDataStore(new DiscardingPreferenceDataStore());
        addPreferencesFromResource(R.xml.pref_audio_effect);
        init();
    }

    @Override
    public void onStart() {
        super.onStart();
        setupAudioEffectPreference();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        EventBus.getDefault().post(new TitleChangeEvent(R.string.audio_effects));
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    ATESwitchPreference skipSilencePreference;
    ATESwitchPreference monoPreference;
    ListPreference clearVoicePreference;
    ListPreference volumeLevelingPreference;
    Preference resetToDefaultPreference;
    Preference setAsDefaultPreference;

    private void init() {
        skipSilencePreference = findPreference(PREF_FEED_SKIP_SILENCE);
        monoPreference = findPreference(PREF_FEED_MONO);
        clearVoicePreference = findPreference(PREF_FEED_CLEAR_VOICE);
        volumeLevelingPreference = findPreference(PREF_FEED_VOLUME_LEVELING);
        resetToDefaultPreference = findPreference(PREF_RESET_TO_DEFAULT);
        setAsDefaultPreference = findPreference(PREF_SET_AS_DEFAULT);
    }

    /** Every change is stored for the podcast and applied live if it is playing. */
    private void setupAudioEffectPreference() {
        clearVoicePreference.setOnPreferenceChangeListener((preference, newValue) -> {
            int level = ClearVoicePreferences.parse(newValue);
            if (level < 0) {
                return false;
            }
            changeEffect(() -> feedPreferences.setClearVoiceLevel(level));
            ClearVoicePreferences.updateSummary(clearVoicePreference, level);
            return true;
        });

        volumeLevelingPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            int mode = VolumeLevelingPreferences.parse(newValue);
            if (mode < 0) {
                return false;
            }
            changeEffect(() -> feedPreferences.setVolumeLeveling(mode));
            VolumeLevelingPreferences.updateSummary(volumeLevelingPreference, mode);
            return true;
        });

        skipSilencePreference.setOnPreferenceChangeListener((preference, newValue) -> {
            changeEffect(() -> feedPreferences.setSkipSilence((Boolean) newValue));
            return true;
        });

        monoPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            changeEffect(() -> feedPreferences.setMono((Boolean) newValue));
            return true;
        });

        resetToDefaultPreference.setOnPreferenceClickListener(preference -> {
            feedPreferences.setUseFeedEffect(false);
            AudioEffectUtils.saveFeedEffects(feed.getId(), feedPreferences);
            showEffectValues();
            return true;
        });

        setAsDefaultPreference.setOnPreferenceClickListener(preference -> {
            AudioEffectUtils.makeDefault(feed.getId(), feedPreferences);
            Toast.makeText(requireContext(), R.string.default_updated, Toast.LENGTH_SHORT).show();
            showEffectValues();
            return true;
        });
        showEffectValues();
    }

    /**
     * Applies one change for this podcast, copying the current defaults into it first if it
     * followed them, like the player dialog does.
     */
    private void changeEffect(Runnable change) {
        AudioEffectUtils.customize(feedPreferences);
        change.run();
        AudioEffectUtils.saveFeedEffects(feed.getId(), feedPreferences);
        updateDefaultRows();
    }

    /** Shows the values in effect: the podcast's own, or the global defaults it follows. */
    private void showEffectValues() {
        skipSilencePreference.setChecked(AudioEffectUtils.isSkipSilence(feedPreferences));
        monoPreference.setChecked(AudioEffectUtils.isMono(feedPreferences));
        ClearVoicePreferences.show(clearVoicePreference, AudioEffectUtils.getClearVoiceLevel(feedPreferences));
        VolumeLevelingPreferences.show(volumeLevelingPreference, AudioEffectUtils.getVolumeLeveling(feedPreferences));
        updateDefaultRows();
    }

    private void updateDefaultRows() {
        boolean ownEffects = AudioEffectUtils.hasOwnEffects(feedPreferences);
        resetToDefaultPreference.setVisible(ownEffects);
        setAsDefaultPreference.setVisible(ownEffects);
    }

    @Override
    public void invalidateSettings() {

    }
}
