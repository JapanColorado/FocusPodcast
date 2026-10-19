package allen.town.podcast.model.playback;

/**
 * The player's "Voice" mode: Off (original audio), the two Clear voice strengths (de-essing,
 * noise reduction and leveling) and Warm (a fuller tone and softer "s" sounds for thin, harsh
 * microphones, without noise reduction or leveling). Stored as
 * these integers in the feeds table, in the global preference and in events, so the values must
 * never be renumbered.
 */
public final class ClearVoiceLevel {
    public static final int OFF = 0;
    public static final int LIGHT = 1;
    public static final int STRONG = 2;
    public static final int WARM = 3;

    private ClearVoiceLevel() {
    }

    /** Maps anything unknown (a corrupt or future value) to the nearest supported level. */
    public static int sanitize(int level) {
        if (level <= OFF) {
            return OFF;
        }
        return Math.min(level, WARM);
    }
}
