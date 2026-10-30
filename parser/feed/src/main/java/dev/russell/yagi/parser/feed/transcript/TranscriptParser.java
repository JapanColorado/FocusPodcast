package dev.russell.yagi.parser.feed.transcript;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;

import java.util.List;

import dev.russell.yagi.model.transcript.Transcript;
import dev.russell.yagi.model.transcript.TranscriptSegment;

/**
 * Turns a downloaded transcript into a {@link Transcript}. The format comes from the content
 * when it is unmistakable (JSON, a WEBVTT header, SRT timing lines), since servers often send
 * transcripts with a generic MIME type, otherwise from the MIME type the feed gave, otherwise
 * from a last look at the content (markup or plain text). Never throws: unreadable input gives an
 * empty transcript.
 */
public final class TranscriptParser {

    private static final String TAG = "TranscriptParser";

    private TranscriptParser() {
    }

    @NonNull
    public static Transcript parse(@Nullable String text, @Nullable String mimeType) {
        if (text == null || text.trim().isEmpty()) {
            return Transcript.empty();
        }
        TranscriptFormat format = TranscriptFormat.sniffStrong(text);
        if (format == null) {
            format = TranscriptFormat.fromMimeType(mimeType);
        }
        if (format == null) {
            format = TranscriptFormat.sniffWeak(text);
        }
        List<TranscriptSegment> segments = parseAs(text, format);
        if (segments == null) {
            // not JSON after all, e.g. prose that starts with a bracket
            segments = parseAs(text, TranscriptFormat.sniffWeak(text));
        }
        return segments == null ? Transcript.empty() : new Transcript(TranscriptSegmentMerger.merge(segments));
    }

    /** The segments, or null when the text is not in {@code format} at all (only JSON can tell). */
    @Nullable
    static List<TranscriptSegment> parseAs(@NonNull String text, @NonNull TranscriptFormat format) {
        switch (format) {
            case JSON:
                try {
                    return JsonTranscriptParser.parse(text);
                } catch (JSONException e) {
                    Log.w(TAG, "transcript is not valid JSON: " + e.getMessage());
                    return null;
                }
            case VTT:
            case SRT:
                return CueTranscriptParser.parse(text);
            case HTML:
                return HtmlTranscriptParser.parse(text);
            case PLAIN:
            default:
                return PlainTextTranscriptParser.parse(text);
        }
    }
}
