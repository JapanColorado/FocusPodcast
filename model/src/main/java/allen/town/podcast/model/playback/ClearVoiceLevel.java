package allen.town.podcast.model.playback;

/**
 * Strength of the "Clear voice" enhancer (noise reduction, speech EQ and leveling). Stored as
 * these integers in the feeds table, in the global preference and in events, so the values must
 * never be renumbered.
 */
public final class ClearVoiceLevel {
    public static final int OFF = 0;
    public static final int LIGHT = 1;
    public static final int STRONG = 2;

    private ClearVoiceLevel() {
    }

    /** Maps anything unknown (a corrupt or future value) to the nearest supported level. */
    public static int sanitize(int level) {
        if (level <= OFF) {
            return OFF;
        }
        return Math.min(level, STRONG);
    }
}
