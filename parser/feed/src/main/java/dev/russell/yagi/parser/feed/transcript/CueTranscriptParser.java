package dev.russell.yagi.parser.feed.transcript;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.russell.yagi.model.transcript.TranscriptSegment;

/**
 * WebVTT and SRT: blocks of an optional identifier, a {@code start --> end} timing line and the
 * cue text. WebVTT's header and its NOTE, STYLE and REGION blocks are skipped; a voice tag
 * ({@code <v Speaker>} or {@code <v.class Speaker>}) names the speaker; every other tag, and
 * the inline timestamps of karaoke-style captions, is stripped. Cues whose timing cannot be
 * read are dropped.
 */
final class CueTranscriptParser {

    private static final String ARROW = "-->";
    private static final Pattern VOICE = Pattern.compile("<v(?:\\.[^\\s>]*)?\\s+([^>]+)>");

    private CueTranscriptParser() {
    }

    @NonNull
    static List<TranscriptSegment> parse(@NonNull String text) {
        List<TranscriptSegment> segments = new ArrayList<>();
        List<String[]> blocks = TranscriptText.blocks(text);
        for (int b = 0; b < blocks.size(); b++) {
            String[] lines = blocks.get(b);
            String first = lines[0].trim();
            if ((b == 0 && first.startsWith("WEBVTT")) || isMetadataBlock(first)) {
                continue;
            }
            TranscriptSegment segment = parseCue(lines);
            if (segment != null) {
                segments.add(segment);
            }
        }
        return segments;
    }

    private static boolean isMetadataBlock(String firstLine) {
        return !firstLine.contains(ARROW)
                && (startsWithWord(firstLine, "NOTE") || startsWithWord(firstLine, "STYLE")
                        || startsWithWord(firstLine, "REGION"));
    }

    private static boolean startsWithWord(String line, String word) {
        return line.startsWith(word)
                && (line.length() == word.length() || Character.isWhitespace(line.charAt(word.length())));
    }

    @Nullable
    private static TranscriptSegment parseCue(String[] lines) {
        int timing = -1;
        for (int i = 0; i < lines.length && i < 2; i++) {
            if (lines[i].contains(ARROW)) {
                timing = i;
                break;
            }
        }
        if (timing < 0) {
            return null;
        }
        String[] times = lines[timing].split(ARROW, 2);
        long start = TranscriptTime.parse(firstToken(times[0]));
        long end = TranscriptTime.parse(firstToken(times[1]));
        if (start < 0) {
            return null;
        }
        StringBuilder payload = new StringBuilder();
        for (int i = timing + 1; i < lines.length; i++) {
            if (payload.length() > 0) {
                payload.append('\n');
            }
            payload.append(lines[i]);
        }
        String raw = payload.toString();
        String speaker = null;
        Matcher voice = VOICE.matcher(raw);
        if (voice.find()) {
            speaker = TranscriptText.clean(voice.group(1));
        }
        String words = TranscriptText.clean(raw);
        if (words.isEmpty()) {
            return null;
        }
        return new TranscriptSegment(start, end >= start ? end : TranscriptSegment.UNTIMED, speaker, words);
    }

    private static String firstToken(String text) {
        String trimmed = text.trim();
        int space = 0;
        while (space < trimmed.length() && !Character.isWhitespace(trimmed.charAt(space))) {
            space++;
        }
        return trimmed.substring(0, space);
    }
}
