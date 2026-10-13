package allen.town.podcast.storage.db;

import static allen.town.podcast.storage.db.DbSchema.IN_OPERATOR_MAXIMUM;
import static allen.town.podcast.storage.db.DbSchema.JOIN_FEED_ITEM_AND_FEED;
import static allen.town.podcast.storage.db.DbSchema.JOIN_FEED_ITEM_AND_MEDIA;
import static allen.town.podcast.storage.db.DbSchema.KEY_AUDIOBOOK;
import static allen.town.podcast.storage.db.DbSchema.KEY_AUTO_DOWNLOAD_ATTEMPTS;
import static allen.town.podcast.storage.db.DbSchema.KEY_DISC_NUMBER;
import static allen.town.podcast.storage.db.DbSchema.KEY_TRACK_NUMBER;
import static allen.town.podcast.storage.db.DbSchema.KEY_DESCRIPTION;
import static allen.town.podcast.storage.db.DbSchema.KEY_DOWNLOADED;
import static allen.town.podcast.storage.db.DbSchema.KEY_DURATION;
import static allen.town.podcast.storage.db.DbSchema.KEY_DOWNLOAD_URL;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEED;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEEDFILE;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEEDFILETYPE;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEEDITEM;
import static allen.town.podcast.storage.db.DbSchema.KEY_HAS_CHAPTERS;
import static allen.town.podcast.storage.db.DbSchema.KEY_ID;
import static allen.town.podcast.storage.db.DbSchema.KEY_IMAGE_URL;
import static allen.town.podcast.storage.db.DbSchema.KEY_IS_SUBSCRIBED;
import static allen.town.podcast.storage.db.DbSchema.KEY_ITEM_IDENTIFIER;
import static allen.town.podcast.storage.db.DbSchema.KEY_KEEP_UPDATED;
import static allen.town.podcast.storage.db.DbSchema.KEY_LINK;
import static allen.town.podcast.storage.db.DbSchema.KEY_PAYMENT_LINK;
import static allen.town.podcast.storage.db.DbSchema.KEY_PODCASTINDEX_CHAPTER_URL;
import static allen.town.podcast.storage.db.DbSchema.KEY_POSITION;
import static allen.town.podcast.storage.db.DbSchema.KEY_PUBDATE;
import static allen.town.podcast.storage.db.DbSchema.KEY_READ;
import static allen.town.podcast.storage.db.DbSchema.KEY_START;
import static allen.town.podcast.storage.db.DbSchema.KEY_TITLE;
import static allen.town.podcast.storage.db.DbSchema.SELECT_FEED_ITEMS_AND_MEDIA;
import static allen.town.podcast.storage.db.DbSchema.SELECT_FEED_ITEMS_AND_MEDIA_WITH_DESCRIPTION;
import static allen.town.podcast.storage.db.DbSchema.SELECT_KEY_ITEM_ID;
import static allen.town.podcast.storage.db.DbSchema.TABLE_NAME_DOWNLOAD_LOG;
import static allen.town.podcast.storage.db.DbSchema.TABLE_NAME_FAVORITES;
import static allen.town.podcast.storage.db.DbSchema.TABLE_NAME_FEEDS;
import static allen.town.podcast.storage.db.DbSchema.TABLE_NAME_FEED_ITEMS;
import static allen.town.podcast.storage.db.DbSchema.TABLE_NAME_FEED_MEDIA;
import static allen.town.podcast.storage.db.DbSchema.TABLE_NAME_QUEUE;
import static allen.town.podcast.storage.db.DbSchema.TABLE_NAME_SIMPLECHAPTERS;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import allen.town.podcast.model.feed.Chapter;
import allen.town.podcast.model.feed.EpisodeSearchOptions;
import allen.town.podcast.model.feed.Feed;
import allen.town.podcast.model.feed.FeedItem;
import allen.town.podcast.model.feed.FeedItemFilter;
import allen.town.podcast.model.feed.FeedMedia;
import allen.town.podcast.storage.db.mapper.FeedItemFilterQuery;

/**
 * Owns every statement against the {@code episodes} table and the {@code chapters} rows that hang
 * off it: inserting or updating an item (delegating the feed row to {@link FeedDao} and the media
 * row to {@link FeedMediaDao}), the bulk and single-item stores, the played/unplayed state updates,
 * the cascade that removes items together with their chapters, ad segments, media and download log
 * entries, the item search, and all the cursors that read episodes joined with their media.
 */
class FeedItemDao extends Dao {

    private final FeedDao feedDao;
    private final FeedMediaDao mediaDao;
    private final AdSegmentDao adSegmentDao;

    FeedItemDao(SQLiteDatabase db, FeedDao feedDao, FeedMediaDao mediaDao, AdSegmentDao adSegmentDao) {
        super(db);
        this.feedDao = feedDao;
        this.mediaDao = mediaDao;
        this.adSegmentDao = adSegmentDao;
    }

    void storeFeedItemlist(List<FeedItem> items) {
        inTransaction("storeFeedItemlist", () -> {
            for (FeedItem item : items) {
                updateOrInsertFeedItem(item, true);
            }
        });
    }

    long setSingleFeedItem(FeedItem item) {
        long result = 0;
        try {
            db.beginTransactionNonExclusive();
            result = updateOrInsertFeedItem(item, true);
            db.setTransactionSuccessful();
        } catch (SQLException e) {
            // Log and continue: setTransactionSuccessful() was never reached, so the finally
            // below rolls the whole statement back. The caller's in-memory model is unchanged
            // and the next write of the same data retries it.
            Log.e(TAG, "setSingleFeedItem failed, transaction rolled back", e);
        } finally {
            db.endTransaction();
        }
        return result;
    }

    /**
     * Unlike setSingleFeedItem(FeedItem item), this does not update the feed.
     */
    long setSingleFeedItemExcludeFeed(FeedItem item) {
        long result = 0;
        try {
            db.beginTransactionNonExclusive();
            result = updateOrInsertFeedItem(item, false);
            db.setTransactionSuccessful();
        } catch (SQLException e) {
            // Log and continue: setTransactionSuccessful() was never reached, so the finally
            // below rolls the whole statement back. The caller's in-memory model is unchanged
            // and the next write of the same data retries it.
            Log.e(TAG, "setSingleFeedItemExcludeFeed failed, transaction rolled back", e);
        } finally {
            db.endTransaction();
        }
        return result;
    }

    /**
     * Inserts or updates a feeditem entry
     *
     * @param item     The FeedItem
     * @param saveFeed true if the Feed of the item should also be saved. This should be set to
     *                 false if the method is executed on a list of FeedItems of the same Feed.
     * @return the id of the entry
     */
    long updateOrInsertFeedItem(FeedItem item, boolean saveFeed) {
        return updateOrInsertFeedItem(item, saveFeed, false);
    }

    /**
     * @param fromRefresh true if the item comes from a feed refresh merge. For rows that already
     *                    exist only feed-derived columns are written then: the read state is
     *                    written only when the merge marked the item NEW, and the media row
     *                    keeps its position/downloaded/file columns.
     */
    long updateOrInsertFeedItem(FeedItem item, boolean saveFeed, boolean fromRefresh) {
        final boolean existingRefreshRow = fromRefresh && item.getId() != 0;
        if (item.getId() == 0 && item.getPubDate() == null) {
            Log.e(TAG, "Newly saved item has no pubDate. Using current date as pubDate");
            item.setPubDate(new Date());
        }

        ContentValues values = new ContentValues();
        values.put(KEY_TITLE, item.getTitle());
        values.put(KEY_LINK, item.getLink());
        if (item.getDescription() != null) {
            values.put(KEY_DESCRIPTION, item.getDescription());
        }
        values.put(KEY_PUBDATE, item.getPubDate().getTime());
        values.put(KEY_PAYMENT_LINK, item.getPaymentLink());
        if (saveFeed && item.getFeed() != null) {
            feedDao.setFeed(item.getFeed());
        }
        values.put(KEY_FEED, item.getFeed().getId());
        if (item.isNew()) {
            values.put(KEY_READ, FeedItem.NEW);
        } else if (!existingRefreshRow) {
            values.put(KEY_READ, item.isPlayed() ? FeedItem.PLAYED : FeedItem.UNPLAYED);
        }
        values.put(KEY_HAS_CHAPTERS, item.getChapters() != null || item.hasChapters());
        values.put(KEY_ITEM_IDENTIFIER, item.getItemIdentifier());
        if (!existingRefreshRow) {
            values.put(KEY_AUTO_DOWNLOAD_ATTEMPTS, item.getAutoDownloadAttemptsAndTime());
        }
        values.put(KEY_IMAGE_URL, item.getImageUrl());
        values.put(KEY_PODCASTINDEX_CHAPTER_URL, item.getPodcastIndexChapterUrl());
        values.put(KEY_DISC_NUMBER, item.getDiscNumber());
        values.put(KEY_TRACK_NUMBER, item.getTrackNumber());

        if (item.getId() == 0) {
            item.setId(db.insert(TABLE_NAME_FEED_ITEMS, null, values));
        } else {
            db.update(TABLE_NAME_FEED_ITEMS, values, KEY_ID + "=?",
                    new String[]{String.valueOf(item.getId())});
        }
        if (item.getMedia() != null) {
            if (fromRefresh && item.getMedia().getId() != 0) {
                mediaDao.setMediaFeedData(item.getMedia());
            } else {
                mediaDao.setMedia(item.getMedia());
            }
        }
        if (item.getChapters() != null) {
            setChapters(item);
        }
        return item.getId();
    }

    void setFeedItemRead(int played, long itemId, long mediaId, boolean resetMediaPosition) {
        inTransaction("setFeedItemRead", () -> {
            ContentValues values = new ContentValues();

            values.put(KEY_READ, played);
            db.update(TABLE_NAME_FEED_ITEMS, values, KEY_ID + "=?", new String[]{String.valueOf(itemId)});

            if (resetMediaPosition) {
                values.clear();
                values.put(KEY_POSITION, 0);
                db.update(TABLE_NAME_FEED_MEDIA, values, KEY_ID + "=?", new String[]{String.valueOf(mediaId)});
            }
        });
    }

    /**
     * Sets the 'read' attribute of the item.
     *
     * @param read    must be one of FeedItem.PLAYED, FeedItem.NEW, FeedItem.UNPLAYED
     * @param itemIds items to change the value of
     */
    void setFeedItemRead(int read, long... itemIds) {
        inTransaction("setFeedItemRead", () -> {
            ContentValues values = new ContentValues();
            for (long id : itemIds) {
                values.clear();
                values.put(KEY_READ, read);
                db.update(TABLE_NAME_FEED_ITEMS, values, KEY_ID + "=?", new String[]{String.valueOf(id)});
            }
        });
    }

    private void setChapters(FeedItem item) {
        ContentValues values = new ContentValues();
        for (Chapter chapter : item.getChapters()) {
            values.put(KEY_TITLE, chapter.getTitle());
            values.put(KEY_START, chapter.getStart());
            values.put(KEY_FEEDITEM, item.getId());
            values.put(KEY_LINK, chapter.getLink());
            values.put(KEY_IMAGE_URL, chapter.getImageUrl());
            if (chapter.getId() == 0) {
                chapter.setId(db.insert(TABLE_NAME_SIMPLECHAPTERS, null, values));
            } else {
                db.update(TABLE_NAME_SIMPLECHAPTERS, values, KEY_ID + "=?",
                        new String[]{String.valueOf(chapter.getId())});
            }
        }
    }

    /**
     * Remove the listed items and their FeedMedia entries.
     */
    void removeFeedItems(@NonNull List<FeedItem> items) {
        try {
            StringBuilder mediaIds = new StringBuilder();
            StringBuilder itemIds = new StringBuilder();
            long[] itemIdArray = new long[items.size()];
            for (int i = 0; i < items.size(); i++) {
                itemIdArray[i] = items.get(i).getId();
            }
            for (FeedItem item : items) {
                if (item.getMedia() != null) {
                    if (mediaIds.length() != 0) {
                        mediaIds.append(",");
                    }
                    mediaIds.append(item.getMedia().getId());
                }
                if (itemIds.length() != 0) {
                    itemIds.append(",");
                }
                itemIds.append(item.getId());
            }

            db.beginTransactionNonExclusive();
            db.delete(TABLE_NAME_SIMPLECHAPTERS, KEY_FEEDITEM + " IN (" + itemIds + ")", null);
            adSegmentDao.deleteAdSegmentsOfItems(itemIdArray);
            db.delete(TABLE_NAME_DOWNLOAD_LOG, KEY_FEEDFILETYPE + "=" + FeedMedia.FEEDFILETYPE_FEEDMEDIA
                            + " AND " + KEY_FEEDFILE + " IN (" + mediaIds + ")", null);
            db.delete(TABLE_NAME_FEED_MEDIA, KEY_ID + " IN (" + mediaIds + ")", null);
            db.delete(TABLE_NAME_FEED_ITEMS, KEY_ID + " IN (" + itemIds + ")", null);
            db.setTransactionSuccessful();
        } catch (SQLException e) {
            // Log and continue: setTransactionSuccessful() was never reached, so the finally
            // below rolls the whole statement back. The caller's in-memory model is unchanged
            // and the next write of the same data retries it.
            Log.e(TAG, "removeFeedItems failed, transaction rolled back", e);
        } finally {
            db.endTransaction();
        }
    }

    void setFeedItems(int oldState, int newState, long feedId) {
        String sql = "UPDATE " + TABLE_NAME_FEED_ITEMS + " SET " + KEY_READ + "=" + newState;
        if (feedId > 0) {
            sql += " WHERE " + KEY_FEED + "=" + feedId;
        }
        if (FeedItem.NEW <= oldState && oldState <= FeedItem.PLAYED) {
            sql += feedId > 0 ? " AND " : " WHERE ";
            sql += KEY_READ + "=" + oldState;
        }
        db.execSQL(sql);
    }

    /**
     * Returns a cursor with all FeedItems of a Feed. Uses FEEDITEM_SEL_FI_SMALL
     *
     * @param feed The feed you want to get the FeedItems from.
     * @return The cursor of the query
     */
    final Cursor getItemsOfFeedCursor(final Feed feed, FeedItemFilter filter) {
        String filterQuery = FeedItemFilterQuery.generateFrom(filter);
        String whereClauseAnd = "".equals(filterQuery) ? "" : " AND " + filterQuery;
        final String query = SELECT_FEED_ITEMS_AND_MEDIA
                + " WHERE " + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + "=" + feed.getId()
                + whereClauseAnd;
        return db.rawQuery(query, null);
    }

    /**
     * Return the description and content_encoded of item
     */
    final Cursor getDescriptionOfItem(final FeedItem item) {
        final String query = "SELECT " + KEY_DESCRIPTION
                + " FROM " + TABLE_NAME_FEED_ITEMS
                + " WHERE " + KEY_ID + "=" + item.getId();
        return db.rawQuery(query, null);
    }

    final Cursor getSimpleChaptersOfFeedItemCursor(final FeedItem item) {
        return db.query(TABLE_NAME_SIMPLECHAPTERS, null, KEY_FEEDITEM
                        + "=?", new String[]{String.valueOf(item.getId())}, null,
                null, null
        );
    }

    /**
     * Returns a cursor which contains all feed items that are considered new.
     * Excludes those feeds that do not have 'Keep Updated' enabled.
     * The returned cursor uses the FEEDITEM_SEL_FI_SMALL selection.
     */
    final Cursor getNewItemsCursor(int offset, int limit) {
        final String query = SELECT_FEED_ITEMS_AND_MEDIA
                + " INNER JOIN " + TABLE_NAME_FEEDS
                + " ON " + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + "=" + TABLE_NAME_FEEDS + "." + KEY_ID
                + " WHERE " + TABLE_NAME_FEED_ITEMS + "." + KEY_READ + "=" + FeedItem.NEW
                    + " AND " + TABLE_NAME_FEEDS + "." + KEY_KEEP_UPDATED + " > 0"
                    + " AND " + TABLE_NAME_FEEDS + "." + KEY_IS_SUBSCRIBED + "=1"
                    + " AND " + NOT_AUDIOBOOK
                + " ORDER BY " + TABLE_NAME_FEED_ITEMS + "." + KEY_PUBDATE + " DESC"
                + " LIMIT " + offset + ", " + limit;
        return db.rawQuery(query, null);
    }

    /**
     * SQL condition (on a query that joins {@code feeds}) that keeps only items of feeds that are
     * not audiobooks. Audiobooks stay out of the podcast-wide lists: All Episodes, the new-item
     * list and its count.
     */
    static final String NOT_AUDIOBOOK = "COALESCE(" + TABLE_NAME_FEEDS + "." + KEY_AUDIOBOOK + ", 0) = 0";

    /**
     * WHERE clause of the episode lists: subscribed feeds only, no audiobooks, plus the filter.
     */
    private static String episodeListWhereClause(FeedItemFilter filter) {
        String filterQuery = FeedItemFilterQuery.generateFrom(filter);
        String base = TABLE_NAME_FEEDS + "." + KEY_IS_SUBSCRIBED + "=1 AND " + NOT_AUDIOBOOK;
        return "".equals(filterQuery) ? " WHERE " + base : " WHERE " + filterQuery + " AND " + base;
    }

    /** Items of subscribed, non-audiobook feeds matching the filter, newest first. */
    final Cursor getRecentlyPublishedItemsCursor(int offset, int limit, FeedItemFilter filter) {
        final String query = SELECT_FEED_ITEMS_AND_MEDIA + JOIN_FEED_ITEM_AND_FEED + episodeListWhereClause(filter)
                + " ORDER BY " + KEY_PUBDATE + " DESC LIMIT " + offset + ", " + limit;
        return db.rawQuery(query, null);
    }

    /**
     * Every item, with its media, of every subscribed audiobook feed. Unordered; the caller sorts
     * per feed in track order.
     */
    final Cursor getAudiobookItemsCursor() {
        final String query = SELECT_FEED_ITEMS_AND_MEDIA
                + " INNER JOIN " + TABLE_NAME_FEEDS
                + " ON " + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + "=" + TABLE_NAME_FEEDS + "." + KEY_ID
                + " WHERE " + TABLE_NAME_FEEDS + "." + KEY_AUDIOBOOK + "=1"
                + " AND " + TABLE_NAME_FEEDS + "." + KEY_IS_SUBSCRIBED + "=1";
        return db.rawQuery(query, null);
    }

    /** Number of items of the feed, ignoring the feed's item filter. */
    final int getFeedItemCount(long feedId) {
        final String query = "SELECT COUNT(" + KEY_ID + ") FROM " + TABLE_NAME_FEED_ITEMS
                + " WHERE " + KEY_FEED + "=?";
        try (Cursor c = db.rawQuery(query, new String[]{String.valueOf(feedId)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /** Number of items of the feed that match {@code filter} (what its episode list shows). */
    final int getFeedItemCount(long feedId, FeedItemFilter filter) {
        String filterQuery = FeedItemFilterQuery.generateFrom(filter);
        final String query = "SELECT COUNT(" + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + ") FROM "
                + TABLE_NAME_FEED_ITEMS + JOIN_FEED_ITEM_AND_MEDIA
                + " WHERE " + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + "=?"
                + ("".equals(filterQuery) ? "" : " AND " + filterQuery);
        try (Cursor c = db.rawQuery(query, new String[]{String.valueOf(feedId)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /**
     * Counts what {@link #getRecentlyPublishedItemsCursor} lists. (Before version 7 an empty filter
     * left this without a WHERE, so the subscribed condition ended up in the LEFT JOIN's ON clause
     * and every item was counted.)
     */
    final Cursor getTotalEpisodeCountCursor(FeedItemFilter filter) {
        final String query = "SELECT count(" + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + ") FROM " + TABLE_NAME_FEED_ITEMS
                + JOIN_FEED_ITEM_AND_MEDIA + JOIN_FEED_ITEM_AND_FEED + episodeListWhereClause(filter);
        return db.rawQuery(query, null);
    }

    Cursor getDownloadedItemsCursor() {
        final String query = SELECT_FEED_ITEMS_AND_MEDIA
                + "WHERE " + TABLE_NAME_FEED_MEDIA + "." + KEY_DOWNLOADED + " > 0";
        return db.rawQuery(query, null);
    }

    Cursor getPlayedItemsCursor() {
        final String query = SELECT_FEED_ITEMS_AND_MEDIA
                + "WHERE " + TABLE_NAME_FEED_ITEMS + "." + KEY_READ + "=" + FeedItem.PLAYED;
        return db.rawQuery(query, null);
    }

    final Cursor getFeedItemCursor(final String id) {
        return getFeedItemCursor(new String[]{id});
    }

    /**
     * Returns all feed items of unsubscribed feeds.
     */
    final Cursor getUnsubFeedItemsCursor() {
        final String query = SELECT_FEED_ITEMS_AND_MEDIA
                + " INNER JOIN " + "(select * from " + TABLE_NAME_FEEDS + " where " + KEY_IS_SUBSCRIBED + " = 0 ) feeds "
                + " ON feeds." + KEY_ID + " = " + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED
                + " LEFT JOIN " + TABLE_NAME_QUEUE + " ON "
                + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + " = " + TABLE_NAME_QUEUE + "." + KEY_FEEDITEM + " "
                + " LEFT JOIN " + TABLE_NAME_FAVORITES + " ON "
                + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + " = " + TABLE_NAME_FAVORITES + "." + KEY_FEEDITEM + " "
                + " WHERE " + TABLE_NAME_FAVORITES + "." + KEY_FEEDITEM + " is null "
                + " AND " + TABLE_NAME_QUEUE + "." + KEY_FEEDITEM + " is null ";
        return db.rawQuery(query, null);
    }

    final Cursor getFeedItemCursor(final String[] ids) {
        if (ids.length > IN_OPERATOR_MAXIMUM) {
            throw new IllegalArgumentException("number of IDs must not be larger than " + IN_OPERATOR_MAXIMUM);
        }
        final String query = SELECT_FEED_ITEMS_AND_MEDIA
                + " WHERE " + SELECT_KEY_ITEM_ID + " IN (" + TextUtils.join(",", ids) + ")";
        return db.rawQuery(query, null);
    }

    final Cursor getFeedItemCursor(final String guid, final String episodeUrl) {
        String escapedEpisodeUrl = DatabaseUtils.sqlEscapeString(episodeUrl);
        String whereClauseCondition = TABLE_NAME_FEED_MEDIA + "." + KEY_DOWNLOAD_URL + "=" + escapedEpisodeUrl;

        if (guid != null) {
            String escapedGuid = DatabaseUtils.sqlEscapeString(guid);
            whereClauseCondition = TABLE_NAME_FEED_ITEMS + "." + KEY_ITEM_IDENTIFIER + "=" + escapedGuid;
        }

        final String query = SELECT_FEED_ITEMS_AND_MEDIA
                + " INNER JOIN " + TABLE_NAME_FEEDS
                + " ON " + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + "=" + TABLE_NAME_FEEDS + "." + KEY_ID
                + " WHERE " + whereClauseCondition;
        return db.rawQuery(query, null);
    }

    final int getNumberOfNewItems() {
        Object[] args = new String[]{
                TABLE_NAME_FEED_ITEMS + "." + KEY_ID,
                TABLE_NAME_FEED_ITEMS,
                TABLE_NAME_FEEDS,
                TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + "=" + TABLE_NAME_FEEDS + "." + KEY_ID,
                TABLE_NAME_FEED_ITEMS + "." + KEY_READ + "=" + FeedItem.NEW
                        + " AND " + TABLE_NAME_FEEDS + "." + KEY_KEEP_UPDATED + " > 0"
                        + " AND " + NOT_AUDIOBOOK
        };
        final String query = String.format("SELECT COUNT(%s) FROM %s INNER JOIN %s ON %s WHERE %s", args);
        Cursor c = db.rawQuery(query, null);
        int result = 0;
        if (c.moveToFirst()) {
            result = c.getInt(0);
        }
        c.close();
        return result;
    }

    /** At most this many search results are returned. */
    static final int SEARCH_LIMIT = 300;

    /**
     * Searches for the given query in the title and show notes of all items or the items of a
     * specified feed, newest first.
     *
     * @return A cursor with all search results in SEL_FI_EXTRA selection.
     */
    Cursor searchItems(long feedID, String searchQuery) {
        EpisodeSearchOptions options = EpisodeSearchOptions.defaults()
                .withFeedId(feedID)
                .withSort(EpisodeSearchOptions.Sort.NEWEST);
        return searchItems(searchQuery, options, System.currentTimeMillis());
    }

    /**
     * Items that contain every word of {@code searchQuery} in one of the {@link EpisodeSearchOptions#fields}
     * and match the rest of the options, in their sort order, at most {@link #SEARCH_LIMIT}. A blank
     * query matches every item that passes the options.
     *
     * @param nowMillis the current time, which the date range counts back from
     * @return A cursor with all search results in SEL_FI_EXTRA selection.
     */
    Cursor searchItems(String searchQuery, EpisodeSearchOptions options, long nowMillis) {
        List<String> args = new ArrayList<>();
        String[] words = searchWords(searchQuery);
        String query = SELECT_FEED_ITEMS_AND_MEDIA_WITH_DESCRIPTION
                + searchWhereClause(words, options, nowMillis, args)
                + searchOrderBy(words, options, args)
                + " LIMIT " + SEARCH_LIMIT;
        return db.rawQuery(query, args.toArray(new String[0]));
    }

    /** How many items {@link #searchItems(String, EpisodeSearchOptions, long)} returns. */
    int countSearchItems(String searchQuery, EpisodeSearchOptions options, long nowMillis) {
        List<String> args = new ArrayList<>();
        String query = "SELECT COUNT(*) FROM (SELECT " + TABLE_NAME_FEED_ITEMS + "." + KEY_ID
                + " FROM " + TABLE_NAME_FEED_ITEMS + JOIN_FEED_ITEM_AND_MEDIA
                + searchWhereClause(searchWords(searchQuery), options, nowMillis, args)
                + " LIMIT " + SEARCH_LIMIT + ")";
        try (Cursor cursor = db.rawQuery(query, args.toArray(new String[0]))) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    /** The WHERE clause of a search; appends its bound arguments to {@code args}. */
    private static String searchWhereClause(String[] words, EpisodeSearchOptions options, long nowMillis,
                                            List<String> args) {
        List<String> conditions = new ArrayList<>();
        List<String> columns = searchColumns(options);
        for (String word : words) {
            StringBuilder anyColumn = new StringBuilder("(");
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    anyColumn.append(" OR ");
                }
                anyColumn.append(columns.get(i)).append(LIKE_ESCAPED);
                args.add(likeContains(word));
            }
            conditions.add(anyColumn.append(")").toString());
        }
        if (options.feedId != 0) {
            conditions.add(TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + " = " + options.feedId);
        }
        long earliest = options.dateRange.earliestPubDate(nowMillis);
        if (earliest > 0) {
            conditions.add(TABLE_NAME_FEED_ITEMS + "." + KEY_PUBDATE + " >= " + earliest);
        }
        String duration = TABLE_NAME_FEED_MEDIA + "." + KEY_DURATION;
        if (options.durationRange.minMillis > 0) {
            conditions.add(duration + " >= " + options.durationRange.minMillis);
        }
        if (options.durationRange.maxMillis > 0) {
            conditions.add(duration + " <= " + options.durationRange.maxMillis);
        }
        String filter = FeedItemFilterQuery.generateFrom(options.filter);
        if (!filter.isEmpty()) {
            conditions.add(filter);
        }
        return conditions.isEmpty() ? "" : " WHERE " + TextUtils.join(" AND ", conditions);
    }

    /** The searched columns, in the order of {@link EpisodeSearchOptions.Field}. */
    private static List<String> searchColumns(EpisodeSearchOptions options) {
        List<String> columns = new ArrayList<>();
        for (EpisodeSearchOptions.Field field : options.fields) {
            switch (field) {
                case TITLE:
                    columns.add(TABLE_NAME_FEED_ITEMS + "." + KEY_TITLE);
                    break;
                case SHOW_NOTES:
                    columns.add(TABLE_NAME_FEED_ITEMS + "." + KEY_DESCRIPTION);
                    break;
                default:
                    break;
            }
        }
        return columns;
    }

    /**
     * The ORDER BY clause of a search; appends its bound arguments to {@code args}. Ties go newest
     * first, then by id, so the order is stable.
     */
    private static String searchOrderBy(String[] words, EpisodeSearchOptions options, List<String> args) {
        String pubDate = TABLE_NAME_FEED_ITEMS + "." + KEY_PUBDATE;
        String duration = TABLE_NAME_FEED_MEDIA + "." + KEY_DURATION;
        String newest = pubDate + " DESC, " + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + " DESC";
        switch (options.sort) {
            case OLDEST:
                return " ORDER BY " + pubDate + " ASC, " + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + " ASC";
            case LONGEST:
                // NULL sorts last in descending order
                return " ORDER BY " + duration + " DESC, " + newest;
            case SHORTEST:
                // unknown durations (NULL or 0) last rather than first
                return " ORDER BY (" + duration + " IS NULL OR " + duration + " <= 0), "
                        + duration + " ASC, " + newest;
            case RELEVANCE:
                if (words.length > 0 && options.fields.contains(EpisodeSearchOptions.Field.TITLE)) {
                    StringBuilder titleHasAll = new StringBuilder(" ORDER BY CASE WHEN ");
                    for (int i = 0; i < words.length; i++) {
                        if (i > 0) {
                            titleHasAll.append(" AND ");
                        }
                        titleHasAll.append(TABLE_NAME_FEED_ITEMS + "." + KEY_TITLE).append(LIKE_ESCAPED);
                        args.add(likeContains(words[i]));
                    }
                    return titleHasAll.append(" THEN 0 ELSE 1 END, ").append(newest).toString();
                }
                return " ORDER BY " + newest;
            case NEWEST:
            default:
                return " ORDER BY " + newest;
        }
    }
}
