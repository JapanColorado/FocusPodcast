package allen.town.podcast.core.feed.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import allen.town.podcast.core.pref.Prefs;
import allen.town.podcast.core.storage.DBWriter;
import allen.town.podcast.event.settings.AdSkipChangedEvent;
import allen.town.podcast.model.feed.Feed;
import allen.town.podcast.model.feed.FeedItem;
import allen.town.podcast.model.feed.FeedMedia;
import allen.town.podcast.model.feed.FeedPreferences;
import allen.town.podcast.model.playback.Playable;

/**
 * Resolves whether ad detection and skipping is on for a feed, and changes it for one feed.
 *
 * <p>The global preference ({@link Prefs#isAdSkipEnabled()}) is only the default. A feed follows
 * it until the user makes a choice for that feed, which is stored as
 * {@link FeedPreferences#getAdSkipOverride()}; this class is the one place that combines the two,
 * so every gate (analysis, skipping, drawing segments) agrees.
 */
public final class AdSkipUtils {

    private AdSkipUtils() {
    }

    /** The feed's own choice if it has one, otherwise the global default. */
    public static boolean isAdSkipEnabled(@Nullable FeedPreferences preferences) {
        Boolean override = preferences != null ? preferences.getAdSkipOverride() : null;
        return override != null ? override : Prefs.isAdSkipEnabled();
    }

    /**
     * Whether ads are detected and skipped for this media. Anything that is not an episode of a
     * feed with loaded preferences follows the global default.
     */
    public static boolean isAdSkipEnabled(@Nullable Playable media) {
        return isAdSkipEnabled(preferencesOf(media));
    }

    /**
     * Stores an explicit choice for one feed, even when it equals the current default: the user
     * picked it for this feed, so a later change of the default must not move it. Posts
     * {@link AdSkipChangedEvent} once the change is in the database.
     */
    public static void setAdSkipForFeed(@NonNull Feed feed, boolean enabled) {
        store(feed, enabled);
    }

    /** Makes the feed follow the global default again. Posts {@link AdSkipChangedEvent}. */
    public static void resetAdSkipForFeed(@NonNull Feed feed) {
        store(feed, null);
    }

    private static void store(@NonNull Feed feed, @Nullable Boolean override) {
        FeedPreferences preferences = feed.getPreferences();
        if (preferences == null) {
            return;
        }
        preferences.setAdSkipOverride(override);
        // Only this field is written: the caller's object may be older than the stored row.
        DBWriter.updateFeedPreferences(feed.getId(), stored -> stored.setAdSkipOverride(override),
                new AdSkipChangedEvent(feed.getId()));
    }

    @Nullable
    private static FeedPreferences preferencesOf(@Nullable Playable media) {
        if (!(media instanceof FeedMedia)) {
            return null;
        }
        FeedItem item = ((FeedMedia) media).getItem();
        Feed feed = item != null ? item.getFeed() : null;
        return feed != null ? feed.getPreferences() : null;
    }
}
