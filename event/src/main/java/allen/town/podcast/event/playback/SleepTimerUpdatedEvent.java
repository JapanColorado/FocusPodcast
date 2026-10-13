package allen.town.podcast.event.playback;

/**
 * Progress of the sleep timer. {@link #getTimeLeft()} is wall-clock milliseconds until playback
 * stops; for the boundary modes ({@link SleepTimerMode#END_OF_EPISODE},
 * {@link SleepTimerMode#END_OF_CHAPTER}) it is the content time to the boundary divided by the
 * playback speed, and it may be unknown (see {@link #isTimeLeftKnown()}) when the episode's
 * duration is not known yet.
 */
public class SleepTimerUpdatedEvent {
    /** {@link #getTimeLeft()} of a boundary timer whose remaining time cannot be computed. */
    public static final long TIME_LEFT_UNKNOWN = -1;
    private static final long CANCELLED = Long.MAX_VALUE;

    private enum Kind { ENABLED, UPDATED, CANCELLED }

    private final Kind kind;
    private final long timeLeft;
    private final SleepTimerMode mode;

    private SleepTimerUpdatedEvent(Kind kind, long timeLeft, SleepTimerMode mode) {
        this.kind = kind;
        this.timeLeft = timeLeft;
        this.mode = mode;
    }

    public static SleepTimerUpdatedEvent justEnabled(long timeLeft) {
        return justEnabled(timeLeft, SleepTimerMode.DURATION);
    }

    public static SleepTimerUpdatedEvent justEnabled(long timeLeft, SleepTimerMode mode) {
        return new SleepTimerUpdatedEvent(Kind.ENABLED, normalise(timeLeft, mode), mode);
    }

    public static SleepTimerUpdatedEvent updated(long timeLeft) {
        return updated(timeLeft, SleepTimerMode.DURATION);
    }

    public static SleepTimerUpdatedEvent updated(long timeLeft, SleepTimerMode mode) {
        return new SleepTimerUpdatedEvent(Kind.UPDATED, normalise(timeLeft, mode), mode);
    }

    public static SleepTimerUpdatedEvent cancelled() {
        return new SleepTimerUpdatedEvent(Kind.CANCELLED, CANCELLED, SleepTimerMode.DURATION);
    }

    /** Clamps an overrun to 0; only a boundary timer's time left can be unknown. */
    private static long normalise(long timeLeft, SleepTimerMode mode) {
        if (timeLeft == TIME_LEFT_UNKNOWN && mode != SleepTimerMode.DURATION) {
            return TIME_LEFT_UNKNOWN;
        }
        return Math.max(0, timeLeft);
    }

    /**
     * Milliseconds left, or {@link #TIME_LEFT_UNKNOWN}. Callers that only care about running
     * timers check {@link #isCancelled()} and {@link #isOver()} first.
     */
    public long getTimeLeft() {
        return timeLeft;
    }

    public boolean isTimeLeftKnown() {
        return timeLeft != TIME_LEFT_UNKNOWN;
    }

    public SleepTimerMode getMode() {
        return mode;
    }

    /** The timer ran out; for {@link SleepTimerMode#DURATION} the service pauses playback on this. */
    public boolean isOver() {
        return kind == Kind.UPDATED && timeLeft == 0;
    }

    public boolean wasJustEnabled() {
        return kind == Kind.ENABLED;
    }

    public boolean isCancelled() {
        return kind == Kind.CANCELLED;
    }
}
