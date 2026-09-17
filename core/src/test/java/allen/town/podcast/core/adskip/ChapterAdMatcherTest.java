package allen.town.podcast.core.adskip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import allen.town.podcast.model.feed.AdSegment;
import allen.town.podcast.model.feed.Chapter;

/**
 * The matcher has to be aggressive about real ad labels and completely silent about words that
 * merely start with "ad". These are the cases that motivated the word-boundary anchors.
 */
public class ChapterAdMatcherTest {

    private static Chapter chapter(long startMs, String title) {
        return new Chapter(startMs, title, null, null);
    }

    @Test
    public void matchesAdLabels() {
        String[] positives = {
                "Ad", "Ads", "ad break", "Advert", "Adverts", "Advertisement", "Advertisements",
                "advertising", "Sponsor", "Sponsors", "Sponsored", "sponsorship",
                "A word from our sponsor", "A word from our sponsors", "Promo", "Promotion",
                "promotional message", "Commercial", "Commercials", "Midroll", "Mid-roll",
                "MID-ROLL", "Preroll", "Pre-roll", "Break", "Sponsored by ACME",
                "-- ADVERTISEMENT --", "[ad]", "Werbung / ad",
        };
        for (String title : positives) {
            assertTrue("expected a match for \"" + title + "\"",
                    ChapterAdMatcher.isAdTitle(title));
        }
    }

    @Test
    public void doesNotMatchInnocentTitles() {
        String[] negatives = {
                "Podcast", "This podcast", "Advice", "Some advice for you", "Adventure",
                "The Great Adventure", "Adam", "Adam and Eve", "Broadcast", "Broadcasting",
                "Adaptation", "Adjacent", "Breaking news", "Breakfast", "Breakdown",
                "Introduction", "Interview with Adam", "Sponge", "Commercialisation",
                "Addendum", "Adoption", null, "", "   ",
        };
        for (String title : negatives) {
            assertFalse("did not expect a match for \"" + title + "\"",
                    ChapterAdMatcher.isAdTitle(title));
        }
    }

    @Test
    public void segmentRunsToTheNextChapter() {
        List<Chapter> chapters = Arrays.asList(
                chapter(0, "Intro"),
                chapter(60_000, "Sponsor"),
                chapter(150_000, "Main topic"),
                chapter(900_000, "Outro"));
        List<AdSegment> segments = ChapterAdMatcher.match(chapters, 1_200_000, 7L);
        assertEquals(1, segments.size());
        AdSegment segment = segments.get(0);
        assertEquals(60_000, segment.getStartMs());
        assertEquals(150_000, segment.getEndMs());
        assertEquals(AdSegment.Source.CHAPTER, segment.getSource());
        assertEquals(1f, segment.getConfidence(), 1e-6);
        assertEquals(7L, segment.getFeedItemId());
    }

    @Test
    public void lastChapterRunsToTheEndOfTheEpisode() {
        List<Chapter> chapters = Arrays.asList(
                chapter(0, "Intro"),
                chapter(1_100_000, "Ad"));
        List<AdSegment> segments = ChapterAdMatcher.match(chapters, 1_200_000, 1L);
        assertEquals(1, segments.size());
        assertEquals(1_100_000, segments.get(0).getStartMs());
        assertEquals(1_200_000, segments.get(0).getEndMs());
    }

    @Test
    public void trailingAdChapterIsSkippedWhenTheDurationIsUnknown() {
        List<Chapter> chapters = Arrays.asList(
                chapter(0, "Intro"),
                chapter(1_100_000, "Ad"));
        assertTrue(ChapterAdMatcher.match(chapters, 0, 1L).isEmpty());
    }

    @Test
    public void consecutiveAdChaptersBecomeSeparateSegments() {
        List<Chapter> chapters = Arrays.asList(
                chapter(0, "Intro"),
                chapter(30_000, "Ad 1"),
                chapter(60_000, "Ad 2"),
                chapter(90_000, "Topic"));
        List<AdSegment> segments = ChapterAdMatcher.match(chapters, 600_000, 1L);
        assertEquals(2, segments.size());
        assertEquals(30_000, segments.get(0).getStartMs());
        assertEquals(60_000, segments.get(0).getEndMs());
        assertEquals(60_000, segments.get(1).getStartMs());
        assertEquals(90_000, segments.get(1).getEndMs());
    }

    @Test
    public void unsortedInputIsSortedAndNullsAreIgnored() {
        List<Chapter> chapters = new ArrayList<>(Arrays.asList(
                chapter(90_000, "Topic"),
                null,
                chapter(30_000, "Sponsor"),
                chapter(0, "Intro")));
        List<AdSegment> segments = ChapterAdMatcher.match(chapters, 600_000, 1L);
        assertEquals(1, segments.size());
        assertEquals(30_000, segments.get(0).getStartMs());
        assertEquals(90_000, segments.get(0).getEndMs());
    }

    @Test
    public void emptyAndNullInputsAreSafe() {
        assertTrue(ChapterAdMatcher.match(null, 1000, 1L).isEmpty());
        assertTrue(ChapterAdMatcher.match(Collections.<Chapter>emptyList(), 1000, 1L).isEmpty());
    }
}
