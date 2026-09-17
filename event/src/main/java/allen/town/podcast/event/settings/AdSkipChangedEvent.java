package allen.town.podcast.event.settings;

/**
 * Posted when the global ad-skip preferences or a feed's ad-skip switch change, so the playback
 * service re-evaluates whether to skip on the currently playing episode.
 */
public class AdSkipChangedEvent {
    /** 0 when the global preference changed rather than a single feed's. */
    private final long feedId;

    public AdSkipChangedEvent(long feedId) {
        this.feedId = feedId;
    }

    public long getFeedId() {
        return feedId;
    }
}
