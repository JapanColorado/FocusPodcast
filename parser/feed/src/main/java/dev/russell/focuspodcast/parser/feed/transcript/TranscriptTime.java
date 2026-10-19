package dev.russell.focuspodcast.parser.feed.transcript;

import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Timestamps as transcripts write them. */
final class TranscriptTime {

    /** {@code [hh:]mm:ss[.fff]} or {@code [hh:]mm:ss[,fff]}; hours may have any number of digits. */
    private static final Pattern CLOCK = Pattern.compile("^(?:(\\d+):)?(\\d{1,2}):(\\d{1,2})(?:[.,](\\d+))?$");
    /** Plain seconds, e.g. {@code 12} or {@code 12.5}. */
    private static final Pattern SECONDS = Pattern.compile("^(\\d+)(?:\\.(\\d+))?$");
    /** An SRT cue's timing line: comma before the milliseconds. */
    static final Pattern SRT_TIMING_LINE = Pattern.compile(
            "(?m)^\\s*\\d+:\\d{1,2}:\\d{1,2},\\d+\\s*-->\\s*\\d+:\\d{1,2}:\\d{1,2},\\d+");

    private TranscriptTime() {
    }

    /**
     * Milliseconds of a clock time such as {@code 01:02:03.500}, {@code 02:03.5},
     * {@code 00:00:01,000} or {@code 12.5} (seconds); -1 when it is none of those.
     */
    static long parse(@Nullable String value) {
        if (value == null) {
            return -1;
        }
        String trimmed = value.trim();
        Matcher clock = CLOCK.matcher(trimmed);
        if (clock.matches()) {
            long hours = clock.group(1) == null ? 0 : parseLong(clock.group(1));
            long minutes = parseLong(clock.group(2));
            long seconds = parseLong(clock.group(3));
            if (hours < 0 || minutes < 0 || seconds < 0 || seconds >= 60) {
                return -1;
            }
            return ((hours * 60 + minutes) * 60 + seconds) * 1000 + fractionMillis(clock.group(4));
        }
        Matcher plain = SECONDS.matcher(trimmed);
        if (plain.matches()) {
            long seconds = parseLong(plain.group(1));
            return seconds < 0 ? -1 : seconds * 1000 + fractionMillis(plain.group(2));
        }
        return -1;
    }

    /** "5" is 500 ms, "05" 50 ms, "0505" 50 ms (anything past milliseconds is dropped). */
    private static long fractionMillis(@Nullable String digits) {
        if (digits == null || digits.isEmpty()) {
            return 0;
        }
        String millis = (digits + "00").substring(0, 3);
        return Math.max(0, parseLong(millis));
    }

    private static long parseLong(String digits) {
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            // Only reachable for a number too long for a long; treated as an unreadable time.
            return -1;
        }
    }
}
