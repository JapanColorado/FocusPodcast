package allen.town.podcast.storage.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;

import allen.town.podcast.model.feed.EpisodeSearchOptions;
import allen.town.podcast.model.feed.EpisodeSearchOptions.DateRange;
import allen.town.podcast.model.feed.EpisodeSearchOptions.DurationRange;
import allen.town.podcast.model.feed.EpisodeSearchOptions.Field;
import allen.town.podcast.model.feed.EpisodeSearchOptions.Sort;
import allen.town.podcast.model.feed.Feed;
import allen.town.podcast.model.feed.FeedItem;
import allen.town.podcast.model.feed.FeedItemFilter;
import allen.town.podcast.model.feed.FeedMedia;
import allen.town.podcast.storage.db.mapper.FeedItemCursorMapper;

/**
 * The episode search with filters, scope and sort ({@link Db#searchItems(String, EpisodeSearchOptions, long)}),
 * all of which is SQL: each option alone, combinations, the bounds of the date and duration ranges,
 * every sort order and the escaping of LIKE wildcards.
 */
@RunWith(RobolectricTestRunner.class)
public class EpisodeSearchQueryTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final long MINUTE = 60_000L;
    private static final long DAY = 24 * 60 * MINUTE;

    private Feed space;
    private Feed other;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        Db.init(context);
    }

    @After
    public void tearDown() {
        Db.tearDownTests();
        ApplicationProvider.getApplicationContext().deleteDatabase(Db.DATABASE_NAME);
    }

    /**
     * Space show: "Rocket launch" (2 days, 15 min, unplayed, downloaded), "Gardening" (notes mention a
     * rocket; 20 days, 45 min, played), "Old rocket news" (200 days, 90 min, unplayed, favorite),
     * "No media rocket" (400 days, no media). Other show: "Rocket recap" (1 day, 30 min).
     */
    private void seed() {
        Db db = Db.getInstance();
        space = new Feed("https://example.com/space.xml", null, "Space show");
        space.setItems(new ArrayList<>(Arrays.asList(
                item(space, "Rocket launch", "We talk about engines", 2 * DAY, FeedItem.UNPLAYED, 15 * MINUTE),
                item(space, "Gardening", "A rocket of a tomato", 20 * DAY, FeedItem.PLAYED, 45 * MINUTE),
                item(space, "Old rocket news", "archives", 200 * DAY, FeedItem.NEW, 90 * MINUTE),
                item(space, "No media rocket", "", 400 * DAY, FeedItem.NEW, -1))));
        space.getItems().get(0).getMedia().setDownloaded(true);
        other = new Feed("https://example.com/other.xml", null, "Other show");
        other.setItems(new ArrayList<>(Collections.singletonList(
                item(other, "Rocket recap", "rocket", DAY, FeedItem.UNPLAYED, 30 * MINUTE))));
        db.setCompleteFeed(space, other);
        db.addFavoriteItem(space.getItems().get(2));
    }

    // ------------------------------------------------------------ search in

    @Test
    public void defaultsSearchTitleAndShowNotes() {
        seed();
        assertEquals(Arrays.asList("Rocket recap", "Rocket launch", "Old rocket news", "No media rocket", "Gardening"),
                titles("rocket", EpisodeSearchOptions.defaults()));
    }

    @Test
    public void titleOnlyIgnoresShowNotes() {
        seed();
        List<String> found = titles("rocket", byNewest().withFields(EnumSet.of(Field.TITLE)));
        assertEquals(Arrays.asList("Rocket recap", "Rocket launch", "Old rocket news", "No media rocket"), found);
    }

    @Test
    public void showNotesOnlyIgnoresTitles() {
        seed();
        List<String> found = titles("rocket", byNewest().withFields(EnumSet.of(Field.SHOW_NOTES)));
        assertEquals(Arrays.asList("Rocket recap", "Gardening"), found);
    }

    @Test
    public void emptyFieldChoiceMeansEveryField() {
        assertEquals(EnumSet.allOf(Field.class),
                EpisodeSearchOptions.defaults().withFields(Collections.emptySet()).fields);
        assertFalse(EpisodeSearchOptions.defaults().isFiltering());
        assertTrue(EpisodeSearchOptions.defaults().withFields(EnumSet.of(Field.TITLE)).isFiltering());
        assertFalse("the podcast scope is not a filter",
                EpisodeSearchOptions.defaults().withFeedId(5).isFiltering());
    }

    @Test
    public void everyWordMustMatchInSomeField() {
        seed();
        // "rocket" in the title, "engines" in the notes
        assertEquals(Collections.singletonList("Rocket launch"), titles("rocket engines", byNewest()));
        assertEquals(Collections.emptyList(),
                titles("rocket engines", byNewest().withFields(EnumSet.of(Field.TITLE))));
    }

    @Test
    public void blankQueryMatchesEverythingTheOptionsLetThrough() {
        seed();
        assertEquals(1, titles("   ", byNewest().withFeedId(other.getId())).size());
        assertEquals(5, titles("", byNewest()).size());
    }

    // ---------------------------------------------------------------- scope

    @Test
    public void scopeToOneFeed() {
        seed();
        assertEquals(Collections.singletonList("Rocket recap"), titles("rocket", byNewest().withFeedId(other.getId())));
        assertEquals(4, titles("rocket", byNewest().withFeedId(space.getId())).size());
    }

    @Test
    public void dateRanges() {
        seed();
        assertEquals(Arrays.asList("Rocket recap", "Rocket launch"),
                titles("rocket", byNewest().withDateRange(DateRange.PAST_WEEK)));
        assertEquals(Arrays.asList("Rocket recap", "Rocket launch", "Gardening"),
                titles("rocket", byNewest().withDateRange(DateRange.PAST_MONTH)));
        assertEquals(Arrays.asList("Rocket recap", "Rocket launch", "Gardening", "Old rocket news"),
                titles("rocket", byNewest().withDateRange(DateRange.PAST_YEAR)));
        assertEquals(5, titles("rocket", byNewest().withDateRange(DateRange.ANY)).size());
    }

    @Test
    public void dateRangeBoundIsInclusive() {
        Feed feed = new Feed("https://example.com/edge.xml", null, "Edge");
        feed.setItems(new ArrayList<>(Arrays.asList(
                item(feed, "exactly a week", "", 7 * DAY, FeedItem.NEW, -1),
                item(feed, "a week and a second", "", 7 * DAY + 1000, FeedItem.NEW, -1))));
        Db.getInstance().setCompleteFeed(feed);
        assertEquals(Collections.singletonList("exactly a week"),
                titles("week", byNewest().withDateRange(DateRange.PAST_WEEK)));
    }

    @Test
    public void durationRanges() {
        seed();
        assertEquals(Collections.singletonList("Rocket launch"),
                titles("rocket", byNewest().withDurationRange(DurationRange.UNDER_20_MIN)));
        assertEquals(Arrays.asList("Rocket recap", "Gardening"),
                titles("rocket", byNewest().withDurationRange(DurationRange.FROM_20_TO_60_MIN)));
        assertEquals(Collections.singletonList("Old rocket news"),
                titles("rocket", byNewest().withDurationRange(DurationRange.OVER_60_MIN)));
    }

    @Test
    public void durationBoundsAndUnknownDurations() {
        Feed feed = new Feed("https://example.com/edge.xml", null, "Edge");
        feed.setItems(new ArrayList<>(Arrays.asList(
                item(feed, "clip zero", "", DAY, FeedItem.NEW, 0),
                item(feed, "clip twenty", "", 2 * DAY, FeedItem.NEW, 20 * MINUTE),
                item(feed, "clip sixty", "", 3 * DAY, FeedItem.NEW, 60 * MINUTE),
                item(feed, "clip sixty and a second", "", 4 * DAY, FeedItem.NEW, 60 * MINUTE + 1000),
                item(feed, "clip without media", "", 5 * DAY, FeedItem.NEW, -1))));
        Db.getInstance().setCompleteFeed(feed);

        assertEquals(Collections.emptyList(),
                titles("clip", byNewest().withDurationRange(DurationRange.UNDER_20_MIN)));
        assertEquals(Arrays.asList("clip twenty", "clip sixty"),
                titles("clip", byNewest().withDurationRange(DurationRange.FROM_20_TO_60_MIN)));
        assertEquals(Collections.singletonList("clip sixty and a second"),
                titles("clip", byNewest().withDurationRange(DurationRange.OVER_60_MIN)));
        assertEquals(5, titles("clip", byNewest()).size());
    }

    // ------------------------------------------------------- episode filter

    @Test
    public void statusFilter() {
        seed();
        assertEquals(Collections.singletonList("Gardening"),
                titles("rocket", byNewest().withFilter(filter(FeedItemFilter.PLAYED))));
        assertEquals(Arrays.asList("Rocket recap", "Rocket launch", "Old rocket news", "No media rocket"),
                titles("rocket", byNewest().withFilter(filter(FeedItemFilter.UNPLAYED))));
    }

    @Test
    public void downloadedFilter() {
        seed();
        assertEquals(Collections.singletonList("Rocket launch"),
                titles("rocket", byNewest().withFilter(filter(FeedItemFilter.DOWNLOADED))));
    }

    @Test
    public void favoriteFilter() {
        seed();
        assertEquals(Collections.singletonList("Old rocket news"),
                titles("rocket", byNewest().withFilter(filter(FeedItemFilter.IS_FAVORITE))));
        assertEquals(4, titles("rocket", byNewest().withFilter(filter(FeedItemFilter.NOT_FAVORITE))).size());
    }

    @Test
    public void filtersCombineWithAnd() {
        seed();
        EpisodeSearchOptions options = byNewest()
                .withFeedId(space.getId())
                .withFilter(filter(FeedItemFilter.UNPLAYED))
                .withDateRange(DateRange.PAST_YEAR);
        assertEquals(Arrays.asList("Rocket launch", "Old rocket news"), titles("rocket", options));
        assertEquals(Collections.singletonList("Old rocket news"),
                titles("rocket", options.withDurationRange(DurationRange.OVER_60_MIN)));
        assertEquals(Collections.emptyList(),
                titles("rocket", options.withFields(EnumSet.of(Field.SHOW_NOTES))));
        assertEquals(Collections.singletonList("Rocket launch"), titles("rocket", options
                .withFilter(filter(FeedItemFilter.UNPLAYED, FeedItemFilter.DOWNLOADED, FeedItemFilter.NOT_FAVORITE))));
    }

    // ----------------------------------------------------------------- sort

    @Test
    public void relevancePutsTitleMatchesFirstThenNewest() {
        seed();
        // "Gardening" matches in its notes only, so it comes last although it is newer than two others
        assertEquals(Arrays.asList("Rocket recap", "Rocket launch", "Old rocket news", "No media rocket", "Gardening"),
                titles("rocket", EpisodeSearchOptions.defaults().withSort(Sort.RELEVANCE)));
    }

    @Test
    public void relevanceNeedsEveryWordInTheTitle() {
        seed();
        // only "Old rocket news" holds both words in its title; "Rocket recap" has "news" nowhere
        assertEquals(Collections.singletonList("Old rocket news"),
                titles("rocket news", EpisodeSearchOptions.defaults()));
        // "launch" + "engines": the title holds one word, the notes the other; still found, ranked as notes
        assertEquals(Collections.singletonList("Rocket launch"), titles("launch engines", EpisodeSearchOptions.defaults()));
    }

    @Test
    public void relevanceWithoutTheTitleFieldIsNewestFirst() {
        seed();
        assertEquals(Arrays.asList("Rocket recap", "Gardening"), titles("rocket",
                EpisodeSearchOptions.defaults().withFields(EnumSet.of(Field.SHOW_NOTES))));
    }

    @Test
    public void newestAndOldest() {
        seed();
        assertEquals(Arrays.asList("Rocket recap", "Rocket launch", "Gardening", "Old rocket news", "No media rocket"),
                titles("rocket", byNewest()));
        assertEquals(Arrays.asList("No media rocket", "Old rocket news", "Gardening", "Rocket launch", "Rocket recap"),
                titles("rocket", byNewest().withSort(Sort.OLDEST)));
    }

    @Test
    public void longestAndShortestPutUnknownDurationsLast() {
        seed();
        assertEquals(Arrays.asList("Old rocket news", "Gardening", "Rocket recap", "Rocket launch", "No media rocket"),
                titles("rocket", byNewest().withSort(Sort.LONGEST)));
        assertEquals(Arrays.asList("Rocket launch", "Rocket recap", "Gardening", "Old rocket news", "No media rocket"),
                titles("rocket", byNewest().withSort(Sort.SHORTEST)));
    }

    // ------------------------------------------------------------- escaping

    @Test
    public void percentAndUnderscoreAreLiteral() {
        Feed feed = new Feed("https://example.com/esc.xml", null, "Escapes");
        feed.setItems(new ArrayList<>(Arrays.asList(
                item(feed, "100% pure", "", DAY, FeedItem.NEW, -1),
                item(feed, "100 percent pure", "", 2 * DAY, FeedItem.NEW, -1),
                item(feed, "snake_case talk", "", 3 * DAY, FeedItem.NEW, -1),
                item(feed, "snakeXcase talk", "", 4 * DAY, FeedItem.NEW, -1),
                item(feed, "back\\slash", "", 5 * DAY, FeedItem.NEW, -1),
                item(feed, "O'Brien interview", "", 6 * DAY, FeedItem.NEW, -1))));
        Db.getInstance().setCompleteFeed(feed);

        assertEquals(Collections.singletonList("100% pure"), titles("100%", byNewest()));
        assertEquals(Collections.singletonList("snake_case talk"), titles("snake_case", byNewest()));
        assertEquals(Collections.singletonList("back\\slash"), titles("back\\slash", byNewest()));
        assertEquals(Collections.singletonList("O'Brien interview"), titles("O'Brien", byNewest()));
        assertEquals(Collections.emptyList(), titles("%", byNewest().withFields(EnumSet.of(Field.SHOW_NOTES))));
        assertEquals(Collections.singletonList("100% pure"), titles("%", byNewest().withFields(EnumSet.of(Field.TITLE))));
    }

    @Test
    public void feedSearchEscapesWildcards() {
        Db db = Db.getInstance();
        Feed percent = new Feed("https://example.com/p.xml", null, "100% Talk");
        Feed plain = new Feed("https://example.com/q.xml", null, "100 Talk");
        db.setCompleteFeed(percent, plain);
        db.subscribeFeed(percent.getId());
        db.subscribeFeed(plain.getId());
        try (Cursor cursor = db.searchFeeds("100%")) {
            assertEquals(1, cursor.getCount());
        }
        try (Cursor cursor = db.searchFeeds("talk")) {
            assertEquals(2, cursor.getCount());
        }
    }

    // ---------------------------------------------------------------- count

    @Test
    public void countMatchesTheResults() {
        seed();
        EpisodeSearchOptions[] cases = {
                EpisodeSearchOptions.defaults(),
                byNewest().withFeedId(space.getId()),
                byNewest().withDateRange(DateRange.PAST_MONTH),
                byNewest().withFilter(filter(FeedItemFilter.UNPLAYED)).withDurationRange(DurationRange.FROM_20_TO_60_MIN),
                byNewest().withFields(EnumSet.of(Field.SHOW_NOTES))
        };
        for (EpisodeSearchOptions options : cases) {
            assertEquals(titles("rocket", options).size(),
                    Db.getInstance().countSearchItems("rocket", options, NOW));
        }
    }

    @Test
    public void resultsAndCountStopAtTheLimit() {
        Feed feed = new Feed("https://example.com/many.xml", null, "Many");
        List<FeedItem> items = new ArrayList<>();
        for (int i = 0; i < FeedItemDao.SEARCH_LIMIT + 5; i++) {
            items.add(item(feed, "episode " + i, "", DAY + i, FeedItem.NEW, -1));
        }
        feed.setItems(items);
        Db.getInstance().setCompleteFeed(feed);
        assertEquals(FeedItemDao.SEARCH_LIMIT, titles("episode", byNewest()).size());
        assertEquals(FeedItemDao.SEARCH_LIMIT,
                Db.getInstance().countSearchItems("episode", byNewest(), NOW));
    }

    // -------------------------------------------------------------- helpers

    private static EpisodeSearchOptions byNewest() {
        return EpisodeSearchOptions.defaults().withSort(Sort.NEWEST);
    }

    private static FeedItemFilter filter(String... keys) {
        return new FeedItemFilter(keys);
    }

    private static List<String> titles(String query, EpisodeSearchOptions options) {
        List<String> result = new ArrayList<>();
        try (Cursor cursor = Db.getInstance().searchItems(query, options, NOW)) {
            while (cursor.moveToNext()) {
                result.add(FeedItemCursorMapper.convert(cursor).getTitle());
            }
        }
        return result;
    }

    /** An item published {@code age} ms before {@link #NOW}; {@code duration} < 0 means no media. */
    private static FeedItem item(Feed feed, String title, String description, long age, int state, long duration) {
        FeedItem item = new FeedItem(0, title, "guid-" + feed.getDownload_url() + title,
                "https://example.com/" + title, new Date(NOW - age), state, feed);
        item.setDescriptionIfLonger(description);
        if (duration >= 0) {
            FeedMedia media = new FeedMedia(item, "https://example.com/" + title + ".mp3", 1L, "audio/mpeg");
            media.setDuration((int) duration);
            item.setMedia(media);
        }
        return item;
    }
}
