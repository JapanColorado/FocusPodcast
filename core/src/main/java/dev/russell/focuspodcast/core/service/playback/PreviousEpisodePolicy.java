package dev.russell.focuspodcast.core.service.playback;

import androidx.annotation.Nullable;

import java.util.List;

import dev.russell.focuspodcast.model.feed.FeedItem;
import dev.russell.focuspodcast.model.feed.FeedMedia;

/**
 * What "previous" does, music-player style: past the first few seconds it restarts the current
 * episode; within them it goes back to the most recently played other episode, or restarts the
 * current one when the history has none. Pure logic, so it is tested on the JVM.
 */
public final class PreviousEpisodePolicy {

    /** Up to this position, "previous" means the episode before; after it, "start over". */
    public static final long RESTART_THRESHOLD_MS = 5000;

    /**
     * How many history entries to read: the current episode may itself be the newest entry,
     * so the one before it is at most second.
     */
    public static final int HISTORY_LOOKUP_LIMIT = 2;

    public enum Action {
        /** Seek the current episode back to its start. */
        RESTART,
        /** Start playing the previously played episode. */
        PLAY_PREVIOUS
    }

    private PreviousEpisodePolicy() {
    }

    /** True if the position alone already decides on a restart, so the history need not be read. */
    public static boolean isPastRestartThreshold(long positionMs) {
        return positionMs > RESTART_THRESHOLD_MS;
    }

    public static Action decide(long positionMs, boolean hasPreviousEpisode) {
        if (isPastRestartThreshold(positionMs) || !hasPreviousEpisode) {
            return Action.RESTART;
        }
        return Action.PLAY_PREVIOUS;
    }

    /**
     * The newest history entry that is not the episode playing now.
     *
     * @param history        playback history, newest first
     * @param currentMediaId media id of the playing episode, or 0 when it is not a feed episode
     */
    @Nullable
    public static FeedMedia pickPrevious(List<FeedItem> history, long currentMediaId) {
        for (FeedItem item : history) {
            FeedMedia media = item.getMedia();
            if (media != null && media.getId() != currentMediaId) {
                return media;
            }
        }
        return null;
    }
}
