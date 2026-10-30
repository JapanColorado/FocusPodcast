package dev.russell.yagi.storage.db;

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
import java.util.Collections;
import java.util.Date;
import java.util.List;

import dev.russell.yagi.model.feed.Bookmark;
import dev.russell.yagi.model.feed.Feed;
import dev.russell.yagi.model.feed.FeedItem;
import dev.russell.yagi.storage.db.mapper.BookmarkCursorMapper;

/**
 * Bookmarks in the database (version 10): the DAO round trip, the two orderings, the delete
 * cascade with their episode, and the 9 -&gt; 10 upgrade step.
 */
@RunWith(RobolectricTestRunner.class)
public class BookmarkStorageTest {

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
    public void insertSetsTheIdAndRoundTripsEveryField() {
        long itemId = storeFeed("Show", "Episode").getItems().get(0).getId();
        Db db = Db.getInstance();
        Bookmark bookmark = new Bookmark(itemId, 754_000L, "a note", 1_000L);

        long id = db.insertBookmark(bookmark);

        assertTrue(id > 0);
        assertEquals(id, bookmark.getId());
        List<Bookmark> read = bookmarksOf(itemId);
        assertEquals(1, read.size());
        Bookmark stored = read.get(0);
        assertEquals(id, stored.getId());
        assertEquals(itemId, stored.getFeedItemId());
        assertEquals(754_000L, stored.getPositionMs());
        assertEquals("a note", stored.getNote());
        assertEquals(1_000L, stored.getCreatedAt());
    }

    @Test
    public void bookmarkWithoutNoteReadsNull() {
        long itemId = storeFeed("Show", "Episode").getItems().get(0).getId();
        Db.getInstance().insertBookmark(new Bookmark(itemId, 5_000L, null, 1L));

        Bookmark stored = bookmarksOf(itemId).get(0);
        assertNull(stored.getNote());
        assertFalse(stored.hasNote());
    }

    @Test
    public void editingTheNoteChangesOnlyThatBookmark() {
        long itemId = storeFeed("Show", "Episode").getItems().get(0).getId();
        Db db = Db.getInstance();
        Bookmark first = new Bookmark(itemId, 1_000L, "first", 1L);
        Bookmark second = new Bookmark(itemId, 2_000L, "second", 2L);
        db.insertBookmark(first);
        db.insertBookmark(second);

        db.setBookmarkNote(first.getId(), "edited");
        db.setBookmarkNote(second.getId(), null);

        List<Bookmark> read = bookmarksOf(itemId);
        assertEquals("edited", read.get(0).getNote());
        assertNull(read.get(1).getNote());
    }

    @Test
    public void deleteRemovesOneBookmarkAndReinsertingRestoresItsId() {
        long itemId = storeFeed("Show", "Episode").getItems().get(0).getId();
        Db db = Db.getInstance();
        Bookmark gone = new Bookmark(itemId, 1_000L, "gone", 1L);
        Bookmark kept = new Bookmark(itemId, 2_000L, "kept", 2L);
        db.insertBookmark(gone);
        db.insertBookmark(kept);
        long goneId = gone.getId();

        db.deleteBookmark(goneId);
        List<Bookmark> read = bookmarksOf(itemId);
        assertEquals(1, read.size());
        assertEquals("kept", read.get(0).getNote());

        // the undo of a delete stores the same object again
        db.insertBookmark(gone);
        assertEquals(goneId, gone.getId());
        assertEquals(2, bookmarksOf(itemId).size());
    }

    @Test
    public void bookmarksOfAnEpisodeComeInPositionOrder() {
        Feed feed = storeFeed("Show", "Episode", "Other");
        long itemId = feed.getItems().get(0).getId();
        long otherId = feed.getItems().get(1).getId();
        Db db = Db.getInstance();
        db.insertBookmark(new Bookmark(itemId, 30_000L, "c", 1L));
        db.insertBookmark(new Bookmark(itemId, 10_000L, "a", 2L));
        db.insertBookmark(new Bookmark(otherId, 15_000L, "other", 3L));
        db.insertBookmark(new Bookmark(itemId, 20_000L, "b", 4L));

        List<String> notes = new ArrayList<>();
        for (Bookmark bookmark : bookmarksOf(itemId)) {
            notes.add(bookmark.getNote());
        }
        assertEquals(Arrays.asList("a", "b", "c"), notes);
    }

    @Test
    public void allBookmarksComeNewestFirst() {
        Feed feed = storeFeed("Show", "Episode", "Other");
        long itemId = feed.getItems().get(0).getId();
        long otherId = feed.getItems().get(1).getId();
        Db db = Db.getInstance();
        db.insertBookmark(new Bookmark(itemId, 1_000L, "old", 100L));
        db.insertBookmark(new Bookmark(otherId, 1_000L, "newest", 300L));
        db.insertBookmark(new Bookmark(itemId, 5_000L, "middle", 200L));

        List<String> notes = new ArrayList<>();
        try (Cursor cursor = db.getAllBookmarksCursor()) {
            while (cursor.moveToNext()) {
                notes.add(BookmarkCursorMapper.convert(cursor).getNote());
            }
        }
        assertEquals(Arrays.asList("newest", "middle", "old"), notes);
    }

    @Test
    public void removingEpisodesRemovesTheirBookmarks() {
        Feed feed = storeFeed("Show", "Gone", "Kept");
        FeedItem gone = feed.getItems().get(0);
        FeedItem kept = feed.getItems().get(1);
        Db db = Db.getInstance();
        db.insertBookmark(new Bookmark(gone.getId(), 1_000L, null, 1L));
        db.insertBookmark(new Bookmark(gone.getId(), 2_000L, null, 2L));
        db.insertBookmark(new Bookmark(kept.getId(), 3_000L, null, 3L));

        db.removeFeedItems(Collections.singletonList(gone));

        assertEquals(0, bookmarksOf(gone.getId()).size());
        assertEquals(1, bookmarksOf(kept.getId()).size());
        assertEquals(1, rowCount(db.getDb(), Db.TABLE_NAME_BOOKMARKS));
    }

    @Test
    public void removingAFeedRemovesItsBookmarks() {
        Feed gone = storeFeed("Gone show", "Episode");
        Feed kept = storeFeed("Kept show", "Episode");
        Db db = Db.getInstance();
        db.insertBookmark(new Bookmark(gone.getItems().get(0).getId(), 1_000L, null, 1L));
        db.insertBookmark(new Bookmark(kept.getItems().get(0).getId(), 1_000L, null, 1L));

        db.removeFeed(gone);

        assertEquals(1, rowCount(db.getDb(), Db.TABLE_NAME_BOOKMARKS));
        assertEquals(1, bookmarksOf(kept.getItems().get(0).getId()).size());
    }

    @Test
    public void deleteDatabaseEmptiesTheBookmarks() {
        long itemId = storeFeed("Show", "Episode").getItems().get(0).getId();
        Db db = Db.getInstance();
        db.insertBookmark(new Bookmark(itemId, 1_000L, null, 1L));

        Db.deleteDatabase();

        assertEquals(0, rowCount(db.getDb(), Db.TABLE_NAME_BOOKMARKS));
    }

    /** The real 9 -&gt; 10 step: drop the table and its index, run the ladder, check both are back. */
    @Test
    public void upgradeFromVersion9AddsTheBookmarksTable() {
        SQLiteDatabase db = Db.getInstance().getDb();
        db.execSQL("DROP TABLE " + Db.TABLE_NAME_BOOKMARKS);
        assertFalse(tableNames(db).contains(Db.TABLE_NAME_BOOKMARKS));

        DBUpgrade.upgrade(db, 9, Db.VERSION);

        assertTrue(tableNames(db).contains(Db.TABLE_NAME_BOOKMARKS));
        List<String> columns = columnNames(db, Db.TABLE_NAME_BOOKMARKS);
        assertTrue(columns.contains(Db.KEY_FEEDITEM));
        assertTrue(columns.contains(Db.KEY_BOOKMARK_POSITION_MS));
        assertTrue(columns.contains(Db.KEY_BOOKMARK_NOTE));
        assertTrue(columns.contains(Db.KEY_BOOKMARK_CREATED_AT));
        assertTrue(indexNames(db).contains(Db.TABLE_NAME_BOOKMARKS + "_" + Db.KEY_FEEDITEM));

        // running it again must not fail on what it just created, nor touch the rows
        long itemId = storeFeed("Show", "Episode").getItems().get(0).getId();
        Db.getInstance().insertBookmark(new Bookmark(itemId, 1_000L, "kept", 1L));
        DBUpgrade.upgrade(db, 9, Db.VERSION);
        assertEquals(1, rowCount(db, Db.TABLE_NAME_BOOKMARKS));
    }

    @Test
    public void reopeningAVersion9DatabaseReachesTheCurrentVersion() {
        long itemId = storeFeed("Show", "Episode").getItems().get(0).getId();
        Db.getInstance().getDb().execSQL("DROP TABLE " + Db.TABLE_NAME_BOOKMARKS);
        Db.tearDownTests();
        Context context = ApplicationProvider.getApplicationContext();
        SQLiteDatabase raw = SQLiteDatabase.openDatabase(
                context.getDatabasePath(Db.DATABASE_NAME).getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
        raw.setVersion(9);
        raw.close();

        Db reopened = Db.getInstance();
        assertEquals(Db.VERSION, reopened.getDb().getVersion());
        reopened.insertBookmark(new Bookmark(itemId, 1_000L, null, 1L));
        assertEquals(1, bookmarksOf(itemId).size());
    }

    // --------------------------------------------------------------- helpers

    private static Feed storeFeed(String feedTitle, String... itemTitles) {
        Feed feed = new Feed("https://example.com/" + feedTitle.replace(' ', '-') + ".xml", null, feedTitle);
        List<FeedItem> items = new ArrayList<>();
        for (String title : itemTitles) {
            items.add(new FeedItem(0, title, "guid-" + feedTitle + "-" + title, "https://example.com/" + title,
                    new Date(1_600_000_000_000L), FeedItem.UNPLAYED, feed));
        }
        feed.setItems(items);
        Db.getInstance().setCompleteFeed(feed);
        return feed;
    }

    private static List<Bookmark> bookmarksOf(long itemId) {
        List<Bookmark> bookmarks = new ArrayList<>();
        try (Cursor cursor = Db.getInstance().getBookmarksOfItemCursor(itemId)) {
            while (cursor.moveToNext()) {
                bookmarks.add(BookmarkCursorMapper.convert(cursor));
            }
        }
        return bookmarks;
    }

    private static List<String> tableNames(SQLiteDatabase db) {
        return sqliteMasterNames(db, "table");
    }

    private static List<String> indexNames(SQLiteDatabase db) {
        return sqliteMasterNames(db, "index");
    }

    private static List<String> sqliteMasterNames(SQLiteDatabase db, String type) {
        List<String> names = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type=?", new String[]{type})) {
            while (cursor.moveToNext()) {
                names.add(cursor.getString(0));
            }
        }
        return names;
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
