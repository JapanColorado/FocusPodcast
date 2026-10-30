package dev.russell.yagi.model.feed;

import androidx.annotation.Nullable;

/**
 * How far the user got through one audiobook feed. Durations are in milliseconds and only count
 * tracks whose duration is known.
 */
public class AudiobookProgress {
    private final long feedId;
    private final int trackCount;
    private final int playedTrackCount;
    private final long totalDuration;
    private final long listenedDuration;
    @Nullable
    private final FeedItem resumeItem;
    private final long lastPlayedTime;

    /**
     * @param lastPlayedTime the most recent last-played time of any track, 0 if none was played
     */
    public AudiobookProgress(long feedId, int trackCount, int playedTrackCount, long totalDuration,
                             long listenedDuration, @Nullable FeedItem resumeItem, long lastPlayedTime) {
        this.feedId = feedId;
        this.trackCount = trackCount;
        this.playedTrackCount = playedTrackCount;
        this.totalDuration = totalDuration;
        this.listenedDuration = Math.min(listenedDuration, totalDuration);
        this.resumeItem = resumeItem;
        this.lastPlayedTime = lastPlayedTime;
    }

    public long getFeedId() {
        return feedId;
    }

    /** Number of tracks (items) of the book. */
    public int getTrackCount() {
        return trackCount;
    }

    /** Number of tracks marked played. */
    public int getPlayedTrackCount() {
        return playedTrackCount;
    }

    /** Sum of the durations of every track, in milliseconds. */
    public long getTotalDuration() {
        return totalDuration;
    }

    /**
     * Played tracks count with their full duration, the others with their saved position; in
     * milliseconds, never more than {@link #getTotalDuration()}.
     */
    public long getListenedDuration() {
        return listenedDuration;
    }

    /** Milliseconds left to listen. */
    public long getRemainingDuration() {
        return totalDuration - listenedDuration;
    }

    /** 0 to 100; 0 when the total duration is unknown. */
    public int getPercent() {
        if (totalDuration <= 0) {
            return 0;
        }
        return (int) Math.min(100, (listenedDuration * 100) / totalDuration);
    }

    /**
     * The track to continue with: the one in progress (the most recently played one if several are),
     * else the first unplayed track in track order. Null when every track is played or the book has
     * no tracks. Its media and feed are loaded.
     */
    @Nullable
    public FeedItem getResumeItem() {
        return resumeItem;
    }

    /** True when the book has tracks and all of them are played. */
    public boolean isFinished() {
        return trackCount > 0 && playedTrackCount == trackCount;
    }

    /** The most recent time any track of the book was played (epoch ms), 0 if never. */
    public long getLastPlayedTime() {
        return lastPlayedTime;
    }
}
