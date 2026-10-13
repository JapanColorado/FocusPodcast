package allen.town.podcast.model.transcript;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A parsed episode transcript: its lines in order. A transcript is <em>timed</em> when every
 * line has a start time, in which case the starts never decrease and {@link #indexAt} finds the
 * line being spoken at a playback position. An untimed transcript (plain text, HTML without
 * times) is only read, never followed.
 */
public final class Transcript {

    private static final Transcript EMPTY = new Transcript(Collections.emptyList());

    @NonNull
    private final List<TranscriptSegment> segments;
    private final boolean timed;

    public Transcript(@NonNull List<TranscriptSegment> segments) {
        this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
        boolean allTimed = !segments.isEmpty();
        long previousStart = Long.MIN_VALUE;
        for (TranscriptSegment segment : segments) {
            if (!segment.isTimed() || segment.getStartMs() < previousStart) {
                allTimed = false;
                break;
            }
            previousStart = segment.getStartMs();
        }
        this.timed = allTimed;
    }

    @NonNull
    public static Transcript empty() {
        return EMPTY;
    }

    @NonNull
    public List<TranscriptSegment> getSegments() {
        return segments;
    }

    public int size() {
        return segments.size();
    }

    public boolean isEmpty() {
        return segments.isEmpty();
    }

    /** Whether every line has a start time and the starts are in order. */
    public boolean isTimed() {
        return timed;
    }

    /**
     * The index of the line being spoken at {@code positionMs}: the last line that started at or
     * before it. -1 before the first line and for an untimed transcript. Binary search, so it is
     * cheap enough to call on every position update.
     */
    public int indexAt(long positionMs) {
        if (!timed || segments.isEmpty() || positionMs < segments.get(0).getStartMs()) {
            return -1;
        }
        int low = 0;
        int high = segments.size() - 1;
        while (low < high) {
            // upper middle, so that low always moves when the middle line has started
            int mid = (low + high + 1) >>> 1;
            if (segments.get(mid).getStartMs() <= positionMs) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    /**
     * The words of every line, one line per row with its speaker in front when known. This is
     * what the episode search looks through, so it carries no markup or timestamps.
     */
    @NonNull
    public String toPlainText() {
        StringBuilder builder = new StringBuilder();
        for (TranscriptSegment segment : segments) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            if (segment.getSpeaker() != null) {
                builder.append(segment.getSpeaker()).append(": ");
            }
            builder.append(segment.getText());
        }
        return builder.toString();
    }
}
