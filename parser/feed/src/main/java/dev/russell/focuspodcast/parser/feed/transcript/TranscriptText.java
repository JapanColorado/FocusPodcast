package dev.russell.focuspodcast.parser.feed.transcript;

import androidx.annotation.NonNull;

import org.jsoup.parser.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Text clean-up shared by the transcript parsers. */
final class TranscriptText {

    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    /** SubStation-style override blocks that some SRT files carry, e.g. {@code {\an8}}. */
    private static final Pattern ASS_OVERRIDE = Pattern.compile("\\{\\\\[^}]*\\}");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0\\u200E\\u200F]+");
    private static final Pattern BLANK_LINE = Pattern.compile("\\n[ \\t]*\\n");

    private TranscriptText() {
    }

    /** Removes tags, decodes entities and collapses whitespace (newlines included). */
    @NonNull
    static String clean(@NonNull String markup) {
        String withoutTags = TAG.matcher(markup).replaceAll("");
        withoutTags = ASS_OVERRIDE.matcher(withoutTags).replaceAll("");
        String decoded = Parser.unescapeEntities(withoutTags, false);
        return collapse(decoded);
    }

    /** Collapses every run of whitespace into one space and trims. */
    @NonNull
    static String collapse(@NonNull String text) {
        return WHITESPACE.matcher(text).replaceAll(" ").trim();
    }

    /** Unix line ends and no byte-order mark. */
    @NonNull
    static String normalizeLines(@NonNull String text) {
        return TranscriptFormat.stripBom(text).replace("\r\n", "\n").replace('\r', '\n');
    }

    /** The blocks of a cue file (VTT, SRT): runs of lines separated by blank lines. */
    @NonNull
    static List<String[]> blocks(@NonNull String text) {
        List<String[]> blocks = new ArrayList<>();
        for (String block : BLANK_LINE.split(normalizeLines(text))) {
            String trimmed = block.trim();
            if (!trimmed.isEmpty()) {
                blocks.add(trimmed.split("\n"));
            }
        }
        return blocks;
    }
}
