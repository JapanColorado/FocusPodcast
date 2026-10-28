package dev.russell.focuspodcast.model.playback;

/**
 * The player's "Volume leveling" mode: Off, Gentle (brings every podcast to about the same
 * loudness, following the program slowly so the dynamics within it are kept) and Strong (also
 * evens out speakers within an episode and squeezes loud moments, for noisy places). Stored as
 * these integers in the feeds table, in the global preference and in events, so the values must
 * never be renumbered.
 */
public final class VolumeLeveling {
    public static final int OFF = 0;
    public static final int GENTLE = 1;
    public static final int STRONG = 2;

    private VolumeLeveling() {
    }

    /** Maps anything unknown (a corrupt or future value) to the nearest supported mode. */
    public static int sanitize(int mode) {
        if (mode <= OFF) {
            return OFF;
        }
        return Math.min(mode, STRONG);
    }
}
