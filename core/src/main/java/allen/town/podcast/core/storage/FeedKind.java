package allen.town.podcast.core.storage;

import androidx.annotation.NonNull;

import allen.town.podcast.model.feed.Feed;

/**
 * Which subscriptions a feed list holds: podcasts and audiobooks are shown on separate pages.
 */
public enum FeedKind {
    /** Every subscribed feed. */
    ALL,
    /** Feeds that are not audiobooks (RSS podcasts and plain local folders). */
    PODCASTS,
    /** Feeds marked as audiobooks ({@link Feed#isAudiobook()}). */
    AUDIOBOOKS;

    /** Whether {@code feed} (with its preferences loaded) belongs to this kind. */
    public boolean matches(@NonNull Feed feed) {
        switch (this) {
            case PODCASTS:
                return !feed.isAudiobook();
            case AUDIOBOOKS:
                return feed.isAudiobook();
            case ALL:
            default:
                return true;
        }
    }
}
