package allen.town.podcast.fragment.pref;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;

import allen.town.podcast.common.common.prefs.supportv7.ATESwitchPreference;
import allen.town.podcast.R;
import allen.town.podcast.core.feed.util.AudioEffectUtils;
import allen.town.podcast.event.playback.TitleChangeEvent;
import allen.town.podcast.model.feed.Feed;
import allen.town.podcast.model.feed.FeedPreferences;

public class AudioEffectFragment extends AbsSettingsFragment {
    private static final String PREF_FEED_AUDIO_EFFECT = "feed_audio_effect_pref";
    private static final String PREF_FEED_SKIP_SILENCE = "feed_skip_silence";
    private static final String PREF_FEED_MONO = "feed_mono_pref";
    private static final String PREF_FEED_LOUDNESS = "feed_loudness_pref";
    private FeedPreferences feedPreferences;
    private Feed feed;

    public AudioEffectFragment(FeedPreferences feedPreferences, Feed feed) {
        this.feedPreferences = feedPreferences;
        this.feed = feed;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
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

    ATESwitchPreference audioEffectPreference;
    ATESwitchPreference skipSilencePreference;
    ATESwitchPreference monoPreference;
    ATESwitchPreference loudnessPreference;

    private void init() {
        audioEffectPreference = findPreference(PREF_FEED_AUDIO_EFFECT);
        skipSilencePreference = findPreference(PREF_FEED_SKIP_SILENCE);
        monoPreference = findPreference(PREF_FEED_MONO);
        loudnessPreference = findPreference(PREF_FEED_LOUDNESS);
    }

    /**
     * The master switch is "customize for this podcast": off, the podcast follows the global
     * defaults from Settings (shown read-only below); on, it has its own values, which start as a
     * copy of the defaults. Every change is stored and applied live if the podcast is playing.
     */
    private void setupAudioEffectPreference() {
        audioEffectPreference.setChecked(feedPreferences.isUseFeedEffect());
        audioEffectPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            if ((Boolean) newValue) {
                AudioEffectUtils.customize(feedPreferences);
            } else {
                feedPreferences.setUseFeedEffect(false);
            }
            saveEffects();
            showEffectValues();
            return true;
        });

        skipSilencePreference.setOnPreferenceChangeListener((preference, newValue) -> {
            feedPreferences.setSkipSilence((Boolean) newValue);
            saveEffects();
            return true;
        });

        monoPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            feedPreferences.setMono((Boolean) newValue);
            saveEffects();
            return true;
        });

        loudnessPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            feedPreferences.setLoudness((Boolean) newValue);
            saveEffects();
            return true;
        });
        showEffectValues();
    }

    private void saveEffects() {
        AudioEffectUtils.saveFeedEffects(feed.getId(), feedPreferences);
    }

    /** Shows the values in effect: the podcast's own, or the global defaults it follows. */
    private void showEffectValues() {
        skipSilencePreference.setChecked(AudioEffectUtils.isSkipSilence(feedPreferences));
        monoPreference.setChecked(AudioEffectUtils.isMono(feedPreferences));
        loudnessPreference.setChecked(AudioEffectUtils.isLoudness(feedPreferences));
    }

    @Override
    public void invalidateSettings() {

    }
}
