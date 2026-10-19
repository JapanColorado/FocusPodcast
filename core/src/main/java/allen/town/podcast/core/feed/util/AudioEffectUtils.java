package allen.town.podcast.core.feed.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;

import allen.town.podcast.core.pref.Prefs;
import allen.town.podcast.core.storage.DBWriter;
import allen.town.podcast.event.settings.AudioEffectsChangedEvent;
import allen.town.podcast.model.feed.FeedPreferences;
import allen.town.podcast.model.playback.ClearVoiceLevel;
import allen.town.podcast.model.playback.Playable;

/**
 * Resolves and stores the audio effects (Voice mode, skip silence, stereo to mono). A subscribed
 * podcast follows the global defaults from Settings until one of them is changed for it, in the
 * player or on its Audio effects screen, which gives it its own copy of all of them
 * ({@link FeedPreferences#isUseFeedEffect()}) until "Reset to default".
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

    /** The Voice mode ({@link ClearVoiceLevel}) for {@code media}. */
    public static int getClearVoiceLevel(@Nullable Playable media) {
        return getClearVoiceLevel(PlayableFeedPreferences.of(media));
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

    public static int getClearVoiceLevel(@Nullable FeedPreferences preferences) {
        return hasOwnEffects(preferences) ? preferences.getClearVoiceLevel() : Prefs.getClearVoiceLevel();
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
        preferences.setClearVoiceLevel(Prefs.getClearVoiceLevel());
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
        final int clearVoiceLevel = preferences.getClearVoiceLevel();
        DBWriter.updateFeedPreferences(feedId, stored -> {
            stored.setUseFeedEffect(useFeedEffect);
            stored.setSkipSilence(skipSilence);
            stored.setMono(mono);
            stored.setClearVoiceLevel(clearVoiceLevel);
        });
        EventBus.getDefault().post(AudioEffectsChangedEvent.forFeed(
                feedId, useFeedEffect, skipSilence, mono, clearVoiceLevel));
    }

    /**
     * "Set as default": makes the podcast's own effects the global defaults, tells the playback
     * service that the defaults changed, and then makes the podcast follow them again. Nothing is
     * audible, since the podcast's values are now the defaults; podcasts with their own effects
     * keep them. Does nothing when the podcast has no effects of its own.
     */
    public static void makeDefault(long feedId, @NonNull FeedPreferences preferences) {
        if (!preferences.isUseFeedEffect()) {
            return;
        }
        Prefs.setSkipSilence(preferences.isSkipSilence());
        Prefs.stereoToMono(preferences.isMono());
        Prefs.setClearVoiceLevel(preferences.getClearVoiceLevel());
        EventBus.getDefault().post(AudioEffectsChangedEvent.global());
        preferences.setUseFeedEffect(false);
        saveFeedEffects(feedId, preferences);
    }
}
