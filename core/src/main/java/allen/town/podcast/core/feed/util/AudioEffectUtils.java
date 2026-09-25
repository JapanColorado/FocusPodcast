package allen.town.podcast.core.feed.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;

import allen.town.podcast.core.pref.Prefs;
import allen.town.podcast.core.storage.DBWriter;
import allen.town.podcast.event.settings.AudioEffectsChangedEvent;
import allen.town.podcast.model.feed.FeedPreferences;
import allen.town.podcast.model.playback.Playable;

/**
 * Resolves and stores the audio effects (skip silence, stereo to mono, vocal enhancement). A
 * subscribed podcast follows the global defaults from Settings until an effect is changed in the
 * player (or its own effects are switched on in Feed Settings), which gives it its own copy of
 * all three ({@link FeedPreferences#isUseFeedEffect()}).
 */
public final class AudioEffectUtils {

    private AudioEffectUtils() {
    }

    /** Whether silence is skipped for {@code media}. */
    public static boolean isSkipEnable(@Nullable Playable media) {
        return isSkipSilence(PlayableFeedPreferences.of(media));
    }

    /** Whether stereo is downmixed to mono for {@code media}. */
    public static boolean isMonoEnable(@Nullable Playable media) {
        return isMono(PlayableFeedPreferences.of(media));
    }

    /** Whether vocal enhancement (loudness) is on for {@code media}. */
    public static boolean isLoudnessEnable(@Nullable Playable media) {
        return isLoudness(PlayableFeedPreferences.of(media));
    }

    /** True when the podcast has its own effects instead of following the global defaults. */
    public static boolean hasOwnEffects(@Nullable FeedPreferences preferences) {
        return preferences != null && preferences.isUseFeedEffect();
    }

    public static boolean isSkipSilence(@Nullable FeedPreferences preferences) {
        return hasOwnEffects(preferences) ? preferences.isSkipSilence() : Prefs.isSkipSilence();
    }

    public static boolean isMono(@Nullable FeedPreferences preferences) {
        return hasOwnEffects(preferences) ? preferences.isMono() : Prefs.stereoToMono();
    }

    public static boolean isLoudness(@Nullable FeedPreferences preferences) {
        return hasOwnEffects(preferences) ? preferences.isLoudness() : Prefs.audioLoudness();
    }

    /**
     * Gives the podcast its own effects, starting from the values currently in effect (the
     * global defaults), so switching to per-podcast effects changes nothing audible by itself.
     * Does nothing when the podcast already has its own effects. Only changes {@code preferences};
     * call {@link #saveFeedEffects} to persist and apply.
     */
    public static void customize(@NonNull FeedPreferences preferences) {
        if (preferences.isUseFeedEffect()) {
            return;
        }
        preferences.setSkipSilence(Prefs.isSkipSilence());
        preferences.setMono(Prefs.stereoToMono());
        preferences.setLoudness(Prefs.audioLoudness());
        preferences.setUseFeedEffect(true);
    }

    /**
     * Writes the effect fields of {@code preferences} to the database (only those fields, so a
     * stale copy does not overwrite other settings) and tells the playback service, which applies
     * them if that podcast is playing.
     */
    public static void saveFeedEffects(long feedId, @NonNull FeedPreferences preferences) {
        final boolean useFeedEffect = preferences.isUseFeedEffect();
        final boolean skipSilence = preferences.isSkipSilence();
        final boolean mono = preferences.isMono();
        final boolean loudness = preferences.isLoudness();
        DBWriter.updateFeedPreferences(feedId, stored -> {
            stored.setUseFeedEffect(useFeedEffect);
            stored.setSkipSilence(skipSilence);
            stored.setMono(mono);
            stored.setLoudness(loudness);
        });
        EventBus.getDefault().post(AudioEffectsChangedEvent.forFeed(
                feedId, useFeedEffect, skipSilence, mono, loudness));
    }
}
