package dev.russell.yagi.parser.feed.transcript;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * The transcript formats the app reads, best first. The order is the preference used when a feed
 * item lists several {@code <podcast:transcript>} tags: the Podcast Index JSON carries speakers
 * and exact times, WebVTT carries times and usually speakers, SRT only times, HTML sometimes
 * times and speakers, and plain text neither.
 */
public enum TranscriptFormat {
    JSON, VTT, SRT, HTML, PLAIN;

    private static final char BYTE_ORDER_MARK = 0xFEFF;

    /** Rank of a MIME type the app cannot name; still usable, the content is sniffed. */
    public static final int RANK_UNKNOWN = values().length;

    /**
     * The format a MIME type names, or null when it names none of them. Parameters such as
     * {@code ;charset=utf-8} and case are ignored.
     */
    @Nullable
    public static TranscriptFormat fromMimeType(@Nullable String mimeType) {
        if (mimeType == null) {
            return null;
        }
        String type = mimeType.toLowerCase(Locale.ROOT);
        int parameters = type.indexOf(';');
        if (parameters >= 0) {
            type = type.substring(0, parameters);
        }
        type = type.trim();
        if (type.endsWith("json")) {
            return JSON;
        } else if (type.equals("text/vtt") || type.endsWith("/webvtt")) {
            return VTT;
        } else if (type.equals("application/x-subrip") || type.equals("application/srt")
                || type.equals("text/srt") || type.equals("text/x-srt")) {
            return SRT;
        } else if (type.equals("text/html") || type.equals("application/xhtml+xml")) {
            return HTML;
        } else if (type.equals("text/plain")) {
            return PLAIN;
        }
        return null;
    }

    /**
     * How much a MIME type is preferred; lower is better. Unknown types rank after every known
     * one but are still used when nothing better is offered.
     */
    public static int rankOf(@Nullable String mimeType) {
        TranscriptFormat format = fromMimeType(mimeType);
        return format == null ? RANK_UNKNOWN : format.ordinal();
    }

    /**
     * Guesses the format from the content alone. Returns null unless the text clearly is JSON,
     * WebVTT or SRT; those signatures are reliable enough to override a wrong MIME type.
     */
    @Nullable
    static TranscriptFormat sniffStrong(@NonNull String text) {
        String start = stripBom(text).trim();
        if (start.startsWith("WEBVTT")) {
            return VTT;
        }
        if (start.startsWith("{") || start.startsWith("[")) {
            return JSON;
        }
        if (TranscriptTime.SRT_TIMING_LINE.matcher(start).find()) {
            return SRT;
        }
        return null;
    }

    /** HTML when the text looks like markup, plain text otherwise. */
    @NonNull
    static TranscriptFormat sniffWeak(@NonNull String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.trim().startsWith("<") || lower.contains("<p") || lower.contains("<br")) {
            return HTML;
        }
        return PLAIN;
    }

    @NonNull
    static String stripBom(@NonNull String text) {
        return !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? text.substring(1) : text;
    }
}
