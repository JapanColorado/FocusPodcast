package dev.russell.focuspodcast.event;

/**
 * Posted after the bookmarks of an episode changed (one was added, its note edited, or it was
 * deleted or restored), so that the player's seek bar and any open bookmark list can reload.
 */
public class BookmarksChangedEvent {
    private final long feedItemId;

    public BookmarksChangedEvent(long feedItemId) {
        this.feedItemId = feedItemId;
    }

    /** The episode whose bookmarks changed. */
    public long getFeedItemId() {
        return feedItemId;
    }
}
