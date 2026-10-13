package allen.town.podcast.parser.feed.transcript;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import allen.town.podcast.model.transcript.TranscriptSegment;

/**
 * Joins the short cues of automatic captions (often two to five words each) into sentence-sized
 * lines. Two consecutive timed cues are joined when the same speaker (or no speaker) says both,
 * the first does not end a sentence, the pause between them is short and the result stays
 * modest in length and duration. Untimed segments are left as they are.
 */
final class TranscriptSegmentMerger {

    /** Longest pause between two cues that still reads as one sentence. */
    static final long MAX_GAP_MS = 1500;
    /** Longest a merged line may last. */
    static final long MAX_LINE_MS = 20_000;
    /** Longest a merged line may be. */
    static final int MAX_LINE_CHARS = 200;

    /** . ? ! and the ellipsis, ideographic full stop and full-width ? and ! */
    private static final String SENTENCE_END = ".?!\u2026\u3002\uFF1F\uFF01";
    /** Quotes and brackets that may follow a sentence end. */
    private static final String CLOSERS = "\"')]\u201D\u2019\u00BB\u300D";

    private TranscriptSegmentMerger() {
    }

    @NonNull
    static List<TranscriptSegment> merge(@NonNull List<TranscriptSegment> segments) {
        List<TranscriptSegment> result = new ArrayList<>(segments.size());
        TranscriptSegment current = null;
        for (TranscriptSegment next : segments) {
            if (current != null && canJoin(current, next)) {
                long end = next.getEndMs() != TranscriptSegment.UNTIMED ? next.getEndMs() : current.getEndMs();
                current = new TranscriptSegment(current.getStartMs(), end, current.getSpeaker(),
                        current.getText() + " " + next.getText());
            } else {
                if (current != null) {
                    result.add(current);
                }
                current = next;
            }
        }
        if (current != null) {
            result.add(current);
        }
        return result;
    }

    private static boolean canJoin(TranscriptSegment first, TranscriptSegment second) {
        if (!first.isTimed() || !second.isTimed() || second.getStartMs() < first.getStartMs()) {
            return false;
        }
        if (!Objects.equals(first.getSpeaker(), second.getSpeaker()) || endsSentence(first.getText())) {
            return false;
        }
        if (first.getEndMs() != TranscriptSegment.UNTIMED && second.getStartMs() - first.getEndMs() > MAX_GAP_MS) {
            return false;
        }
        long end = second.getEndMs() != TranscriptSegment.UNTIMED ? second.getEndMs() : second.getStartMs();
        return end - first.getStartMs() <= MAX_LINE_MS
                && first.getText().length() + 1 + second.getText().length() <= MAX_LINE_CHARS;
    }

    static boolean endsSentence(@NonNull String text) {
        int i = text.length() - 1;
        while (i >= 0 && CLOSERS.indexOf(text.charAt(i)) >= 0) {
            i--;
        }
        return i >= 0 && SENTENCE_END.indexOf(text.charAt(i)) >= 0;
    }
}
