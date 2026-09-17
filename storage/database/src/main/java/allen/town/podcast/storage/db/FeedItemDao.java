package allen.town.podcast.storage.db;

import static allen.town.podcast.storage.db.DbSchema.IN_OPERATOR_MAXIMUM;
import static allen.town.podcast.storage.db.DbSchema.JOIN_FEED_ITEM_AND_FEED;
import static allen.town.podcast.storage.db.DbSchema.JOIN_FEED_ITEM_AND_MEDIA;
import static allen.town.podcast.storage.db.DbSchema.KEY_AUTO_DOWNLOAD_ATTEMPTS;
import static allen.town.podcast.storage.db.DbSchema.KEY_DESCRIPTION;
import static allen.town.podcast.storage.db.DbSchema.KEY_DOWNLOADED;
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

import java.util.Date;
import java.util.List;

import allen.town.podcast.model.feed.Chapter;
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
                + " ORDER BY " + TABLE_NAME_FEED_ITEMS + "." + KEY_PUBDATE + " DESC"
                + " LIMIT " + offset + ", " + limit;
        return db.rawQuery(query, null);
    }

    final Cursor getRecentlyPublishedItemsCursor(int offset, int limit, FeedItemFilter filter) {
        // Filter out the episodes of feeds that are not actually subscribed
        String filterQuery = FeedItemFilterQuery.generateFrom(filter);
        String whereClause = "".equals(filterQuery) ? " WHERE " + TABLE_NAME_FEEDS + "." + KEY_IS_SUBSCRIBED + "=1"
                : " WHERE " + filterQuery + " AND " + TABLE_NAME_FEEDS + "." + KEY_IS_SUBSCRIBED + "=1";
        final String query = SELECT_FEED_ITEMS_AND_MEDIA + JOIN_FEED_ITEM_AND_FEED + whereClause
                + " ORDER BY " + KEY_PUBDATE + " DESC LIMIT " + offset + ", " + limit;
        return db.rawQuery(query, null);
    }

    final Cursor getTotalEpisodeCountCursor(FeedItemFilter filter) {
        String filterQuery = FeedItemFilterQuery.generateFrom(filter);
        String whereClause = "".equals(filterQuery) ? "" : " WHERE " + filterQuery;
        final String query = "SELECT count(" + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + ") FROM " + TABLE_NAME_FEED_ITEMS
                + JOIN_FEED_ITEM_AND_MEDIA + JOIN_FEED_ITEM_AND_FEED + whereClause + " AND " + TABLE_NAME_FEEDS + "." + KEY_IS_SUBSCRIBED + "=1";
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

    /**
     * Searches for the given query in various values of all items or the items
     * of a specified feed.
     *
     * @return A cursor with all search results in SEL_FI_EXTRA selection.
     */
    Cursor searchItems(long feedID, String searchQuery) {
        String[] queryWords = prepareSearchQuery(searchQuery);

        String queryFeedId;
        if (feedID != 0) {
            // search items in specific feed
            queryFeedId = KEY_FEED + " = " + feedID;
        } else {
            // search through all items
            queryFeedId = "1 = 1";
        }
        StringBuilder descriptionStr = new StringBuilder();

        String queryStart = SELECT_FEED_ITEMS_AND_MEDIA_WITH_DESCRIPTION
                + " WHERE " + queryFeedId + " AND (";
        StringBuilder sb = new StringBuilder(queryStart);

        for (int i = 0; i < queryWords.length; i++) {
            descriptionStr = descriptionStr
                    .append(KEY_DESCRIPTION + " LIKE '%").append(queryWords[i])
                    .append("%' OR ");
            sb.append("(")
                    .append(descriptionStr)
                    .append(KEY_TITLE).append(" LIKE '%").append(queryWords[i])
                    .append("%') ");

            if (i != queryWords.length - 1) {
                sb.append("AND ");
            }
        }

        sb.append(") ORDER BY " + KEY_PUBDATE + " DESC LIMIT 300");

        return db.rawQuery(sb.toString(), null);
    }
}
