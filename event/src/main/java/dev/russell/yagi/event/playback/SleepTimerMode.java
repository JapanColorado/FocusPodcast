package dev.russell.yagi.event.playback;

/** What makes a running sleep timer stop playback. */
public enum SleepTimerMode {
    /** After a fixed amount of wall-clock time. */
    DURATION,
    /** When the current episode finishes (it still completes normally, the next one does not start). */
    END_OF_EPISODE,
    /** At the start of the next chapter of the current episode. */
    END_OF_CHAPTER;

    /** The mode stored under {@code name}, or {@link #DURATION} for null or an unknown name. */
    public static SleepTimerMode fromName(String name) {
        if (name != null) {
            for (SleepTimerMode mode : values()) {
                if (mode.name().equals(name)) {
                    return mode;
                }
            }
        }
        return DURATION;
    }
}
