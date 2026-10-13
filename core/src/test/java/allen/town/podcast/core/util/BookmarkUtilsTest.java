package allen.town.podcast.core.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BookmarkUtilsTest {

    private static final String FORMAT = "%1$s — %2$s at %3$s";

    @Test
    public void formatsMinutesAndHours() {
        assertEquals("0:00", BookmarkUtils.formatPosition(0));
        assertEquals("0:00", BookmarkUtils.formatPosition(-5000));
        assertEquals("0:59", BookmarkUtils.formatPosition(59_999));
        assertEquals("12:34", BookmarkUtils.formatPosition((12 * 60 + 34) * 1000L));
        assertEquals("1:02:03", BookmarkUtils.formatPosition((3600 + 2 * 60 + 3) * 1000L));
        assertEquals("10:00:00", BookmarkUtils.formatPosition(36_000_000L));
    }

    @Test
    public void shareTextHasHeadlineNoteAndLink() {
        String text = BookmarkUtils.buildShareText(FORMAT, "Episode 1", "My Show",
                (12 * 60 + 34) * 1000L, "  great quote  ", "https://example.com/ep1");
        assertEquals("Episode 1 — My Show at 12:34\ngreat quote\nhttps://example.com/ep1", text);
    }

    @Test
    public void shareTextLeavesOutBlankNoteAndLink() {
        assertEquals("Episode 1 — My Show at 0:05",
                BookmarkUtils.buildShareText(FORMAT, "Episode 1", "My Show", 5000, null, null));
        assertEquals("Episode 1 — My Show at 0:05\nhttps://example.com",
                BookmarkUtils.buildShareText(FORMAT, "Episode 1", "My Show", 5000, " ", "https://example.com"));
        assertEquals("Episode 1 — My Show at 0:05\nnote",
                BookmarkUtils.buildShareText(FORMAT, "Episode 1", "My Show", 5000, "note", ""));
    }

    @Test
    public void shareTextToleratesMissingTitles() {
        assertEquals(" —  at 1:00", BookmarkUtils.buildShareText(FORMAT, null, null, 60_000, null, null));
    }

    @Test
    public void tickFractionMapsPositionOntoTheBar() {
        assertEquals(0f, BookmarkUtils.tickFraction(0, 1000), 0f);
        assertEquals(0.25f, BookmarkUtils.tickFraction(250, 1000), 1e-6f);
        assertEquals(1f, BookmarkUtils.tickFraction(1000, 1000), 0f);
    }

    @Test
    public void tickFractionRejectsUnknownDurationAndOutOfRangePositions() {
        assertEquals(BookmarkUtils.NO_TICK, BookmarkUtils.tickFraction(10, 0), 0f);
        assertEquals(BookmarkUtils.NO_TICK, BookmarkUtils.tickFraction(10, -1), 0f);
        assertEquals(BookmarkUtils.NO_TICK, BookmarkUtils.tickFraction(1001, 1000), 0f);
        assertEquals(BookmarkUtils.NO_TICK, BookmarkUtils.tickFraction(-1, 1000), 0f);
    }
}
