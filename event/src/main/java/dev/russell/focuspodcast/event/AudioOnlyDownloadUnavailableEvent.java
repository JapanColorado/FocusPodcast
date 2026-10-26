package dev.russell.focuspodcast.event;

/**
 * Posted when the listener downloaded a video episode as audio only but its audio could not be
 * cut out of the file, and the whole file is too large (or of unknown size) to download without
 * asking. The UI offers to download the full video instead.
 */
public class AudioOnlyDownloadUnavailableEvent {
    private final long mediaId;
    private final long fullSize;

    public AudioOnlyDownloadUnavailableEvent(long mediaId, long fullSize) {
        this.mediaId = mediaId;
        this.fullSize = fullSize;
    }

    public long getMediaId() {
        return mediaId;
    }

    /** The size of the whole video file in bytes, or a value {@code <= 0} if unknown. */
    public long getFullSize() {
        return fullSize;
    }
}
