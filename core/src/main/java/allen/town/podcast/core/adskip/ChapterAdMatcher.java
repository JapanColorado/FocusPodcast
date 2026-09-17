package allen.town.podcast.core.adskip;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import allen.town.podcast.model.feed.AdSegment;
import allen.town.podcast.model.feed.Chapter;

/**
 * Reads the episode's chapter list and turns ad-labelled chapters into {@link AdSegment}s.
 *
 * <p>This is the cheap, exact half of ad detection: when a publisher bothers to title a chapter
 * "Sponsor" or "Mid-roll", there is nothing to infer. Such segments carry a confidence of 1 and, in
 * {@link AdSegmentMerger}, override anything the audio analysis guessed in the same range.
 *
 * <p>Matching is case-insensitive and word-boundary aware, which is the whole difficulty: "ad" must
 * not fire on "advice", "adventure", "Adam", "broadcast" or "podcast". Every pattern is therefore
 * anchored with {@code \b} on both sides and spelled out with its real inflections rather than
 * relying on a prefix match.
 */
public final class ChapterAdMatcher {

    /**
     * Word-boundary-anchored alternation of the labels publishers actually use. Kept as one pattern
     * so the boundary anchors apply uniformly; the optional hyphens cover "mid-roll" / "midroll".
     */
    private static final Pattern AD_TITLE = Pattern.compile(
            "\\b(?:"
                    + "ads?"
                    + "|advert(?:s|ise[sd]?|ising|isement|isements)?"
                    + "|sponsor(?:s|ed|ing|ship|ships)?"
                    + "|promo(?:s|tion|tions|tional)?"
                    + "|commercials?"
                    + "|mid-?rolls?"
                    + "|pre-?rolls?"
                    + "|post-?rolls?"
                    + "|breaks?"
                    + ")\\b",
            Pattern.CASE_INSENSITIVE);

    private ChapterAdMatcher() {
    }

    /** True when a chapter title names an ad break. Null and blank titles are never ads. */
    public static boolean isAdTitle(@Nullable String title) {
        if (title == null || title.trim().isEmpty()) {
            return false;
        }
        Matcher matcher = AD_TITLE.matcher(title);
        return matcher.find();
    }

    /**
     * Converts ad-labelled chapters into segments.
     *
     * <p>Each segment runs from its chapter's start to the start of the next chapter, or to the end
     * of the episode for the last one. Chapters have no declared length in any feed format, so the
     * next start is the only end available.
     *
     * @param chapters   the episode's chapters; need not be sorted, a sorted copy is taken.
     * @param durationMs episode duration, used as the end of the final chapter. Values that are not
     *                   positive mean "unknown", and a trailing ad chapter is then skipped because
     *                   its end cannot be established.
     * @param feedItemId stamped onto every returned segment.
     * @return segments with Source.CHAPTER and confidence 1, sorted by start.
     */
    @NonNull
    public static List<AdSegment> match(@Nullable List<Chapter> chapters, long durationMs,
                                        long feedItemId) {
        if (chapters == null || chapters.isEmpty()) {
            return Collections.emptyList();
        }
        List<Chapter> sorted = new ArrayList<>(chapters.size());
        for (Chapter chapter : chapters) {
            if (chapter != null && chapter.getStart() >= 0) {
                sorted.add(chapter);
            }
        }
        Collections.sort(sorted, new Comparator<Chapter>() {
            @Override
            public int compare(Chapter a, Chapter b) {
                return Long.compare(a.getStart(), b.getStart());
            }
        });

        List<AdSegment> out = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Chapter chapter = sorted.get(i);
            if (!isAdTitle(chapter.getTitle())) {
                continue;
            }
            long start = chapter.getStart();
            long end;
            if (i + 1 < sorted.size()) {
                end = sorted.get(i + 1).getStart();
            } else if (durationMs > 0) {
                end = durationMs;
            } else {
                continue;
            }
            if (durationMs > 0) {
                end = Math.min(end, durationMs);
            }
            if (end > start) {
                out.add(new AdSegment(feedItemId, start, end, AdSegment.Source.CHAPTER, 1f));
            }
        }
        return out;
    }
}
