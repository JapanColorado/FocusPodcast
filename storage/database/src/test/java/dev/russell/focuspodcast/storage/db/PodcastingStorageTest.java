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
import java.util.Collections;
import java.util.Date;
import java.util.List;

import dev.russell.focuspodcast.model.feed.Feed;
import dev.russell.focuspodcast.model.feed.FeedItem;
import dev.russell.focuspodcast.model.feed.Person;
import dev.russell.focuspodcast.model.feed.PodcastingJson;
import dev.russell.focuspodcast.model.feed.Soundbite;
import dev.russell.focuspodcast.storage.db.mapper.FeedCursorMapper;

/**
 * Podcasting 2.0 people and soundbites in the database (version 11): the JSON form, the round trip
 * of feed and episode columns, that storing an item read without them keeps them, the newest
 * episode with soundbites, and the 10 -&gt; 11 upgrade step.
 */
@RunWith(RobolectricTestRunner.class)
public class PodcastingStorageTest {

    private static final Person ALICE = new Person("Alice", null, null,
            "https://example.com/a.jpg", "https://example.com/a");
    private static final Person CAROL = new Person("Carol", "Guest", "cast", null, null);
    private static final Soundbite BITE = new Soundbite(73_000, 60_500, "Salty");
    private static final Soundbite UNTITLED = new Soundbite(1_000, 2_000, null);

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

    // ------------------------------------------------------------------ JSON

    @Test
    public void jsonRoundTripsPersonsAndSoundbites() {
        List<Person> persons = Arrays.asList(ALICE, CAROL);
        assertEquals(persons, PodcastingJson.personsFromJson(PodcastingJson.personsToJson(persons)));
        List<Soundbite> bites = Arrays.asList(BITE, UNTITLED);
        assertEquals(bites, PodcastingJson.soundbitesFromJson(PodcastingJson.soundbitesToJson(bites)));
    }

    @Test
    public void emptyListsAreStoredAsNull() {
        assertNull(PodcastingJson.personsToJson(null));
        assertNull(PodcastingJson.personsToJson(Collections.emptyList()));
        assertNull(PodcastingJson.soundbitesToJson(Collections.emptyList()));
    }

    @Test
    public void malformedJsonReadsAsNone() {
        assertNull(PodcastingJson.personsFromJson(null));
        assertNull(PodcastingJson.personsFromJson(""));
        assertNull(PodcastingJson.personsFromJson("{not json"));
        assertNull(PodcastingJson.soundbitesFromJson("[1, 2"));
        // entries missing what they need are skipped, the rest kept
        List<Person> persons = PodcastingJson.personsFromJson("[{\"role\":\"guest\"},{\"name\":\"Dan\"}]");
        assertEquals(Collections.singletonList(new Person("Dan", null, null, null, null)), persons);
        List<Soundbite> bites = PodcastingJson.soundbitesFromJson(
                "[{\"start\":5},{\"start\":-1,\"duration\":3},{\"start\":1,\"duration\":2}]");
        assertEquals(Collections.singletonList(new Soundbite(1, 2, null)), bites);
    }

    // ------------------------------------------------------------- storage

    @Test
    public void feedAndItemColumnsRoundTrip() {
        Feed feed = newFeed("Show", "Episode");
        feed.setPersons(Collections.singletonList(ALICE));
        FeedItem item = feed.getItems().get(0);
        item.setPersons(Arrays.asList(ALICE, CAROL));
        item.setSoundbites(Arrays.asList(BITE, UNTITLED));
        Db.getInstance().setCompleteFeed(feed);

        assertEquals(Collections.singletonList(ALICE), readFeed(feed.getId()).getPersons());
        FeedItem read = new FeedItem();
        read.setId(item.getId());
        loadExtras(read);
        assertEquals(Arrays.asList(ALICE, CAROL), read.getPersons());
        assertEquals(Arrays.asList(BITE, UNTITLED), read.getSoundbites());
    }

    @Test
    public void storingWithoutExtrasKeepsTheStoredOnes() {
        Feed feed = newFeed("Show", "Episode");
        feed.setPersons(Collections.singletonList(ALICE));
        FeedItem item = feed.getItems().get(0);
        item.setSoundbites(Collections.singletonList(BITE));
        Db.getInstance().setCompleteFeed(feed);

        // as a list query would hand it back: no people, no soundbites
        item.setSoundbites(null);
        feed.setPersons(null);
        Db.getInstance().setCompleteFeed(feed);

        assertEquals(Collections.singletonList(ALICE), readFeed(feed.getId()).getPersons());
        FeedItem read = new FeedItem();
        read.setId(item.getId());
        loadExtras(read);
        assertEquals(Collections.singletonList(BITE), read.getSoundbites());
        assertNull(read.getPersons());
    }

    @Test
    public void newestItemWithSoundbites() {
        Feed feed = newFeed("Show", "Old", "Middle", "New");
        feed.getItems().get(0).setSoundbites(Collections.singletonList(BITE));
        feed.getItems().get(1).setSoundbites(Collections.singletonList(UNTITLED));
        Db.getInstance().setCompleteFeed(feed);

        assertEquals(feed.getItems().get(1).getId(),
                Db.getInstance().getNewestItemIdWithSoundbites(feed.getId()));
        Feed other = newFeed("Other", "Plain");
        Db.getInstance().setCompleteFeed(other);
        assertEquals(0, Db.getInstance().getNewestItemIdWithSoundbites(other.getId()));
    }

    // ------------------------------------------------------------- upgrade

    /** The real 10 -&gt; 11 step on tables recreated without the new columns. */
    @Test
    public void upgradeFromVersion10AddsTheColumns() {
        SQLiteDatabase db = Db.getInstance().getDb();
        String feedColumn = "," + DbSchema.KEY_PERSONS + " TEXT)";
        String itemColumns = "," + DbSchema.KEY_PERSONS + " TEXT," + DbSchema.KEY_SOUNDBITES + " TEXT)";
        assertTrue(DbSchema.CREATE_TABLE_FEEDS.endsWith(feedColumn));
        assertTrue(DbSchema.CREATE_TABLE_FEED_ITEMS.endsWith(itemColumns));
        db.execSQL("DROP TABLE " + Db.TABLE_NAME_FEEDS);
        db.execSQL(DbSchema.CREATE_TABLE_FEEDS.replace(feedColumn, ")"));
        db.execSQL("DROP TABLE " + Db.TABLE_NAME_FEED_ITEMS);
        db.execSQL(DbSchema.CREATE_TABLE_FEED_ITEMS.replace(itemColumns, ")"));
        db.execSQL("INSERT INTO " + Db.TABLE_NAME_FEED_ITEMS + " (" + Db.KEY_TITLE + ") VALUES ('Old item')");
        assertFalse(columnNames(db, Db.TABLE_NAME_FEEDS).contains(Db.KEY_PERSONS));

        DBUpgrade.upgrade(db, 10, Db.VERSION);

        assertTrue(columnNames(db, Db.TABLE_NAME_FEEDS).contains(Db.KEY_PERSONS));
        List<String> itemColumnNames = columnNames(db, Db.TABLE_NAME_FEED_ITEMS);
        assertTrue(itemColumnNames.contains(Db.KEY_PERSONS));
        assertTrue(itemColumnNames.contains(Db.KEY_SOUNDBITES));
        try (Cursor cursor = db.rawQuery("SELECT " + Db.KEY_SOUNDBITES + " FROM "
                + Db.TABLE_NAME_FEED_ITEMS, null)) {
            assertTrue(cursor.moveToFirst());
            assertTrue(cursor.isNull(0));
        }

        // running it again must not fail on the columns it just added
        DBUpgrade.upgrade(db, 10, Db.VERSION);
        assertEquals(1, rowCount(db, Db.TABLE_NAME_FEED_ITEMS));
    }

    // ------------------------------------------------------------- helpers

    private static Feed newFeed(String feedTitle, String... itemTitles) {
        Feed feed = new Feed("https://example.com/" + feedTitle + ".xml", null, feedTitle);
        List<FeedItem> items = new ArrayList<>();
        long day = 86_400_000L;
        for (int i = 0; i < itemTitles.length; i++) {
            String title = itemTitles[i];
            items.add(new FeedItem(0, title, "guid-" + feedTitle + "-" + title, "https://example.com/" + title,
                    new Date(1_600_000_000_000L + i * day), FeedItem.UNPLAYED, feed));
        }
        feed.setItems(items);
        return feed;
    }

    private static Feed readFeed(long feedId) {
        try (Cursor cursor = Db.getInstance().getFeedCursor(feedId)) {
            assertTrue(cursor.moveToFirst());
            return FeedCursorMapper.convert(cursor);
        }
    }

    private static void loadExtras(FeedItem item) {
        try (Cursor cursor = Db.getInstance().getPodcastingExtrasOfItem(item.getId())) {
            assertTrue(cursor.moveToFirst());
            item.setPersons(PodcastingJson.personsFromJson(cursor.getString(0)));
            item.setSoundbites(PodcastingJson.soundbitesFromJson(cursor.getString(1)));
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

    private static int rowCount(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery("SELECT count(*) FROM " + table, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }
}
