package allen.town.podcast.core.feed.util;

import androidx.annotation.Nullable;

import allen.town.podcast.model.feed.Feed;
import allen.town.podcast.model.feed.FeedItem;
import allen.town.podcast.model.feed.FeedMedia;
import allen.town.podcast.model.feed.FeedPreferences;
import allen.town.podcast.model.playback.Playable;

/**
 * Finds the per-podcast preferences that apply to a playable. Playback speed and audio effects
 * can be set per podcast only for subscribed feeds: an episode of a feed that is merely being
 * previewed, or media that is not a feed episode at all, always uses (and, when changed in the
 * player, writes) the global defaults from Settings.
 */
public final class PlayableFeedPreferences {

    private PlayableFeedPreferences() {
    }

    /** The feed of {@code media}, or null when it is not a feed episode or its feed is not loaded. */
    @Nullable
    public static Feed feedOf(@Nullable Playable media) {
        if (!(media instanceof FeedMedia)) {
            return null;
        }
        FeedItem item = ((FeedMedia) media).getItem();
        return item != null ? item.getFeed() : null;
    }

    /**
     * The preferences whose speed and effect settings apply to {@code media}, or null when the
     * global defaults apply because it has no subscribed feed.
     */
    @Nullable
    public static FeedPreferences of(@Nullable Playable media) {
        return of(feedOf(media));
    }

    /** Like {@link #of(Playable)} for a feed that is already at hand. */
    @Nullable
    public static FeedPreferences of(@Nullable Feed feed) {
        if (feed == null || !feed.isSubscribed()) {
            return null;
        }
        return feed.getPreferences();
    }

    /** The id of the feed of {@code media}, or 0 when it has none. */
    public static long feedIdOf(@Nullable Playable media) {
        Feed feed = feedOf(media);
        return feed != null ? feed.getId() : 0;
    }
}
