package allen.town.podcast.storage.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import allen.town.podcast.model.feed.Feed;
import allen.town.podcast.model.feed.FeedItem;
import allen.town.podcast.model.feed.FeedFilter;
import allen.town.podcast.model.feed.FeedItemFilter;
import allen.town.podcast.model.feed.FeedMedia;
import allen.town.podcast.model.feed.FeedPreferences;
import allen.town.podcast.model.feed.VolumeAdaptionSetting;
import allen.town.podcast.storage.db.mapper.FeedCursorMapper;
import allen.town.podcast.storage.db.mapper.FeedItemCursorMapper;
import allen.town.podcast.storage.db.mapper.FeedMediaCursorMapper;

/**
 * Tests for the raw SQLite layer: schema creation, the (currently empty) upgrade ladder,
 * the cursor mappers and the search queries.
 *
 * <p>Note on the upgrade tests: {@link DBUpgrade#upgrade} is a no-op in this fork — the
 * AntennaPod ALTER ladder was dropped when the schema was renumbered, and no version-1 or
 * version-2 CREATE statements survive anywhere in the tree. The lowest schema the code can
 * construct is therefore the current one, so the upgrade tests build a version-3 schema,
 * stamp an older {@code user_version} onto it and assert that reopening runs
 * {@code onUpgrade} without error, leaves the rows intact and lands on {@link Db#VERSION}.
 * That is exactly what a real 1 -&gt; 3 or 2 -&gt; 3 upgrade does today.</p>
 */
@RunWith(RobolectricTestRunner.class)
public class DbTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        Db.init(context);
    }

    @After
    public void tearDown() {
        Db.tearDownTests();
        context.deleteDatabase(Db.DATABASE_NAME);
    }

    // ---------------------------------------------------------------- schema

    @Test
    public void freshDatabaseIsAtSchemaVersion3() {
        assertEquals(3, Db.VERSION);
        assertEquals(Db.VERSION, Db.getInstance().getDb().getVersion());
    }

    @Test
    public void freshDatabaseHasEveryExpectedTable() {
        List<String> tables = tableNames(Db.getInstance().getDb());
        assertTrue(tables.toString(), tables.containsAll(Arrays.asList(
                Db.TABLE_NAME_FEEDS,
                Db.TABLE_NAME_FEED_ITEMS,
                Db.TABLE_NAME_FEED_MEDIA,
                Db.TABLE_NAME_DOWNLOAD_LOG,
                Db.TABLE_NAME_QUEUE,
                Db.TABLE_NAME_SIMPLECHAPTERS,
                Db.TABLE_NAME_FAVORITES)));
    }

    @Test
    public void freshDatabaseHasEveryExpectedIndex() {
        List<String> indexes = new ArrayList<>();
        try (Cursor cursor = Db.getInstance().getDb().rawQuery(
                "SELECT name FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%'", null)) {
            while (cursor.moveToNext()) {
                indexes.add(cursor.getString(0));
            }
        }
        assertTrue(indexes.toString(), indexes.containsAll(Arrays.asList(
                Db.TABLE_NAME_FEED_ITEMS + "_" + Db.KEY_FEED,
                Db.TABLE_NAME_FEED_ITEMS + "_" + Db.KEY_PUBDATE,
                Db.TABLE_NAME_FEED_ITEMS + "_" + Db.KEY_READ,
                Db.TABLE_NAME_QUEUE + "_" + Db.KEY_FEEDITEM,
                Db.TABLE_NAME_FEED_MEDIA + "_" + Db.KEY_FEEDITEM,
                Db.TABLE_NAME_SIMPLECHAPTERS + "_" + Db.KEY_FEEDITEM)));
    }

    // --------------------------------------------------------------- upgrade

    @Test
    public void upgradeFromVersion1ReachesVersion3AndKeepsRows() {
        assertUpgradePreservesRows(1);
    }

    @Test
    public void upgradeFromVersion2ReachesVersion3AndKeepsRows() {
        assertUpgradePreservesRows(2);
    }

    private void assertUpgradePreservesRows(int oldVersion) {
        Feed feed = newFeed("Kept across the upgrade");
        feed.setItems(new ArrayList<>(Arrays.asList(newItem(feed, "Episode kept", "body"))));
        Db.getInstance().setCompleteFeed(feed);
        long feedId = feed.getId();
        assertTrue(feedId > 0);

        stampSchemaVersion(oldVersion);

        // Reopening runs DbHelper.onUpgrade(oldVersion, 3).
        Db reopened = Db.getInstance();
        assertEquals(Db.VERSION, reopened.getDb().getVersion());

        try (Cursor cursor = reopened.getFeedCursor(feedId)) {
            assertTrue("feed row was lost by the upgrade", cursor.moveToFirst());
            assertEquals("Kept across the upgrade", FeedCursorMapper.convert(cursor).getFeedTitle());
        }
        Feed reloaded = new Feed(null, null);
        reloaded.setId(feedId);
        try (Cursor cursor = reopened.getItemsOfFeedCursor(reloaded, FeedItemFilter.unfiltered())) {
            assertEquals("item rows were lost by the upgrade", 1, cursor.getCount());
        }
    }

    /** Closes the database, rewrites its {@code user_version} and drops the cached instance. */
    private void stampSchemaVersion(int version) {
        Db.tearDownTests();
        File path = context.getDatabasePath(Db.DATABASE_NAME);
        SQLiteDatabase raw = SQLiteDatabase.openDatabase(
                path.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
        raw.setVersion(version);
        assertEquals(version, raw.getVersion());
        raw.close();
    }

    // ----------------------------------------------------------- round trips

    @Test
    public void feedItemAndMediaRoundTripThroughTheCursorMappers() {
        Feed feed = newFeed("Round trip feed");
        FeedItem item = newItem(feed, "Round trip episode", "Episode description");
        FeedMedia media = new FeedMedia(item, "https://example.com/ep1.mp3", 12345L, "audio/mpeg");
        media.setDuration(60000);
        media.setPosition(1234);
        item.setMedia(media);
        feed.setItems(new ArrayList<>(Arrays.asList(item)));

        Db db = Db.getInstance();
        db.setCompleteFeed(feed);
        assertTrue(item.getId() > 0);
        assertTrue(media.getId() > 0);

        try (Cursor cursor = db.getFeedCursor(feed.getId())) {
            assertTrue(cursor.moveToFirst());
            Feed read = FeedCursorMapper.convert(cursor);
            assertEquals(feed.getId(), read.getId());
            assertEquals("Round trip feed", read.getFeedTitle());
            assertEquals("https://example.com/feed.xml", read.getDownload_url());
            assertEquals("Feed description", read.getDescription());
            assertNotNull(read.getPreferences());
            assertEquals("user", read.getPreferences().getUsername());
        }

        try (Cursor cursor = db.getItemsOfFeedCursor(feed, FeedItemFilter.unfiltered())) {
            assertTrue(cursor.moveToFirst());
            FeedItem readItem = FeedItemCursorMapper.convert(cursor);
            assertEquals(item.getId(), readItem.getId());
            assertEquals("Round trip episode", readItem.getTitle());
            assertEquals(feed.getId(), readItem.getFeedId());

            FeedMedia readMedia = FeedMediaCursorMapper.convert(cursor);
            assertEquals(media.getId(), readMedia.getId());
            assertEquals("https://example.com/ep1.mp3", readMedia.getDownload_url());
            assertEquals(12345L, readMedia.getSize());
            assertEquals("audio/mpeg", readMedia.getMime_type());
            assertEquals(60000, readMedia.getDuration());
            assertEquals(1234, readMedia.getPosition());
            assertFalse(readMedia.isDownloaded());
        }
    }

    @Test
    public void descriptionIsStoredWithTheItem() {
        Feed feed = newFeed("Description feed");
        FeedItem item = newItem(feed, "Episode", "The long form show notes");
        feed.setItems(new ArrayList<>(Arrays.asList(item)));
        Db db = Db.getInstance();
        db.setCompleteFeed(feed);

        try (Cursor cursor = db.getDescriptionOfItem(item)) {
            assertTrue(cursor.moveToFirst());
            assertEquals("The long form show notes", cursor.getString(0));
        }
    }

    // ------------------------------------------------------------ search

    @Test
    public void searchItemsFindsByTitle() {
        Db db = Db.getInstance();
        Feed feed = seedSearchableFeed(db);

        try (Cursor cursor = db.searchItems(0, "Rocket")) {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals("Rocket science", FeedItemCursorMapper.convert(cursor).getTitle());
        }
        try (Cursor cursor = db.searchItems(feed.getId(), "Rocket")) {
            assertEquals(1, cursor.getCount());
        }
    }

    @Test
    public void searchItemsFindsByDescription() {
        Db db = Db.getInstance();
        seedSearchableFeed(db);

        try (Cursor cursor = db.searchItems(0, "turbopump")) {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals("Rocket science", FeedItemCursorMapper.convert(cursor).getTitle());
        }
    }

    @Test
    public void searchItemsIgnoresOtherFeedsWhenGivenAFeedId() {
        Db db = Db.getInstance();
        seedSearchableFeed(db);

        Feed other = newFeed("Other feed");
        other.setDownload_url("https://example.com/other.xml");
        other.setItems(new ArrayList<>(Arrays.asList(newItem(other, "Rocket science", "copy"))));
        db.setCompleteFeed(other);

        try (Cursor cursor = db.searchItems(0, "Rocket")) {
            assertEquals(2, cursor.getCount());
        }
        try (Cursor cursor = db.searchItems(other.getId(), "Rocket")) {
            assertEquals(1, cursor.getCount());
        }
    }

    @Test
    public void searchItemsFindsNothingForAnUnknownWord() {
        Db db = Db.getInstance();
        seedSearchableFeed(db);
        try (Cursor cursor = db.searchItems(0, "definitelyabsent")) {
            assertEquals(0, cursor.getCount());
        }
    }

    @Test
    public void searchItemsEscapesQuotes() {
        Db db = Db.getInstance();
        seedSearchableFeed(db);
        // Must not blow up with a syntax error; the escaped quote simply matches nothing.
        try (Cursor cursor = db.searchItems(0, "O'Brien")) {
            assertEquals(0, cursor.getCount());
        }
    }

    @Test
    public void searchFeedsOnlyReturnsSubscribedFeeds() {
        Db db = Db.getInstance();
        Feed feed = seedSearchableFeed(db);

        try (Cursor cursor = db.searchFeeds("Searchable")) {
            assertEquals("unsubscribed feeds must not be found", 0, cursor.getCount());
        }
        db.subscribeFeed(feed.getId());
        try (Cursor cursor = db.searchFeeds("Searchable")) {
            assertEquals(1, cursor.getCount());
        }
    }

    private Feed seedSearchableFeed(Db db) {
        Feed feed = newFeed("Searchable feed");
        List<FeedItem> items = new ArrayList<>(Arrays.asList(
                newItem(feed, "Rocket science", "All about the turbopump"),
                newItem(feed, "Gardening hour", "Tomatoes and compost")));
        feed.setItems(items);
        db.setCompleteFeed(feed);
        return feed;
    }

    // ------------------------------------------------- subscribe / unsubscribe

    @Test
    public void feedsStartUnsubscribed() {
        Db db = Db.getInstance();
        Feed feed = newFeed("Not yet subscribed");
        db.setCompleteFeed(feed);

        try (Cursor cursor = db.getSubscribedFeedsCursor()) {
            assertEquals(0, cursor.getCount());
        }
        try (Cursor cursor = db.getAllFeedsCursor()) {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertFalse(FeedCursorMapper.convert(cursor).isSubscribed());
        }
    }

    @Test
    public void subscribeFlagRoundTrips() {
        Db db = Db.getInstance();
        Feed feed = newFeed("Subscribable");
        db.setCompleteFeed(feed);

        db.subscribeFeed(feed.getId());
        try (Cursor cursor = db.getSubscribedFeedsCursor()) {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertTrue(FeedCursorMapper.convert(cursor).isSubscribed());
        }
        try (Cursor cursor = db.getSubFeedCursorDownloadUrls()) {
            assertTrue(cursor.moveToFirst());
            assertEquals("https://example.com/feed.xml", cursor.getString(1));
        }

        // Writing the feed back with the flag cleared unsubscribes it again.
        feed.setSubscribed(false);
        db.setCompleteFeed(feed);
        try (Cursor cursor = db.getSubscribedFeedsCursor()) {
            assertEquals(0, cursor.getCount());
        }
        try (Cursor cursor = db.getFeedsNotSubAndNotInPlaylistAndFavCursor()) {
            assertEquals(1, cursor.getCount());
        }
    }

    @Test
    public void unsubscribedFeedsWithoutQueueOrFavouriteEntriesAreRemovable() {
        Db db = Db.getInstance();
        Feed kept = newFeed("Kept");
        kept.setSubscribed(true);
        Feed dropped = newFeed("Dropped");
        dropped.setDownload_url("https://example.com/dropped.xml");
        db.setCompleteFeed(kept, dropped);

        db.removeFeedsNotSubAndNotInPlaylistAndFav();

        try (Cursor cursor = db.getAllFeedsCursor()) {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals("Kept", FeedCursorMapper.convert(cursor).getFeedTitle());
        }
    }

    @Test
    public void itunesIdAndCustomTitleRoundTrip() {
        Db db = Db.getInstance();
        Feed feed = newFeed("Original title");
        db.setCompleteFeed(feed);

        db.setFeedItunesId(feed.getId(), "id1234");
        db.setFeedCustomTitle(feed.getId(), "My own name");

        try (Cursor cursor = db.getFeedCursor(feed.getId())) {
            assertTrue(cursor.moveToFirst());
            Feed read = FeedCursorMapper.convert(cursor);
            assertEquals("id1234", read.getItunesId());
            assertEquals("My own name", read.getCustomTitle());
            assertEquals("My own name", read.getTitle());
        }
        try (Cursor cursor = db.getFeedCursorByItunesFeedId("id1234")) {
            assertEquals(1, cursor.getCount());
        }
    }

    @Test
    public void removingAFeedRemovesItsItemsAndMedia() {
        Feed feed = newFeed("Doomed");
        FeedItem item = newItem(feed, "Doomed episode", "notes");
        item.setMedia(new FeedMedia(item, "https://example.com/doomed.mp3", 1L, "audio/mpeg"));
        feed.setItems(new ArrayList<>(Arrays.asList(item)));

        Db db = Db.getInstance();
        db.setCompleteFeed(feed);
        db.removeFeed(feed);

        try (Cursor cursor = db.getAllFeedsCursor()) {
            assertEquals(0, cursor.getCount());
        }
        assertEquals(0, rowCount(db.getDb(), Db.TABLE_NAME_FEED_ITEMS));
        assertEquals(0, rowCount(db.getDb(), Db.TABLE_NAME_FEED_MEDIA));
    }

    @Test
    public void queueRoundTripsInOrderAndClears() {
        Feed feed = newFeed("Queue feed");
        FeedItem first = newItem(feed, "First", "a");
        FeedItem second = newItem(feed, "Second", "b");
        feed.setItems(new ArrayList<>(Arrays.asList(first, second)));

        Db db = Db.getInstance();
        db.setCompleteFeed(feed);
        db.setQueue(Arrays.asList(second, first));

        assertEquals(2, db.getQueueSize());
        List<Long> ids = new ArrayList<>();
        try (Cursor cursor = db.getQueueIDCursor()) {
            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(0));
            }
        }
        assertEquals(Arrays.asList(second.getId(), first.getId()), ids);

        db.clearQueue();
        assertEquals(0, db.getQueueSize());
    }

    @Test
    public void feedPreferencesRoundTrip() {
        Db db = Db.getInstance();
        Feed feed = newFeed("Prefs feed");
        db.setCompleteFeed(feed);

        FeedPreferences prefs = feed.getPreferences();
        prefs.setKeepUpdated(false);
        prefs.setFeedPlaybackSpeed(1.5f);
        prefs.setFeedSkipIntro(7);
        prefs.setFeedSkipEnding(11);
        db.setFeedPreferences(prefs);

        try (Cursor cursor = db.getFeedCursor(feed.getId())) {
            assertTrue(cursor.moveToFirst());
            FeedPreferences read = FeedCursorMapper.convert(cursor).getPreferences();
            assertFalse(read.getKeepUpdated());
            assertEquals(1.5f, read.getFeedPlaybackSpeed(), 0.001f);
            assertEquals(7, read.getFeedSkipIntro());
            assertEquals(11, read.getFeedSkipEnding());
            assertEquals("user", read.getUsername());
            assertEquals("pass", read.getPassword());
        }
    }

    @Test
    public void readStateRoundTrips() {
        Feed feed = newFeed("Read state feed");
        FeedItem item = newItem(feed, "Episode", "notes");
        feed.setItems(new ArrayList<>(Arrays.asList(item)));

        Db db = Db.getInstance();
        db.setCompleteFeed(feed);
        assertEquals(1, db.getNumberOfNewItems());

        db.setFeedItemRead(FeedItem.PLAYED, item.getId());
        assertEquals(0, db.getNumberOfNewItems());

        try (Cursor cursor = db.getItemsOfFeedCursor(feed, FeedItemFilter.unfiltered())) {
            assertTrue(cursor.moveToFirst());
            assertTrue(FeedItemCursorMapper.convert(cursor).isPlayed());
        }
    }

    @Test
    public void deleteDatabaseEmptiesEveryTableButKeepsTheSchema() {
        Feed feed = newFeed("Wiped");
        feed.setItems(new ArrayList<>(Arrays.asList(newItem(feed, "Episode", "notes"))));
        Db db = Db.getInstance();
        db.setCompleteFeed(feed);

        assertTrue(Db.deleteDatabase());

        assertEquals(0, rowCount(db.getDb(), Db.TABLE_NAME_FEEDS));
        assertEquals(0, rowCount(db.getDb(), Db.TABLE_NAME_FEED_ITEMS));
        assertTrue(tableNames(db.getDb()).contains(Db.TABLE_NAME_FEEDS));
    }

    @Test
    public void getInstanceWithoutInitThrows() {
        Db.tearDownTests();
        resetStaticContext();
        try {
            Db.getInstance();
            throw new AssertionError("expected an IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Db.init"));
        } finally {
            Db.init(context);
        }
    }

    @Test
    public void initRejectsANullContext() {
        try {
            Db.init(null);
            throw new AssertionError("expected an IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    // --------------------------------------------------------------- helpers

    /** Clears the package-private static context so {@link Db#getInstance()} fails again. */
    private static void resetStaticContext() {
        try {
            java.lang.reflect.Field field = Db.class.getDeclaredField("context");
            field.setAccessible(true);
            field.set(null, null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static List<String> tableNames(SQLiteDatabase db) {
        List<String> tables = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'", null)) {
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0));
            }
        }
        return tables;
    }

    private static int rowCount(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery("SELECT count(*) FROM " + table, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private static Feed newFeed(String title) {
        Feed feed = new Feed("https://example.com/feed.xml", null, title);
        feed.setDescription("Feed description");
        feed.setAuthor("An author");
        feed.setImageUrl("https://example.com/cover.png");
        feed.setPreferences(new FeedPreferences(0, true, true,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                "user", "pass", new FeedFilter(), FeedPreferences.SPEED_USE_GLOBAL,
                0, 0, false, new HashSet<>(), false, false, false, false));
        return feed;
    }

    private static FeedItem newItem(Feed feed, String title, String description) {
        FeedItem item = new FeedItem(0, title, "guid-" + title, "https://example.com/" + title,
                new Date(1_600_000_000_000L), FeedItem.NEW, feed);
        item.setDescriptionIfLonger(description);
        return item;
    }

    /** Guards against the query silently matching something for an absent row. */
    @Test
    public void unknownFeedIdYieldsAnEmptyCursor() {
        try (Cursor cursor = Db.getInstance().getFeedCursor(4242L)) {
            assertFalse(cursor.moveToFirst());
        }
    }
}
