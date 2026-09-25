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
 * Tests for the raw SQLite layer: schema creation, the upgrade ladder, the cursor mappers and the
 * search queries.
 *
 * <p>Note on the upgrade tests: no version-1, -2 or -3 CREATE statements survive anywhere in the
 * tree — the AntennaPod ladder was dropped when the schema was renumbered. The lowest schema the
 * code can construct is therefore the current one, so the upgrade tests build a current schema,
 * stamp an older {@code user_version} onto it and assert that reopening runs {@code onUpgrade}
 * without error, leaves the rows intact and lands on {@link Db#VERSION}. That is what makes every
 * statement in {@link DBUpgrade} re-runnable against objects that already exist; the 3 -&gt; 4 and
 * 4 -&gt; 5 steps are additionally exercised directly against a database rebuilt in the old layout.</p>
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
    public void freshDatabaseIsAtSchemaVersion5() {
        assertEquals(5, Db.VERSION);
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
                Db.TABLE_NAME_FAVORITES,
                Db.TABLE_NAME_AD_SEGMENTS)));
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
                Db.TABLE_NAME_SIMPLECHAPTERS + "_" + Db.KEY_FEEDITEM,
                Db.TABLE_NAME_AD_SEGMENTS + "_" + Db.KEY_FEEDITEM)));
    }

    // --------------------------------------------------------------- upgrade

    @Test
    public void upgradeFromVersion1ReachesTheCurrentVersionAndKeepsRows() {
        assertUpgradePreservesRows(1);
    }

    @Test
    public void upgradeFromVersion2ReachesTheCurrentVersionAndKeepsRows() {
        assertUpgradePreservesRows(2);
    }

    @Test
    public void upgradeFromVersion3ReachesTheCurrentVersionAndKeepsRows() {
        assertUpgradePreservesRows(3);
    }

    @Test
    public void upgradeFromVersion4ReachesTheCurrentVersionAndKeepsRows() {
        assertUpgradePreservesRows(4);
    }

    /**
     * The real 3 -&gt; 5 path: strip the objects versions 4 and 5 added, then run the ladder and
     * check it puts them back.
     */
    @Test
    public void upgradeFromVersion3AddsTheAdSkipTableAndColumn() {
        SQLiteDatabase db = Db.getInstance().getDb();
        db.execSQL("DROP INDEX " + Db.TABLE_NAME_AD_SEGMENTS + "_" + Db.KEY_FEEDITEM);
        db.execSQL("DROP TABLE " + Db.TABLE_NAME_AD_SEGMENTS);
        // SQLite cannot drop a column on this API level, so the feeds table is rebuilt without it.
        rebuildFeedsTable(db, ")");
        assertFalse(tableNames(db).contains(Db.TABLE_NAME_AD_SEGMENTS));
        assertFalse(columnNames(db, Db.TABLE_NAME_FEEDS).contains(Db.KEY_FEED_AD_SKIP_OVERRIDE));

        DBUpgrade.upgrade(db, 3, Db.VERSION);

        assertTrue(tableNames(db).contains(Db.TABLE_NAME_AD_SEGMENTS));
        assertTrue(columnNames(db, Db.TABLE_NAME_FEEDS).contains(Db.KEY_FEED_AD_SKIP_OVERRIDE));
        assertFalse("a pre-4 database never needs the version-4 column",
                columnNames(db, Db.TABLE_NAME_FEEDS).contains(DbSchema.KEY_FEED_AD_SKIP_V4));

        // Running it again must not fail on the objects it just created.
        DBUpgrade.upgrade(db, 3, Db.VERSION);
        assertTrue(tableNames(db).contains(Db.TABLE_NAME_AD_SEGMENTS));
        assertEquals(0, rowCount(db, Db.TABLE_NAME_AD_SEGMENTS));
    }

    /**
     * The real 4 -&gt; 5 step: the version-4 opt-out column ({@code DEFAULT 1}, 0 = opted out) is
     * mapped onto the override column. 1 meant "follow the global master switch" and becomes
     * NULL; 0 stays an explicit "off".
     */
    @Test
    public void upgradeFromVersion4MapsTheAdSkipOptOutOntoTheOverride() {
        SQLiteDatabase db = Db.getInstance().getDb();
        rebuildFeedsTable(db, "," + DbSchema.KEY_FEED_AD_SKIP_V4 + " INTEGER DEFAULT 1)");
        long optedIn = insertV4Feed(db, "Default", 1);
        long optedOut = insertV4Feed(db, "Opted out", 0);
        db.execSQL("INSERT INTO " + Db.TABLE_NAME_FEEDS + " (" + Db.KEY_TITLE + ") VALUES ('Unset')");
        long unset = lastInsertId(db);

        DBUpgrade.upgrade(db, 4, Db.VERSION);

        assertTrue(columnNames(db, Db.TABLE_NAME_FEEDS).contains(Db.KEY_FEED_AD_SKIP_OVERRIDE));
        assertEquals(null, adSkipOverrideOf(optedIn));
        assertEquals(Boolean.FALSE, adSkipOverrideOf(optedOut));
        assertEquals(null, adSkipOverrideOf(unset));

        // A choice made after the upgrade survives the ladder being run again.
        db.execSQL("UPDATE " + Db.TABLE_NAME_FEEDS + " SET " + Db.KEY_FEED_AD_SKIP_OVERRIDE
                + " = 1 WHERE " + Db.KEY_ID + " = " + optedIn);
        DBUpgrade.upgrade(db, 4, Db.VERSION);
        assertEquals(Boolean.TRUE, adSkipOverrideOf(optedIn));
        assertEquals(Boolean.FALSE, adSkipOverrideOf(optedOut));
    }

    @Test
    public void feedAdSkipOverrideRoundTripsAllThreeStates() {
        Db db = Db.getInstance();
        Feed feed = newFeed("Override feed");
        db.setCompleteFeed(feed);
        assertEquals("a new feed follows the global default", null, mappedAdSkipOverrideOf(feed.getId()));

        feed.getPreferences().setAdSkipOverride(Boolean.TRUE);
        db.setFeedPreferences(feed.getPreferences());
        assertEquals(Boolean.TRUE, mappedAdSkipOverrideOf(feed.getId()));

        feed.getPreferences().setAdSkipOverride(Boolean.FALSE);
        db.setFeedPreferences(feed.getPreferences());
        assertEquals(Boolean.FALSE, mappedAdSkipOverrideOf(feed.getId()));

        feed.getPreferences().setAdSkipOverride(null);
        db.setFeedPreferences(feed.getPreferences());
        assertEquals(null, mappedAdSkipOverrideOf(feed.getId()));
    }

    /** Replaces the feeds table with the current layout minus the override column. */
    private static void rebuildFeedsTable(SQLiteDatabase db, String tail) {
        String current = "," + Db.KEY_FEED_AD_SKIP_OVERRIDE + " INTEGER)";
        assertTrue(DbSchema.CREATE_TABLE_FEEDS.endsWith(current));
        db.execSQL("DROP TABLE " + Db.TABLE_NAME_FEEDS);
        db.execSQL(DbSchema.CREATE_TABLE_FEEDS.replace(current, tail));
    }

    private static long insertV4Feed(SQLiteDatabase db, String title, int adSkip) {
        db.execSQL("INSERT INTO " + Db.TABLE_NAME_FEEDS + " (" + Db.KEY_TITLE + ", "
                + DbSchema.KEY_FEED_AD_SKIP_V4 + ") VALUES ('" + title + "', " + adSkip + ")");
        return lastInsertId(db);
    }

    private static long lastInsertId(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("SELECT last_insert_rowid()", null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(0);
        }
    }

    /** The raw stored override: null for SQL NULL, else whether the INTEGER is non-zero. */
    private static Boolean adSkipOverrideOf(long feedId) {
        try (Cursor cursor = Db.getInstance().getDb().rawQuery("SELECT "
                + Db.KEY_FEED_AD_SKIP_OVERRIDE + " FROM " + Db.TABLE_NAME_FEEDS
                + " WHERE " + Db.KEY_ID + " = " + feedId, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.isNull(0) ? null : cursor.getInt(0) != 0;
        }
    }

    /** Reads a feed's override back through the real cursor mapper. */
    private static Boolean mappedAdSkipOverrideOf(long feedId) {
        try (Cursor cursor = Db.getInstance().getFeedCursor(feedId)) {
            assertTrue(cursor.moveToFirst());
            return FeedCursorMapper.convert(cursor).getPreferences().getAdSkipOverride();
        }
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

    private static List<String> columnNames(SQLiteDatabase db, String table) {
        List<String> columns = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            int nameIndex = cursor.getColumnIndex("name");
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(nameIndex));
            }
        }
        return columns;
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
