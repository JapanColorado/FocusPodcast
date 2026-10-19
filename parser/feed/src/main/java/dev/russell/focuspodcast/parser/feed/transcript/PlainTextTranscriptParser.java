package dev.russell.focuspodcast.parser.feed.transcript;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import dev.russell.focuspodcast.model.transcript.TranscriptSegment;

/**
 * Plain text: one untimed line per paragraph (text between blank lines). A text without blank
 * lines is split at its line breaks instead, so that a one-line-per-sentence file does not turn
 * into a single wall of text.
 */
final class PlainTextTranscriptParser {

    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n[ \\t\\u00A0]*\\n");

    private PlainTextTranscriptParser() {
    }

    @NonNull
    static List<TranscriptSegment> parse(@NonNull String text) {
        String normalized = TranscriptText.normalizeLines(text).trim();
        String[] parts = PARAGRAPH_BREAK.split(normalized);
        if (parts.length == 1) {
            parts = normalized.split("\n");
        }
        List<TranscriptSegment> segments = new ArrayList<>();
        for (String part : parts) {
            String line = TranscriptText.collapse(part);
            if (!line.isEmpty()) {
                segments.add(new TranscriptSegment(TranscriptSegment.UNTIMED, TranscriptSegment.UNTIMED, null, line));
            }
        }
        return segments;
    }
}
