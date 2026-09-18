package allen.town.podcast.event.adskip;

/**
 * Posted after the stored ad segments of an episode changed (analysis finished, a segment was
 * marked by hand, toggled or deleted), so that the player and any open list can reload them.
 */
public class AdSegmentsChangedEvent {
    private final long feedItemId;

    public AdSegmentsChangedEvent(long feedItemId) {
        this.feedItemId = feedItemId;
    }

    public long getFeedItemId() {
        return feedItemId;
    }
}
