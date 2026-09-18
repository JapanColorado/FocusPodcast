package allen.town.podcast.event.adskip;

/**
 * Posted by the playback service right after it seeked past an ad segment. The UI shows a
 * snackbar with an Undo action that seeks back to {@link #getFromMs()} and asks the service not
 * to skip the same segment again during this playback.
 */
public class AdSkippedEvent {
    private final long feedItemId;
    private final long segmentId;
    private final long fromMs;
    private final long toMs;

    public AdSkippedEvent(long feedItemId, long segmentId, long fromMs, long toMs) {
        this.feedItemId = feedItemId;
        this.segmentId = segmentId;
        this.fromMs = fromMs;
        this.toMs = toMs;
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

    public long getToMs() {
        return toMs;
    }

    public long getSkippedMs() {
        return toMs - fromMs;
    }
}
