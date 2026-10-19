package dev.russell.focuspodcast.core.feed.util;

import static dev.russell.focuspodcast.model.feed.FeedPreferences.SPEED_USE_GLOBAL;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;

import dev.russell.focuspodcast.core.pref.Prefs;
import dev.russell.focuspodcast.core.storage.DBWriter;
import dev.russell.focuspodcast.event.settings.SpeedPresetChangedEvent;
import dev.russell.focuspodcast.model.feed.FeedPreferences;
import dev.russell.focuspodcast.model.playback.MediaType;
import dev.russell.focuspodcast.model.playback.Playable;

/**
 * Resolves and stores playback speeds. A subscribed podcast follows the global default speed
 * from Settings until the speed is changed for it (in the player or in its Feed Settings), which
 * gives that podcast its own speed ({@link FeedPreferences#getFeedPlaybackSpeed()});
 * {@link FeedPreferences#SPEED_USE_GLOBAL} ("Reset to default") makes it follow the default again.
 */
public final class PlaybackSpeedUtils {

    private PlaybackSpeedUtils() {
    }

    /**
     * Returns the speed {@code media} plays at: its podcast's own speed, or the global default
     * for its media type.
     */
    public static float getCurrentPlaybackSpeed(@Nullable Playable media) {
        MediaType mediaType = media != null ? media.getMediaType() : null;
        return resolveSpeed(PlayableFeedPreferences.of(media), Prefs.getPlaybackSpeed(mediaType));
    }

    /** The podcast's own speed when it has one, otherwise {@code globalSpeed}. */
    public static float resolveSpeed(@Nullable FeedPreferences preferences, float globalSpeed) {
        return hasOwnSpeed(preferences) ? preferences.getFeedPlaybackSpeed() : globalSpeed;
    }

    /** True when the podcast has its own speed instead of following the global default. */
    public static boolean hasOwnSpeed(@Nullable FeedPreferences preferences) {
        // Anything that is not a positive speed (SPEED_USE_GLOBAL in practice) means "default".
        return preferences != null && preferences.getFeedPlaybackSpeed() > 0;
    }

    /**
     * Remembers a speed the user chose in the player for {@code media}. For an episode of a
     * subscribed podcast the speed becomes that podcast's own speed: the given in-memory
     * preferences are updated and the value is written to the database. Any other media changes
     * the global default for its media type instead.
     *
     * @param speed the new speed, or {@link FeedPreferences#SPEED_USE_GLOBAL} to make the
     *              podcast follow the default again (a no-op for media without a podcast)
     * @return the id of the feed whose own speed changed, or 0 when a global default was written
     *         or nothing changed
     */
    public static long rememberSpeed(@Nullable Playable media, float speed) {
        FeedPreferences preferences = PlayableFeedPreferences.of(media);
        if (preferences != null) {
            long feedId = PlayableFeedPreferences.feedIdOf(media);
            preferences.setFeedPlaybackSpeed(speed);
            DBWriter.updateFeedPreferences(feedId, stored -> stored.setFeedPlaybackSpeed(speed));
            return feedId;
        }
        if (speed == SPEED_USE_GLOBAL) {
            return 0;
        }
        if (media != null && media.getMediaType() == MediaType.VIDEO) {
            Prefs.setVideoPlaybackSpeed(speed);
        } else {
            Prefs.setPlaybackSpeed(speed);
        }
        return 0;
    }

    /**
     * Changes the global default speed for {@code mediaType} (audio when null) and tells the
     * playback service, so a playing podcast that follows the default picks it up.
     */
    public static void setDefaultSpeed(float speed, @Nullable MediaType mediaType) {
        if (mediaType == MediaType.VIDEO) {
            Prefs.setVideoPlaybackSpeed(speed);
        } else {
            Prefs.setPlaybackSpeed(speed);
        }
        EventBus.getDefault().post(new SpeedPresetChangedEvent(SPEED_USE_GLOBAL, 0));
    }

    /**
     * Stores a podcast's own speed, or {@link FeedPreferences#SPEED_USE_GLOBAL} to make it follow
     * the default, from a screen other than the player: updates {@code preferences}, writes only
     * the speed column and tells the playback service.
     */
    public static void setFeedSpeed(long feedId, @NonNull FeedPreferences preferences, float speed) {
        preferences.setFeedPlaybackSpeed(speed);
        // only the speed column: the player may have changed other fields since the caller loaded
        DBWriter.updateFeedPreferences(feedId, stored -> stored.setFeedPlaybackSpeed(speed));
        EventBus.getDefault().post(new SpeedPresetChangedEvent(speed, feedId));
    }

    /**
     * "Set as default" outside the player: makes the podcast's own speed the global default for
     * audio and then makes the podcast follow the default again. Does nothing when the podcast
     * has no speed of its own.
     */
    public static void makeDefault(long feedId, @NonNull FeedPreferences preferences) {
        if (!hasOwnSpeed(preferences)) {
            return;
        }
        setDefaultSpeed(preferences.getFeedPlaybackSpeed(), MediaType.AUDIO);
        setFeedSpeed(feedId, preferences, SPEED_USE_GLOBAL);
    }
}
