package dev.russell.focuspodcast.storage.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import dev.russell.focuspodcast.model.feed.Feed;
import dev.russell.focuspodcast.model.feed.FeedItem;
import dev.russell.focuspodcast.model.feed.FeedItemFilter;
import dev.russell.focuspodcast.storage.db.mapper.FeedItemCursorMapper;

/**
 * Transcripts in the database (version 9): the URL and type on the item row, the cache table,
 * the 8 -&gt; 9 upgrade step and the delete cascade.
 */
@RunWith(RobolectricTestRunner.class)
public class TranscriptStorageTest {

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

    @Test
    public void transcriptUrlAndTypeRoundTripThroughTheItemMapper() {
        Feed feed = feedWith("With transcript", "Without transcript");
        feed.getItems().get(0).setTranscript("https://example.com/ep1.json", "application/json");
        Db.getInstance().setCompleteFeed(feed);

        List<FeedItem> read = readItems(feed);
        FeedItem with = byTitle(read, "With transcript");
        assertEquals("https://example.com/ep1.json", with.getTranscriptUrl());
        assertEquals("application/json", with.getTranscriptType());
        assertTrue(with.hasTranscript());
        FeedItem without = byTitle(read, "Without transcript");
        assertNull(without.getTranscriptUrl());
        assertFalse(without.hasTranscript());
    }

    @Test
    public void refreshKeepsTheTranscriptWhenTheFeedStopsListingItAndTakesANewOne() {
        FeedItem stored = new FeedItem();
        stored.setTranscript("https://example.com/old.vtt", "text/vtt");
        stored.updateFromOther(new FeedItem());
        assertEquals("https://example.com/old.vtt", stored.getTranscriptUrl());

        FeedItem refreshed = new FeedItem();
        refreshed.setTranscript("https://example.com/new.json", "application/json");
        stored.updateFromOther(refreshed);
        assertEquals("https://example.com/new.json", stored.getTranscriptUrl());
        assertEquals("application/json", stored.getTranscriptType());
    }

    @Test
    public void cacheRoundTripsAndIsReplacedWhole() {
        Feed feed = feedWith("Episode");
        Db db = Db.getInstance();
        db.setCompleteFeed(feed);
        long itemId = feed.getItems().get(0).getId();

        try (Cursor cursor = db.getTranscriptCursor(itemId)) {
            assertFalse("nothing cached yet", cursor.moveToFirst());
        }
        db.setTranscript(itemId, "https://example.com/a.vtt", "text/vtt", "WEBVTT raw", "plain words", 1000L);
        db.setTranscript(itemId, "https://example.com/b.srt", "application/x-subrip", "SRT raw", "other words", 2000L);

        try (Cursor cursor = db.getTranscriptCursor(itemId)) {
            assertEquals("one row per episode", 1, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals("https://example.com/b.srt", cursor.getString(cursor.getColumnIndexOrThrow(Db.KEY_TRANSCRIPT_URL)));
            assertEquals("application/x-subrip", cursor.getString(cursor.getColumnIndexOrThrow(Db.KEY_TRANSCRIPT_TYPE)));
            assertEquals("SRT raw", cursor.getString(cursor.getColumnIndexOrThrow(Db.KEY_TRANSCRIPT_RAW)));
            assertEquals("other words", cursor.getString(cursor.getColumnIndexOrThrow(Db.KEY_TRANSCRIPT_TEXT)));
            assertEquals(2000L, cursor.getLong(cursor.getColumnIndexOrThrow(Db.KEY_TRANSCRIPT_FETCHED_AT)));
        }
    }

    @Test
    public void removingAFeedRemovesItsCachedTranscripts() {
        Feed feed = feedWith("Gone");
        Feed kept = feedWith("Kept");
        Db db = Db.getInstance();
        db.setCompleteFeed(feed, kept);
        db.setTranscript(feed.getItems().get(0).getId(), null, null, "raw", "text", 1L);
        db.setTranscript(kept.getItems().get(0).getId(), null, null, "raw", "text", 1L);

        db.removeFeed(feed);

        assertEquals(1, rowCount(db.getDb(), Db.TABLE_NAME_TRANSCRIPTS));
        try (Cursor cursor = db.getTranscriptCursor(kept.getItems().get(0).getId())) {
            assertTrue(cursor.moveToFirst());
        }
    }

    @Test
    public void deleteDatabaseEmptiesTheTranscriptCache() {
        Feed feed = feedWith("Episode");
        Db db = Db.getInstance();
        db.setCompleteFeed(feed);
        db.setTranscript(feed.getItems().get(0).getId(), null, null, "raw", "text", 1L);

        Db.deleteDatabase();

        assertEquals(0, rowCount(db.getDb(), Db.TABLE_NAME_TRANSCRIPTS));
    }

    /**
     * The real 8 -&gt; 9 step: rebuild the items table without the transcript columns and drop
     * the cache table, run the ladder, and check both come back with old rows intact.
     */
    @Test
    public void upgradeFromVersion8AddsTheTranscriptColumnsAndTable() {
        SQLiteDatabase db = Db.getInstance().getDb();
        String transcriptColumns = "," + DbSchema.KEY_TRANSCRIPT_URL + " TEXT,"
                + DbSchema.KEY_TRANSCRIPT_TYPE + " TEXT,";
        assertTrue(DbSchema.CREATE_TABLE_FEED_ITEMS.contains(transcriptColumns));
        db.execSQL("DROP TABLE " + Db.TABLE_NAME_FEED_ITEMS);
        db.execSQL(DbSchema.CREATE_TABLE_FEED_ITEMS.replace(transcriptColumns, ","));
        db.execSQL("DROP TABLE " + Db.TABLE_NAME_TRANSCRIPTS);
        db.execSQL("INSERT INTO " + Db.TABLE_NAME_FEED_ITEMS + " (" + Db.KEY_TITLE + ") VALUES ('Old item')");
        assertFalse(columnNames(db, Db.TABLE_NAME_FEED_ITEMS).contains(Db.KEY_TRANSCRIPT_URL));

        DBUpgrade.upgrade(db, 8, Db.VERSION);

        List<String> columns = columnNames(db, Db.TABLE_NAME_FEED_ITEMS);
        assertTrue(columns.contains(Db.KEY_TRANSCRIPT_URL));
        assertTrue(columns.contains(Db.KEY_TRANSCRIPT_TYPE));
        assertTrue(columnNames(db, Db.TABLE_NAME_TRANSCRIPTS).contains(Db.KEY_TRANSCRIPT_TEXT));
        try (Cursor cursor = db.rawQuery("SELECT " + Db.KEY_TITLE + ", " + Db.KEY_TRANSCRIPT_URL
                + " FROM " + Db.TABLE_NAME_FEED_ITEMS, null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals("Old item", cursor.getString(0));
            assertTrue(cursor.isNull(1));
        }

        // Running it again must not fail on what it just added.
        DBUpgrade.upgrade(db, 8, Db.VERSION);
        assertEquals(1, rowCount(db, Db.TABLE_NAME_FEED_ITEMS));
    }

    @Test
    public void reopeningAVersion8DatabaseReachesTheCurrentVersion() {
        Feed feed = feedWith("Kept");
        Db.getInstance().setCompleteFeed(feed);
        Db.tearDownTests();
        Context context = ApplicationProvider.getApplicationContext();
        SQLiteDatabase raw = SQLiteDatabase.openDatabase(
                context.getDatabasePath(Db.DATABASE_NAME).getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
        raw.setVersion(8);
        raw.close();

        Db reopened = Db.getInstance();
        assertEquals(Db.VERSION, reopened.getDb().getVersion());
        assertEquals(1, readItems(feed).size());
    }

    // --------------------------------------------------------------- helpers

    private static Feed feedWith(String... titles) {
        Feed feed = new Feed("https://example.com/" + titles[0] + ".xml", null, titles[0]);
        List<FeedItem> items = new ArrayList<>();
        for (String title : titles) {
            items.add(new FeedItem(0, title, "guid-" + title, "https://example.com/" + title,
                    new Date(1_600_000_000_000L), FeedItem.UNPLAYED, feed));
        }
        feed.setItems(items);
        return feed;
    }

    private static List<FeedItem> readItems(Feed feed) {
        List<FeedItem> items = new ArrayList<>();
        try (Cursor cursor = Db.getInstance().getItemsOfFeedCursor(feed, FeedItemFilter.unfiltered())) {
            while (cursor.moveToNext()) {
                items.add(FeedItemCursorMapper.convert(cursor));
            }
        }
        return items;
    }

    private static FeedItem byTitle(List<FeedItem> items, String title) {
        for (FeedItem item : items) {
            if (title.equals(item.getTitle())) {
                return item;
            }
        }
        throw new AssertionError("no item " + title + " in " + Arrays.toString(items.toArray()));
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

    private static int rowCount(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery("SELECT count(*) FROM " + table, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }
}
