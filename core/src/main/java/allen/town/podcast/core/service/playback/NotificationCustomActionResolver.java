package allen.town.podcast.core.service.playback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import allen.town.podcast.event.playback.SleepTimerMode;

/**
 * Pure logic behind the custom notification / media session buttons, tested on the JVM: which of
 * the selected buttons apply in a given playback state, where "next chapter" seeks to, and which
 * speed "cycle speed" steps to.
 */
public final class NotificationCustomActionResolver {

    /**
     * A chapter start this close after the position does not count as "next": after seeking to a
     * chapter's start the player may report a position a little before it, and the button must
     * then go on to the following chapter rather than seemingly do nothing.
     */
    static final long NEXT_CHAPTER_TOLERANCE_MS = 500;

    /** Two speeds closer than this are the same preset (presets are stored with one decimal). */
    static final float SPEED_EPSILON = 0.01f;

    /** Returned by {@link #nextChapterStart} when there is no next chapter. */
    public static final long NO_CHAPTER = -1;

    private NotificationCustomActionResolver() {
    }

    /** What the resolver needs to know about playback. */
    public static final class State {
        final boolean hasNextChapter;
        final boolean inAdSegment;
        @Nullable
        final SleepTimerMode sleepTimerMode;
        final boolean hasSpeedPresets;

        /**
         * @param hasNextChapter  a chapter starts after the current position
         * @param inAdSegment     playback is inside an enabled ad segment that can be skipped
         * @param sleepTimerMode  the running sleep timer's mode, or null when none runs
         * @param hasSpeedPresets the user has at least one speed preset
         */
        public State(boolean hasNextChapter, boolean inAdSegment,
                     @Nullable SleepTimerMode sleepTimerMode, boolean hasSpeedPresets) {
            this.hasNextChapter = hasNextChapter;
            this.inAdSegment = inAdSegment;
            this.sleepTimerMode = sleepTimerMode;
            this.hasSpeedPresets = hasSpeedPresets;
        }
    }

    /**
     * The buttons to show, in the user's order: the selected ones that apply in {@code state}.
     * "Sleep timer +15 min" is hidden while an end-of-episode or end-of-chapter timer runs,
     * because those cannot be extended and silently replacing them would change what the user
     * asked for.
     */
    @NonNull
    public static List<NotificationCustomAction> resolve(
            @NonNull List<NotificationCustomAction> selected, @NonNull State state) {
        if (selected.isEmpty()) {
            return Collections.emptyList();
        }
        List<NotificationCustomAction> visible = new ArrayList<>(selected.size());
        for (NotificationCustomAction action : selected) {
            if (isApplicable(action, state) && !visible.contains(action)) {
                visible.add(action);
            }
            if (visible.size() == NotificationCustomAction.MAX_SELECTED) {
                break;
            }
        }
        return visible;
    }

    static boolean isApplicable(@NonNull NotificationCustomAction action, @NonNull State state) {
        switch (action) {
            case NEXT_CHAPTER:
                return state.hasNextChapter;
            case SKIP_AD:
                return state.inAdSegment;
            case SLEEP_TIMER_EXTEND:
                return state.sleepTimerMode == null || state.sleepTimerMode == SleepTimerMode.DURATION;
            case CYCLE_SPEED:
                return state.hasSpeedPresets;
            default:
                return false;
        }
    }

    /**
     * Where "next chapter" seeks to.
     *
     * @param chapterStarts sorted chapter starts as made by
     *                      {@link SleepTimerEndCondition#chapterStarts}, or null
     * @param positionMs    the playback position; negative when unknown
     * @return the start of the first chapter after the position, or {@link #NO_CHAPTER}
     */
    public static long nextChapterStart(@Nullable long[] chapterStarts, long positionMs) {
        if (chapterStarts == null || positionMs < 0) {
            return NO_CHAPTER;
        }
        for (long start : chapterStarts) {
            if (start > positionMs + NEXT_CHAPTER_TOLERANCE_MS) {
                return start;
            }
        }
        return NO_CHAPTER;
    }

    /**
     * The speed "cycle speed" switches to: the lowest preset above {@code current}, wrapping to
     * the lowest preset after the highest. A speed that is not a preset steps to the next higher
     * one. Without presets the current speed is kept.
     */
    public static float nextSpeed(@Nullable List<Float> presets, float current) {
        if (presets == null || presets.isEmpty()) {
            return current;
        }
        float lowest = Float.MAX_VALUE;
        float nextHigher = Float.MAX_VALUE;
        for (Float preset : presets) {
            if (preset == null || preset <= 0) {
                continue;
            }
            lowest = Math.min(lowest, preset);
            if (preset > current + SPEED_EPSILON) {
                nextHigher = Math.min(nextHigher, preset);
            }
        }
        if (nextHigher != Float.MAX_VALUE) {
            return nextHigher;
        }
        return lowest != Float.MAX_VALUE ? lowest : current;
    }
}
