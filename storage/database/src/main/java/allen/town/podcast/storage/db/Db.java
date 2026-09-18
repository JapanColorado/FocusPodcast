package allen.town.podcast.storage.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.DefaultDatabaseErrorHandler;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import allen.town.podcast.model.download.DownloadStatus;
import allen.town.podcast.model.feed.AdSegment;
import allen.town.podcast.model.feed.Feed;
import allen.town.podcast.model.feed.FeedCounter;
import allen.town.podcast.model.feed.FeedItem;
import allen.town.podcast.model.feed.FeedItemFilter;
import allen.town.podcast.model.feed.FeedMedia;
import allen.town.podcast.model.feed.FeedPreferences;
import allen.town.podcast.model.feed.SortOrder;

/**
 * Implements methods for accessing the database.
 *
 * <p>This class is the single entry point callers use. It owns the process-wide singleton, opens the
 * writable {@link SQLiteDatabase} through its {@link SQLiteOpenHelper}, handles corruption, and
 * re-exports the column and table names that other modules refer to as {@code Db.KEY_*} and
 * {@code Db.TABLE_NAME_*}. The statements themselves live in the per-table helpers of this package
 * ({@link FeedDao}, {@link FeedItemDao}, {@link FeedMediaDao}, {@link QueueDao},
 * {@link FavoritesDao}, {@link DownloadLogDao}, {@link AdSegmentDao}); every method below delegates
 * to one of them,
 * except the two writes that span several tables and therefore keep their transaction here.</p>
 */
public class Db {

    private static final String TAG = "Db";

    public static final String DATABASE_NAME = DbSchema.DATABASE_NAME;
    public static final int VERSION = DbSchema.VERSION;

    // Key-constants
    public static final String KEY_TITLE = DbSchema.KEY_TITLE;
    public static final String KEY_ID = DbSchema.KEY_ID;
    public static final String KEY_FILE_URL = DbSchema.KEY_FILE_URL;
    public static final String KEY_CUSTOM_TITLE = DbSchema.KEY_CUSTOM_TITLE;
    public static final String KEY_LINK = DbSchema.KEY_LINK;
    public static final String KEY_POSITION = DbSchema.KEY_POSITION;
    public static final String KEY_DOWNLOAD_URL = DbSchema.KEY_DOWNLOAD_URL;
    public static final String KEY_PUBDATE = DbSchema.KEY_PUBDATE;
    public static final String KEY_READ = DbSchema.KEY_READ;
    public static final String KEY_DESCRIPTION = DbSchema.KEY_DESCRIPTION;
    public static final String KEY_SIZE = DbSchema.KEY_SIZE;
    public static final String KEY_IMAGE_URL = DbSchema.KEY_IMAGE_URL;
    public static final String KEY_FEED = DbSchema.KEY_FEED;
    public static final String KEY_DURATION = DbSchema.KEY_DURATION;
    public static final String KEY_MEDIA = DbSchema.KEY_MEDIA;
    public static final String KEY_DOWNLOADED = DbSchema.KEY_DOWNLOADED;
    public static final String KEY_MIME_TYPE = DbSchema.KEY_MIME_TYPE;
    public static final String KEY_FEEDFILE = DbSchema.KEY_FEEDFILE;
    public static final String KEY_REASON = DbSchema.KEY_REASON;
    public static final String KEY_SUCCESSFUL = DbSchema.KEY_SUCCESSFUL;
    public static final String KEY_LASTUPDATE = DbSchema.KEY_LASTUPDATE;
    public static final String KEY_COMPLETION_DATE = DbSchema.KEY_COMPLETION_DATE;
    public static final String KEY_FEEDITEM = DbSchema.KEY_FEEDITEM;
    public static final String KEY_PAYMENT_LINK = DbSchema.KEY_PAYMENT_LINK;
    public static final String KEY_FEEDFILETYPE = DbSchema.KEY_FEEDFILETYPE;
    public static final String KEY_LANGUAGE = DbSchema.KEY_LANGUAGE;
    public static final String KEY_AUTHOR = DbSchema.KEY_AUTHOR;
    public static final String KEY_HAS_CHAPTERS = DbSchema.KEY_HAS_CHAPTERS;
    public static final String KEY_START = DbSchema.KEY_START;
    public static final String KEY_TYPE = DbSchema.KEY_TYPE;
    public static final String KEY_PLAYBACK_COMPLETION_DATE = DbSchema.KEY_PLAYBACK_COMPLETION_DATE;
    public static final String KEY_ITEM_IDENTIFIER = DbSchema.KEY_ITEM_IDENTIFIER;
    public static final String KEY_DOWNLOADSTATUS_TITLE = DbSchema.KEY_DOWNLOADSTATUS_TITLE;
    public static final String KEY_FEED_IDENTIFIER = DbSchema.KEY_FEED_IDENTIFIER;
    public static final String KEY_REASON_DETAILED = DbSchema.KEY_REASON_DETAILED;
    public static final String KEY_SKIP_SILENCE_ENABLED = DbSchema.KEY_SKIP_SILENCE_ENABLED;
    public static final String KEY_AUTO_DOWNLOAD_ATTEMPTS = DbSchema.KEY_AUTO_DOWNLOAD_ATTEMPTS;
    public static final String KEY_AUTO_DOWNLOAD_ENABLED = DbSchema.KEY_AUTO_DOWNLOAD_ENABLED;
    public static final String KEY_IS_SUBSCRIBED = DbSchema.KEY_IS_SUBSCRIBED;
    public static final String KEY_ITUNES_FEED_ID = DbSchema.KEY_ITUNES_FEED_ID;
    public static final String KEY_USE_FEED_EFFECT = DbSchema.KEY_USE_FEED_EFFECT;
    public static final String KEY_LOUDNESS_ENABLED = DbSchema.KEY_LOUDNESS_ENABLED;
    public static final String KEY_MONO_ENABLED = DbSchema.KEY_MONO_ENABLED;
    public static final String KEY_PLAYED_DURATION = DbSchema.KEY_PLAYED_DURATION;
    public static final String KEY_KEEP_UPDATED = DbSchema.KEY_KEEP_UPDATED;
    public static final String KEY_USERNAME = DbSchema.KEY_USERNAME;
    public static final String KEY_FEED_VOLUME_ADAPTION = DbSchema.KEY_FEED_VOLUME_ADAPTION;
    public static final String KEY_AUTO_DELETE_ACTION = DbSchema.KEY_AUTO_DELETE_ACTION;
    public static final String KEY_MINIMAL_DURATION_FILTER = DbSchema.KEY_MINIMAL_DURATION_FILTER;
    public static final String KEY_IS_PAGED = DbSchema.KEY_IS_PAGED;
    public static final String KEY_NEXT_PAGE_LINK = DbSchema.KEY_NEXT_PAGE_LINK;
    public static final String KEY_HIDE = DbSchema.KEY_HIDE;
    public static final String KEY_PASSWORD = DbSchema.KEY_PASSWORD;
    public static final String KEY_LAST_UPDATE_FAILED = DbSchema.KEY_LAST_UPDATE_FAILED;
    public static final String KEY_HAS_EMBEDDED_PICTURE = DbSchema.KEY_HAS_EMBEDDED_PICTURE;
    public static final String KEY_LAST_PLAYED_TIME = DbSchema.KEY_LAST_PLAYED_TIME;
    public static final String KEY_SORT_ORDER = DbSchema.KEY_SORT_ORDER;
    public static final String KEY_EXCLUDE_FILTER = DbSchema.KEY_EXCLUDE_FILTER;
    public static final String KEY_PODCASTINDEX_CHAPTER_URL = DbSchema.KEY_PODCASTINDEX_CHAPTER_URL;
    public static final String KEY_INCLUDE_FILTER = DbSchema.KEY_INCLUDE_FILTER;
    public static final String KEY_FEED_SKIP_INTRO = DbSchema.KEY_FEED_SKIP_INTRO;
    public static final String KEY_FEED_SKIP_ENDING = DbSchema.KEY_FEED_SKIP_ENDING;
    public static final String KEY_FEED_TAGS = DbSchema.KEY_FEED_TAGS;
    public static final String KEY_EPISODE_NOTIFICATION = DbSchema.KEY_EPISODE_NOTIFICATION;
    public static final String KEY_FEED_PLAYBACK_SPEED = DbSchema.KEY_FEED_PLAYBACK_SPEED;
    public static final String KEY_FEED_AD_SKIP = DbSchema.KEY_FEED_AD_SKIP;
    public static final String KEY_AD_START_MS = DbSchema.KEY_AD_START_MS;
    public static final String KEY_AD_END_MS = DbSchema.KEY_AD_END_MS;
    public static final String KEY_AD_SOURCE = DbSchema.KEY_AD_SOURCE;
    public static final String KEY_AD_CONFIDENCE = DbSchema.KEY_AD_CONFIDENCE;
    public static final String KEY_AD_ENABLED = DbSchema.KEY_AD_ENABLED;

    // Table names
    public static final String TABLE_NAME_FEEDS = DbSchema.TABLE_NAME_FEEDS;
    public static final String TABLE_NAME_FEED_ITEMS = DbSchema.TABLE_NAME_FEED_ITEMS;
    public static final String TABLE_NAME_FEED_MEDIA = DbSchema.TABLE_NAME_FEED_MEDIA;
    public static final String TABLE_NAME_DOWNLOAD_LOG = DbSchema.TABLE_NAME_DOWNLOAD_LOG;
    public static final String TABLE_NAME_QUEUE = DbSchema.TABLE_NAME_QUEUE;
    public static final String TABLE_NAME_SIMPLECHAPTERS = DbSchema.TABLE_NAME_SIMPLECHAPTERS;
    public static final String TABLE_NAME_FAVORITES = DbSchema.TABLE_NAME_FAVORITES;
    public static final String TABLE_NAME_AD_SEGMENTS = DbSchema.TABLE_NAME_AD_SEGMENTS;

    public static final String SELECT_KEY_ITEM_ID = DbSchema.SELECT_KEY_ITEM_ID;
    public static final String SELECT_KEY_MEDIA_ID = DbSchema.SELECT_KEY_MEDIA_ID;
    private static Context context;
    private static Db instance;

    private final SQLiteDatabase db;
    private final DbHelper dbHelper;

    private final FeedDao feedDao;
    private final FeedMediaDao mediaDao;
    private final FeedItemDao itemDao;
    private final QueueDao queueDao;
    private final FavoritesDao favoritesDao;
    private final DownloadLogDao downloadLogDao;
    private final AdSegmentDao adSegmentDao;

    public static void init(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Db.init(context) must be called with a non-null context");
        }
        // Application context only: this field lives for the whole process.
        Db.context = context.getApplicationContext();
    }

    public static synchronized Db getInstance() {
        if (instance == null) {
            if (context == null) {
                throw new IllegalStateException("Db.init(context) must be called first");
            }
            instance = new Db();
        }
        return instance;
    }

    private Db() {
        dbHelper = new DbHelper(Db.context, DATABASE_NAME, null);
        db = openDb();
        feedDao = new FeedDao(db);
        mediaDao = new FeedMediaDao(db);
        adSegmentDao = new AdSegmentDao(db);
        itemDao = new FeedItemDao(db, feedDao, mediaDao, adSegmentDao);
        queueDao = new QueueDao(db);
        favoritesDao = new FavoritesDao(db);
        downloadLogDao = new DownloadLogDao(db);
    }

    public SQLiteDatabase getDb() {
        return db;
    }

    private SQLiteDatabase openDb() {
        SQLiteDatabase newDb;
        try {
            newDb = dbHelper.getWritableDatabase();
            newDb.disableWriteAheadLogging();
        } catch (SQLException ex) {
            // Log and continue: a read-only handle still lets the app show existing content
            // instead of crashing at startup; writes will fail loudly when they are attempted.
            Log.e(TAG, "Could not open the database for writing, falling back to read-only", ex);
            newDb = dbHelper.getReadableDatabase();
        }
        return newDb;
    }

    public synchronized Db open() {
        // do nothing
        return this;
    }

    public synchronized void close() {
        // do nothing
    }

    /**
     * <p>Resets all database connections to ensure new database connections for
     * the next test case. Call method only for unit tests.</p>
     *
     * <p>That's a workaround for a Robolectric issue in ShadowSQLiteConnection
     * that leads to an error <tt>IllegalStateException: Illegal connection
     * pointer</tt> if several threads try to use the same database connection.
     * For more information see
     * <a href="https://github.com/robolectric/robolectric/issues/1890">robolectric/robolectric#1890</a>.</p>
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public static void tearDownTests() {
        getInstance().dbHelper.close();
        instance = null;
    }

    public static boolean deleteDatabase() {
        Db adapter = getInstance();
        adapter.open();
        try {
            adapter.feedDao.deleteAllTables();
            return true;
        } finally {
            adapter.close();
        }
    }

    /**
     * Insert all FeedItems of a feed and the feed object itself in a single
     * transaction. Spans the feeds, episodes, medias and chapters tables, so the transaction is
     * held here rather than in one of the per-table helpers.
     */
    public void setCompleteFeed(Feed... feeds) {
        try {
            db.beginTransactionNonExclusive();
            for (Feed feed : feeds) {
                feedDao.setFeed(feed);
                if (feed.getItems() != null) {
                    for (FeedItem item : feed.getItems()) {
                        // fromRefresh: the item objects were read before the (possibly long)
                        // parse+merge, so only feed-derived columns may be written for rows
                        // that already exist; otherwise played state, playback position and
                        // downloaded flag changed in the meantime would be reverted.
                        itemDao.updateOrInsertFeedItem(item, false, true);
                    }
                }
                if (feed.getPreferences() != null) {
                    feedDao.setFeedPreferences(feed.getPreferences());
                }
            }
            db.setTransactionSuccessful();
        } catch (SQLException e) {
            // Log and continue: setTransactionSuccessful() was never reached, so the finally
            // below rolls the whole statement back. The caller's in-memory model is unchanged
            // and the next write of the same data retries it.
            Log.e(TAG, "setCompleteFeed failed, transaction rolled back", e);
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Remove a feed with all its FeedItems and Media entries. Better not to call this directly,
     * because the items of the feed may not have been queried at all. Spans several tables, so the
     * transaction is held here rather than in one of the per-table helpers.
     */
    public void removeFeed(Feed feed) {
        try {
            db.beginTransactionNonExclusive();
            if (feed.getItems() != null) {
                itemDao.removeFeedItems(feed.getItems());
            }
            feedDao.deleteFeedRow(feed);
            db.setTransactionSuccessful();
        } catch (SQLException e) {
            // Log and continue: setTransactionSuccessful() was never reached, so the finally
            // below rolls the whole statement back. The caller's in-memory model is unchanged
            // and the next write of the same data retries it.
            Log.e(TAG, "removeFeed failed, transaction rolled back", e);
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Insert raw data to the database.
     * Call method only for unit tests.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public void insertTestData(@NonNull String table, @NonNull ContentValues values) {
        db.insert(table, null, values);
    }

    // ---------------------------------------------------------------------------------------
    // Feeds
    // ---------------------------------------------------------------------------------------

    public void setFeedPreferences(FeedPreferences prefs) {
        feedDao.setFeedPreferences(prefs);
    }

    public void setFeedItemFilter(long feedId, Set<String> filterValues) {
        feedDao.setFeedItemFilter(feedId, filterValues);
    }

    public void setFeedItemSortOrder(long feedId, @Nullable SortOrder sortOrder) {
        feedDao.setFeedItemSortOrder(feedId, sortOrder);
    }

    /**
     * Updates the download URL of a Feed.
     */
    public void setFeedDownloadUrl(String original, String updated) {
        feedDao.setFeedDownloadUrl(original, updated);
    }

    public void setFeedLastUpdateFailed(long feedId, boolean failed) {
        feedDao.setFeedLastUpdateFailed(feedId, failed);
    }

    public void setFeedCustomTitle(long feedId, String customTitle) {
        feedDao.setFeedCustomTitle(feedId, customTitle);
    }

    public void subscribeFeed(long feedId) {
        feedDao.subscribeFeed(feedId);
    }

    public void setFeedItunesId(long feedId, String itunesId) {
        feedDao.setFeedItunesId(feedId, itunesId);
    }

    public final Cursor getSubscribedFeedsCountCursor() {
        return feedDao.getSubscribedFeedsCountCursor();
    }

    /**
     * Deletes feeds that are not subscribed and have no items in the playlist or the favorites.
     * Do not use this, the wrapped removeFeed is more appropriate.
     */
    public void removeFeedsNotSubAndNotInPlaylistAndFav() {
        feedDao.removeFeedsNotSubAndNotInPlaylistAndFav();
    }

    /**
     * Returns the feeds that are not subscribed and have no items in the playlist, the favorites
     * or the playback history.
     */
    public Cursor getFeedsNotSubAndNotInPlaylistAndFavCursor() {
        return feedDao.getFeedsNotSubAndNotInPlaylistAndFavCursor();
    }

    /**
     * Get all subscribed Feeds from the Feed Table.
     *
     * @return The cursor of the query
     */
    public final Cursor getSubscribedFeedsCursor() {
        return feedDao.getSubscribedFeedsCursor();
    }

    /**
     * Get all Feeds from the Feed Table
     *
     * @return The cursor of the query
     */
    public final Cursor getAllFeedsCursor() {
        return feedDao.getAllFeedsCursor();
    }

    public final Cursor getFeedCursorDownloadUrls() {
        return feedDao.getFeedCursorDownloadUrls();
    }

    /**
     * Returns the download URLs of the subscribed feeds.
     */
    public final Cursor getSubFeedCursorDownloadUrls() {
        return feedDao.getSubFeedCursorDownloadUrls();
    }

    public final Cursor getFeedCursor(final long id) {
        return feedDao.getFeedCursor(id);
    }

    public final Cursor getFeedCursorByFeedUrl(final String url) {
        return feedDao.getFeedCursorByFeedUrl(url);
    }

    public final Cursor getFeedCursorByItunesFeedId(final String itunesId) {
        return feedDao.getFeedCursorByItunesFeedId(itunesId);
    }

    public Cursor getImageAuthenticationCursor(final String imageUrl) {
        return feedDao.getImageAuthenticationCursor(imageUrl);
    }

    public final LongIntMap getFeedCounters(FeedCounter setting, long... feedIds) {
        return feedDao.getFeedCounters(setting, feedIds);
    }

    public final LongIntMap getPlayedEpisodesCounters(long... feedIds) {
        return feedDao.getPlayedEpisodesCounters(feedIds);
    }

    public final Map<Long, Long> getMostRecentItemDates() {
        return feedDao.getMostRecentItemDates();
    }

    /**
     * Searches for the given query in various values of all feeds.
     *
     * @return A cursor with all search results in SEL_FI_EXTRA selection.
     */
    public Cursor searchFeeds(String searchQuery) {
        return feedDao.searchFeeds(searchQuery);
    }

    // ---------------------------------------------------------------------------------------
    // Feed items
    // ---------------------------------------------------------------------------------------

    public void storeFeedItemlist(List<FeedItem> items) {
        itemDao.storeFeedItemlist(items);
    }

    public long setSingleFeedItem(FeedItem item) {
        return itemDao.setSingleFeedItem(item);
    }

    /**
     * Unlike setSingleFeedItem(FeedItem item), this does not update the feed.
     */
    public long setSingleFeedItemExcludeFeed(FeedItem item) {
        return itemDao.setSingleFeedItemExcludeFeed(item);
    }

    public void setFeedItemRead(int played, long itemId, long mediaId, boolean resetMediaPosition) {
        itemDao.setFeedItemRead(played, itemId, mediaId, resetMediaPosition);
    }

    /**
     * Sets the 'read' attribute of the item.
     *
     * @param read    must be one of FeedItem.PLAYED, FeedItem.NEW, FeedItem.UNPLAYED
     * @param itemIds items to change the value of
     */
    public void setFeedItemRead(int read, long... itemIds) {
        itemDao.setFeedItemRead(read, itemIds);
    }

    /**
     * Remove the listed items and their FeedMedia entries.
     */
    public void removeFeedItems(@NonNull List<FeedItem> items) {
        itemDao.removeFeedItems(items);
    }

    public void setFeedItems(int state) {
        setFeedItems(Integer.MIN_VALUE, state, 0);
    }

    public void setFeedItems(int oldState, int newState) {
        setFeedItems(oldState, newState, 0);
    }

    public void setFeedItems(int state, long feedId) {
        setFeedItems(Integer.MIN_VALUE, state, feedId);
    }

    public void setFeedItems(int oldState, int newState, long feedId) {
        itemDao.setFeedItems(oldState, newState, feedId);
    }

    /**
     * Returns a cursor with all FeedItems of a Feed. Uses FEEDITEM_SEL_FI_SMALL
     *
     * @param feed The feed you want to get the FeedItems from.
     * @return The cursor of the query
     */
    public final Cursor getItemsOfFeedCursor(final Feed feed, FeedItemFilter filter) {
        return itemDao.getItemsOfFeedCursor(feed, filter);
    }

    /**
     * Return the description and content_encoded of item
     */
    public final Cursor getDescriptionOfItem(final FeedItem item) {
        return itemDao.getDescriptionOfItem(item);
    }

    public final Cursor getSimpleChaptersOfFeedItemCursor(final FeedItem item) {
        return itemDao.getSimpleChaptersOfFeedItemCursor(item);
    }

    /**
     * Returns a cursor which contains all feed items that are considered new.
     * Excludes those feeds that do not have 'Keep Updated' enabled.
     * The returned cursor uses the FEEDITEM_SEL_FI_SMALL selection.
     */
    public final Cursor getNewItemsCursor(int offset, int limit) {
        return itemDao.getNewItemsCursor(offset, limit);
    }

    public final Cursor getRecentlyPublishedItemsCursor(int offset, int limit, FeedItemFilter filter) {
        return itemDao.getRecentlyPublishedItemsCursor(offset, limit, filter);
    }

    public final Cursor getTotalEpisodeCountCursor(FeedItemFilter filter) {
        return itemDao.getTotalEpisodeCountCursor(filter);
    }

    public Cursor getDownloadedItemsCursor() {
        return itemDao.getDownloadedItemsCursor();
    }

    public Cursor getPlayedItemsCursor() {
        return itemDao.getPlayedItemsCursor();
    }

    public final Cursor getFeedItemCursor(final String id) {
        return itemDao.getFeedItemCursor(id);
    }

    /**
     * Returns all feed items of unsubscribed feeds.
     */
    public final Cursor getUnsubFeedItemsCursor() {
        return itemDao.getUnsubFeedItemsCursor();
    }

    public final Cursor getFeedItemCursor(final String[] ids) {
        return itemDao.getFeedItemCursor(ids);
    }

    public final Cursor getFeedItemCursor(final String guid, final String episodeUrl) {
        return itemDao.getFeedItemCursor(guid, episodeUrl);
    }

    public final int getNumberOfNewItems() {
        return itemDao.getNumberOfNewItems();
    }

    /**
     * Searches for the given query in various values of all items or the items
     * of a specified feed.
     *
     * @return A cursor with all search results in SEL_FI_EXTRA selection.
     */
    public Cursor searchItems(long feedID, String searchQuery) {
        return itemDao.searchItems(feedID, searchQuery);
    }

    // ---------------------------------------------------------------------------------------
    // Feed media
    // ---------------------------------------------------------------------------------------

    /**
     * Inserts or updates a media entry
     *
     * @return the id of the entry
     */
    public long setMedia(FeedMedia media) {
        return mediaDao.setMedia(media);
    }

    /**
     * Saves only the download-related columns (downloaded flag, file url, embedded picture)
     * so that a stale in-memory snapshot cannot clobber playback position etc.
     */
    public void setFeedMediaDownloadState(FeedMedia media) {
        mediaDao.setFeedMediaDownloadState(media);
    }

    public void setFeedMediaPlaybackInformation(FeedMedia media) {
        mediaDao.setFeedMediaPlaybackInformation(media);
    }

    public void setFeedMediaPlaybackCompletionDate(FeedMedia media) {
        mediaDao.setFeedMediaPlaybackCompletionDate(media);
    }

    /**
     * Resets the playback duration of all podcasts to 0.
     */
    public void resetAllMediaPlayedDuration() {
        mediaDao.resetAllMediaPlayedDuration();
    }

    /**
     * Resets the playback duration of every episode of the given feed to 0.
     */
    public void resetMediaPlayedDurationForFeed(long feedId) {
        mediaDao.resetMediaPlayedDurationForFeed(feedId);
    }

    public void clearPlaybackHistory() {
        mediaDao.clearPlaybackHistory();
    }

    /**
     * Wipes the playback history (completion dates, played durations, last played times)
     * of every episode of the given feed.
     */
    public void clearPlaybackHistoryForFeed(long feedId) {
        mediaDao.clearPlaybackHistoryForFeed(feedId);
    }

    /**
     * Returns a cursor which contains feed media objects with a playback
     * completion date in ascending order.
     *
     * @param offset The row to start at.
     * @param limit  The maximum row count of the returned cursor. Must be an
     *               integer >= 0.
     * @throws IllegalArgumentException if limit < 0
     */
    public final Cursor getCompletedMediaCursor(int offset, int limit) {
        return mediaDao.getCompletedMediaCursor(offset, limit);
    }

    public final long getCompletedMediaLength() {
        return mediaDao.getCompletedMediaLength();
    }

    public final Cursor getSingleFeedMediaCursor(long id) {
        return mediaDao.getSingleFeedMediaCursor(id);
    }

    public final Cursor getMonthlyStatisticsCursor() {
        return mediaDao.getMonthlyStatisticsCursor();
    }

    public final int getNumberOfDownloadedEpisodes() {
        return mediaDao.getNumberOfDownloadedEpisodes();
    }

    // ---------------------------------------------------------------------------------------
    // Queue
    // ---------------------------------------------------------------------------------------

    public void setQueue(List<FeedItem> queue) {
        queueDao.setQueue(queue);
    }

    public void clearQueue() {
        queueDao.clearQueue();
    }

    /**
     * Returns a cursor which contains all feed items in the queue. The returned
     * cursor uses the FEEDITEM_SEL_FI_SMALL selection.
     */
    public final Cursor getQueueCursor() {
        return queueDao.getQueueCursor();
    }

    public Cursor getQueueIDCursor() {
        return queueDao.getQueueIDCursor();
    }

    public Cursor getNextInQueue(final FeedItem item) {
        return queueDao.getNextInQueue(item);
    }

    public int getQueueSize() {
        return queueDao.getQueueSize();
    }

    // ---------------------------------------------------------------------------------------
    // Favorites
    // ---------------------------------------------------------------------------------------

    public void setFavorites(List<FeedItem> favorites) {
        favoritesDao.setFavorites(favorites);
    }

    /**
     * Adds the item to favorites
     */
    public void addFavoriteItem(FeedItem item) {
        favoritesDao.addFavoriteItem(item);
    }

    public void removeFavoriteItem(FeedItem item) {
        favoritesDao.removeFavoriteItem(item);
    }

    public final Cursor getFavoritesCursor(int offset, int limit) {
        return favoritesDao.getFavoritesCursor(offset, limit);
    }

    // ---------------------------------------------------------------------------------------
    // Ad segments
    // ---------------------------------------------------------------------------------------

    /** All ad segments of one episode, earliest start first. */
    public final Cursor getAdSegmentsCursor(final long feedItemId) {
        return adSegmentDao.getAdSegmentsCursor(feedItemId);
    }

    /**
     * Replaces the segments that {@code source} contributed for this episode, leaving segments from
     * the other sources untouched.
     */
    public void replaceAdSegments(final long feedItemId, @NonNull final AdSegment.Source source,
                                  @NonNull final List<AdSegment> segments) {
        adSegmentDao.replaceAdSegments(feedItemId, source, segments);
    }

    /** Inserts one segment and returns its new row id. */
    public long insertAdSegment(@NonNull final AdSegment segment) {
        return adSegmentDao.insertAdSegment(segment);
    }

    public void setAdSegmentEnabled(final long id, final boolean enabled) {
        adSegmentDao.setAdSegmentEnabled(id, enabled);
    }

    public void deleteAdSegment(final long id) {
        adSegmentDao.deleteAdSegment(id);
    }

    public void deleteAdSegmentsOfItems(final long[] itemIds) {
        adSegmentDao.deleteAdSegmentsOfItems(itemIds);
    }

    // ---------------------------------------------------------------------------------------
    // Download log
    // ---------------------------------------------------------------------------------------

    /**
     * Inserts or updates a download status.
     */
    public long setDownloadStatus(DownloadStatus status) {
        return downloadLogDao.setDownloadStatus(status);
    }

    public void clearDownloadLog() {
        downloadLogDao.clearDownloadLog();
    }

    public final Cursor getDownloadLog(final int feedFileType, final long feedFileId) {
        return downloadLogDao.getDownloadLog(feedFileType, feedFileId);
    }

    public final Cursor getDownloadLogCursor(final int limit) {
        return downloadLogDao.getDownloadLogCursor(limit);
    }

    public final Cursor getFeedIdFromDownloadLogCursor(final int limit) {
        return downloadLogDao.getFeedIdFromDownloadLogCursor(limit);
    }

    /**
     * Called when a database corruption happens.
     */
    public static class PodDbErrorHandler implements DatabaseErrorHandler {
        @Override
        public void onCorruption(SQLiteDatabase db) {
            Log.e(TAG, "database corrupted: " + db.getPath());

            File dbPath = new File(db.getPath());
            File backupFolder = Db.context.getExternalFilesDir(null);
            File backupFile = new File(backupFolder, "FocusPodcastDatabaseBackup.db");
            try {
                FileUtils.copyFile(dbPath, backupFile);
                Log.d(TAG, "dump database to " + backupFile.getPath());
            } catch (IOException e) {
                // Log and continue: the backup copy is a diagnostic nicety. The corrupted database
                // must still be handed to DefaultDatabaseErrorHandler below so it gets recreated.
                Log.e(TAG, "Could not back up the corrupted database before deleting it", e);
            }

            new DefaultDatabaseErrorHandler().onCorruption(db); // This deletes the database
        }
    }

    /**
     * Helper class for opening the database.
     */
    private static class DbHelper extends SQLiteOpenHelper {
        /**
         * Constructor.
         *
         * @param context Context to use
         * @param name    Name of the database
         * @param factory to use for creating cursor objects
         */
        DbHelper(final Context context, final String name, final CursorFactory factory) {
            super(context, name, factory, VERSION, new PodDbErrorHandler());
        }

        @Override
        public void onCreate(final SQLiteDatabase db) {
            db.execSQL(DbSchema.CREATE_TABLE_FEED_ITEMS);
            db.execSQL(DbSchema.CREATE_TABLE_FEED_MEDIA);
            db.execSQL(DbSchema.CREATE_TABLE_DOWNLOAD_LOG);
            db.execSQL(DbSchema.CREATE_TABLE_QUEUE);
            db.execSQL(DbSchema.CREATE_TABLE_SIMPLECHAPTERS);
            db.execSQL(DbSchema.CREATE_TABLE_FAVORITES);
            db.execSQL(DbSchema.CREATE_TABLE_AD_SEGMENTS);
            db.execSQL(DbSchema.CREATE_TABLE_FEEDS);

            db.execSQL(DbSchema.CREATE_INDEX_FEEDITEMS_PUBDATE);
            db.execSQL(DbSchema.CREATE_INDEX_FEEDITEMS_READ);
            db.execSQL(DbSchema.CREATE_INDEX_FEEDMEDIA_FEEDITEM);
            db.execSQL(DbSchema.CREATE_INDEX_SIMPLECHAPTERS_FEEDITEM);
            db.execSQL(DbSchema.CREATE_INDEX_QUEUE_FEEDITEM);
            db.execSQL(DbSchema.CREATE_INDEX_FEEDITEMS_FEED);
            db.execSQL(DbSchema.CREATE_INDEX_AD_SEGMENTS_FEEDITEM);
        }

        @Override
        public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
            Log.w("DBAdapter", "Upgrading from version " + oldVersion + " to " + newVersion + ".");
            DBUpgrade.upgrade(db, oldVersion, newVersion);

            db.execSQL("DELETE FROM " + Db.TABLE_NAME_DOWNLOAD_LOG + " WHERE "
                    + Db.KEY_COMPLETION_DATE + "<" + (System.currentTimeMillis() - 7L * 24L * 3600L * 1000L));
        }
    }
}
