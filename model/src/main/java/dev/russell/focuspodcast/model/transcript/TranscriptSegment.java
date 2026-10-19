package dev.russell.focuspodcast.model.transcript;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One line of a {@link Transcript}: what was said, optionally who said it, and, for a timed
 * transcript, when. Immutable.
 */
public final class TranscriptSegment {

    /** Start or end time of a segment whose transcript carries no timing. */
    public static final long UNTIMED = -1;

    private final long startMs;
    private final long endMs;
    @Nullable
    private final String speaker;
    @NonNull
    private final String text;

    /**
     * @param startMs start in ms, or {@link #UNTIMED}
     * @param endMs   end in ms, or {@link #UNTIMED} when unknown (the next segment's start then
     *                stands in for it)
     * @param speaker the speaker's name, null or blank when the transcript does not say
     * @param text    the words, already stripped of markup
     */
    public TranscriptSegment(long startMs, long endMs, @Nullable String speaker, @NonNull String text) {
        this.startMs = startMs < 0 ? UNTIMED : startMs;
        this.endMs = endMs < 0 ? UNTIMED : endMs;
        String trimmedSpeaker = speaker == null ? null : speaker.trim();
        this.speaker = trimmedSpeaker == null || trimmedSpeaker.isEmpty() ? null : trimmedSpeaker;
        this.text = text;
    }

    public long getStartMs() {
        return startMs;
    }

    public long getEndMs() {
        return endMs;
    }

    public boolean isTimed() {
        return startMs != UNTIMED;
    }

    @Nullable
    public String getSpeaker() {
        return speaker;
    }

    @NonNull
    public String getText() {
        return text;
    }

    @NonNull
    @Override
    public String toString() {
        return "TranscriptSegment{" + startMs + "-" + endMs + ", " + speaker + ": " + text + "}";
    }
}
