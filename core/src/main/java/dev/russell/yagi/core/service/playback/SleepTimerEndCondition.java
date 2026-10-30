package dev.russell.yagi.core.service.playback;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;

import dev.russell.yagi.event.playback.SleepTimerMode;
import dev.russell.yagi.model.feed.Chapter;

/**
 * Decides when a boundary sleep timer ({@link SleepTimerMode#END_OF_EPISODE} or
 * {@link SleepTimerMode#END_OF_CHAPTER}) stops playback. Pure logic, so it is tested on the JVM.
 * <p>
 * The target is a content position: the episode's duration, or the start of the chapter that
 * follows the one being played. It is fixed ("anchored") on the first update and kept while
 * playback runs on, so that a tick which lands a little past a chapter start still stops there.
 * It is re-anchored to the chapter that then contains the position whenever that position stops
 * following from the last one (a seek, a skipped intro or ad, a new episode), after
 * {@link #resetAnchor()} (playback resumed, media changed), and when the chapter list changes
 * (chapters load in the background). In the last chapter the target is the end of the episode.
 * <p>
 * Reaching the end of the episode is never reported as {@link #isDue()}: the episode completes
 * through the normal path (marked played, removed from the queue, auto-deleted) and the service
 * then declines to start the next one. Only a chapter boundary inside the episode is "due".
 */
public final class SleepTimerEndCondition {

    /** Returned by {@link #update} while the target position cannot be known. */
    public static final long UNKNOWN = -1;

    /**
     * A chapter boundary counts as reached this close before it, so that a precisely scheduled
     * check that fires a few milliseconds early still stops instead of rescheduling forever.
     */
    static final long DUE_TOLERANCE_MS = 150;

    /**
     * How far the position may run ahead of (or fall behind) what the elapsed time and speed
     * explain before it counts as a jump rather than playback.
     */
    static final long JUMP_SLACK_MS = 2000;

    private static final long UNSET = Long.MIN_VALUE;

    private final SleepTimerMode mode;

    private long targetMs = UNSET;
    private boolean targetIsEpisodeEnd;
    private long[] anchoredChapters;
    private long lastPositionMs = UNSET;
    private long lastNowMs;
    private long remainingMs = UNKNOWN;

    public SleepTimerEndCondition(SleepTimerMode mode) {
        if (mode == SleepTimerMode.DURATION) {
            throw new IllegalArgumentException("Duration timers are not boundary timers");
        }
        this.mode = mode;
    }

    public SleepTimerMode getMode() {
        return mode;
    }

    /** Forgets the target, so that the next {@link #update} anchors to the position it gets. */
    public void resetAnchor() {
        targetMs = UNSET;
        lastPositionMs = UNSET;
        anchoredChapters = null;
    }

    /**
     * Brings the condition up to date with the player.
     *
     * @param nowMs         a monotonic clock, used to tell playback from a jump
     * @param positionMs    the playback position, negative if unknown
     * @param durationMs    the episode's duration, zero or negative if unknown
     * @param speed         the playback speed
     * @param chapterStarts sorted chapter start positions (see {@link #chapterStarts}), or null
     * @return content milliseconds left until the target (never negative), or {@link #UNKNOWN}
     */
    public long update(long nowMs, long positionMs, long durationMs, float speed,
                       @Nullable long[] chapterStarts) {
        if (positionMs < 0) {
            remainingMs = UNKNOWN;
            return remainingMs;
        }
        long[] chapters = mode == SleepTimerMode.END_OF_CHAPTER && chapterStarts != null
                ? chapterStarts : new long[0];
        if (targetMs == UNSET
                || !Arrays.equals(chapters, anchoredChapters)
                || jumped(nowMs, positionMs, speed)) {
            anchor(positionMs, chapters);
        }
        lastPositionMs = positionMs;
        lastNowMs = nowMs;

        long target = targetIsEpisodeEnd ? durationMs : targetMs;
        if (targetIsEpisodeEnd && durationMs <= 0) {
            remainingMs = UNKNOWN;
        } else {
            remainingMs = Math.max(0, target - positionMs);
        }
        return remainingMs;
    }

    /** True once a chapter boundary inside the episode is reached: pause playback now. */
    public boolean isDue() {
        return !targetIsEpisodeEnd && targetMs != UNSET && remainingMs != UNKNOWN
                && remainingMs <= DUE_TOLERANCE_MS;
    }

    /** True if the current target is the end of the episode rather than a chapter boundary. */
    public boolean stopsAtEpisodeEnd() {
        return targetMs == UNSET || targetIsEpisodeEnd;
    }

    private boolean jumped(long nowMs, long positionMs, float speed) {
        if (lastPositionMs == UNSET) {
            return true;
        }
        long elapsed = Math.max(0, nowMs - lastNowMs);
        long expected = (long) (elapsed * Math.max(speed, 0f));
        long advanced = positionMs - lastPositionMs;
        return advanced < -JUMP_SLACK_MS || advanced > expected + JUMP_SLACK_MS;
    }

    private void anchor(long positionMs, long[] chapters) {
        anchoredChapters = chapters;
        targetIsEpisodeEnd = true;
        targetMs = 0;
        for (long start : chapters) {
            if (start > positionMs) {
                targetIsEpisodeEnd = false;
                targetMs = start;
                break;
            }
        }
    }

    /**
     * The chapter starts of {@code chapters} in ascending order, without starts at or before 0
     * (a chapter that starts at the beginning is no boundary) and without duplicates; null when
     * there are none.
     */
    @Nullable
    public static long[] chapterStarts(@Nullable List<Chapter> chapters) {
        if (chapters == null || chapters.isEmpty()) {
            return null;
        }
        long[] starts = new long[chapters.size()];
        int count = 0;
        for (Chapter chapter : chapters) {
            if (chapter != null && chapter.getStart() > 0) {
                starts[count++] = chapter.getStart();
            }
        }
        if (count == 0) {
            return null;
        }
        long[] sorted = Arrays.copyOf(starts, count);
        Arrays.sort(sorted);
        int unique = 1;
        for (int i = 1; i < sorted.length; i++) {
            if (sorted[i] != sorted[unique - 1]) {
                sorted[unique++] = sorted[i];
            }
        }
        return Arrays.copyOf(sorted, unique);
    }

    /** True if {@code chapters} has at least one boundary an end-of-chapter timer can stop at. */
    public static boolean hasChapterBoundaries(@Nullable List<Chapter> chapters) {
        return chapterStarts(chapters) != null;
    }

    /**
     * Wall-clock milliseconds it takes to play {@code contentMs} at {@code speed}, or
     * {@link #UNKNOWN} for an unknown content time.
     */
    public static long toWallClock(long contentMs, float speed) {
        if (contentMs == UNKNOWN) {
            return UNKNOWN;
        }
        if (speed <= 0f) {
            return contentMs;
        }
        return (long) Math.ceil(contentMs / (double) speed);
    }
}
