package allen.town.podcast.event.adskip;

/**
 * Posted by the UI when the user taps Undo on the "skipped ad" snackbar. The playback service
 * seeks back to {@link #getFromMs()} and stops skipping that segment for the rest of the current
 * playback so the user is not thrown forward again a second later.
 */
public class AdSkipUndoEvent {
    private final long feedItemId;
    private final long segmentId;
    private final long fromMs;

    public AdSkipUndoEvent(long feedItemId, long segmentId, long fromMs) {
        this.feedItemId = feedItemId;
        this.segmentId = segmentId;
        this.fromMs = fromMs;
    }

    public long getFeedItemId() {
        return feedItemId;
    }

    public long getSegmentId() {
        return segmentId;
    }

    public long getFromMs() {
        return fromMs;
    }
}
