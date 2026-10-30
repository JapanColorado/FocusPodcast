package dev.russell.yagi.core.util;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns text a user typed or pasted into a feed URL, or tells that it is not one.
 *
 * <p>Pasted links often carry invisible characters: messaging apps (WeChat in particular) insert
 * non-breaking and zero-width spaces, and copied lines end in newlines. A URL can never contain
 * raw whitespace, so every whitespace, space-separator and zero-width character is removed, not
 * only the ones at the ends. The podcast pseudo-schemes {@code feed:}, {@code itpc:} and
 * {@code pcast:} are rewritten to plain HTTP (or to the HTTP(S) URL they wrap). Anything that is not
 * an http(s) URL with a host afterwards yields {@code null}, so callers can treat the input as a
 * search term instead.</p>
 */
public final class FeedUrlInput {

    private static final String[] PODCAST_SCHEMES = {"feed:", "itpc:", "pcast:"};
    private static final Pattern HTTP_URL =
            Pattern.compile("^https?://([^/?#@]*@)?[^/?#@:]+(:\\d+)?([/?#].*)?$", Pattern.CASE_INSENSITIVE);

    private FeedUrlInput() {
    }

    /**
     * @return the cleaned http(s) URL, or {@code null} if the input is not a feed URL
     */
    @Nullable
    public static String normalize(@Nullable String input) {
        if (input == null) {
            return null;
        }
        String url = stripInvisible(input);
        String lower = url.toLowerCase(Locale.ROOT);
        for (String scheme : PODCAST_SCHEMES) {
            if (lower.startsWith(scheme)) {
                url = url.substring(scheme.length());
                if (url.startsWith("//")) {
                    url = url.substring(2);
                }
                lower = url.toLowerCase(Locale.ROOT);
                if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                    url = "http://" + url;
                    lower = "http://" + lower;
                }
                break;
            }
        }
        if (!HTTP_URL.matcher(url).matches()) {
            return null;
        }
        // Scheme names are case-insensitive; the rest of the URL is kept as typed.
        int schemeEnd = url.indexOf("://");
        return lower.substring(0, schemeEnd) + url.substring(schemeEnd);
    }

    private static String stripInvisible(String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isWhitespace(c) || Character.isSpaceChar(c) || isZeroWidth(c)) {
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean isZeroWidth(char c) {
        return c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\u2060' || c == '\uFEFF';
    }
}
