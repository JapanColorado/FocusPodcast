package allen.town.podcast.core.util;

import allen.town.podcast.model.playback.Playable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Pure helpers for bookmarks: how a position is written, the text a shared bookmark carries,
 * and where a bookmark's tick sits on the position bar.
 */
public final class BookmarkUtils {

    /** {@link #tickFraction} for a bookmark that cannot be placed on the bar. */
    public static final float NO_TICK = -1f;

    private BookmarkUtils() {
    }

    /** A position as "m:ss", or "h:mm:ss" from an hour up; negative positions read as 0:00. */
    @NonNull
    public static String formatPosition(long positionMs) {
        long seconds = Math.max(0, positionMs) / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long rest = seconds % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, rest);
    }

    /**
     * The text of a shared bookmark: the headline built from {@code headlineFormat} (arguments:
     * episode title, podcast title, position), then the note and the link, each on its own line
     * and left out when blank.
     *
     * @param headlineFormat e.g. {@code "%1$s — %2$s at %3$s"}
     */
    @NonNull
    public static String buildShareText(@NonNull String headlineFormat, @Nullable String episodeTitle,
                                        @Nullable String podcastTitle, long positionMs,
                                        @Nullable String note, @Nullable String link) {
        StringBuilder text = new StringBuilder(String.format(headlineFormat,
                orEmpty(episodeTitle), orEmpty(podcastTitle), formatPosition(positionMs)));
        if (note != null && !note.trim().isEmpty()) {
            text.append('\n').append(note.trim());
        }
        if (link != null && !link.trim().isEmpty()) {
            text.append('\n').append(link.trim());
        }
        return text.toString();
    }

    /**
     * Where a bookmark at {@code positionMs} sits along a bar that spans {@code durationMs}, from
     * 0 to 1; {@link #NO_TICK} when the duration is unknown or the position lies outside it.
     */
    public static float tickFraction(long positionMs, long durationMs) {
        if (durationMs <= 0 || positionMs < 0 || positionMs > durationMs) {
            return NO_TICK;
        }
        return (float) ((double) positionMs / (double) durationMs);
    }

    /**
     * Sets {@code media} up to start at exactly {@code positionMs}: the position, and a last-played
     * time of {@code nowMs}. Starting a prepared episode rewinds by how long it was paused
     * ({@code RewindAfterPauseUtils}, measured from the last-played time), which would otherwise
     * land up to 20 s before a bookmark when the episode was last heard hours or days ago.
     */
    public static void prepareStartAt(@NonNull Playable media, int positionMs, long nowMs) {
        media.setPosition(Math.max(0, positionMs));
        media.setLastPlayedTime(nowMs);
    }

    @NonNull
    private static String orEmpty(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
