package allen.town.podcast.storage.db;

import static allen.town.podcast.model.feed.SortOrder.toCodeString;
import static allen.town.podcast.storage.db.DbSchema.ALL_TABLES;
import static allen.town.podcast.storage.db.DbSchema.FEED_SEL_STD;
import static allen.town.podcast.storage.db.DbSchema.JOIN_FEED_ITEM_AND_FEED;
import static allen.town.podcast.storage.db.DbSchema.KEY_AUTHOR;
import static allen.town.podcast.storage.db.DbSchema.KEY_AUTO_DELETE_ACTION;
import static allen.town.podcast.storage.db.DbSchema.KEY_AUTO_DOWNLOAD_ENABLED;
import static allen.town.podcast.storage.db.DbSchema.KEY_CUSTOM_TITLE;
import static allen.town.podcast.storage.db.DbSchema.KEY_DESCRIPTION;
import static allen.town.podcast.storage.db.DbSchema.KEY_DOWNLOADED;
import static allen.town.podcast.storage.db.DbSchema.KEY_DOWNLOAD_URL;
import static allen.town.podcast.storage.db.DbSchema.KEY_EPISODE_NOTIFICATION;
import static allen.town.podcast.storage.db.DbSchema.KEY_EXCLUDE_FILTER;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEED;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEEDFILE;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEEDFILETYPE;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEEDITEM;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEED_IDENTIFIER;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEED_PLAYBACK_SPEED;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEED_SKIP_ENDING;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEED_AD_SKIP_OVERRIDE;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEED_SKIP_INTRO;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEED_TAGS;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEED_VOLUME_ADAPTION;
import static allen.town.podcast.storage.db.DbSchema.KEY_FILE_URL;
import static allen.town.podcast.storage.db.DbSchema.KEY_HIDE;
import static allen.town.podcast.storage.db.DbSchema.KEY_ID;
import static allen.town.podcast.storage.db.DbSchema.KEY_IMAGE_URL;
import static allen.town.podcast.storage.db.DbSchema.KEY_INCLUDE_FILTER;
import static allen.town.podcast.storage.db.DbSchema.KEY_IS_PAGED;
import static allen.town.podcast.storage.db.DbSchema.KEY_IS_SUBSCRIBED;
import static allen.town.podcast.storage.db.DbSchema.KEY_ITUNES_FEED_ID;
import static allen.town.podcast.storage.db.DbSchema.KEY_KEEP_UPDATED;
import static allen.town.podcast.storage.db.DbSchema.KEY_LANGUAGE;
import static allen.town.podcast.storage.db.DbSchema.KEY_LASTUPDATE;
import static allen.town.podcast.storage.db.DbSchema.KEY_LAST_UPDATE_FAILED;
import static allen.town.podcast.storage.db.DbSchema.KEY_LINK;
import static allen.town.podcast.storage.db.DbSchema.KEY_LOUDNESS_ENABLED;
import static allen.town.podcast.storage.db.DbSchema.KEY_MINIMAL_DURATION_FILTER;
import static allen.town.podcast.storage.db.DbSchema.KEY_MONO_ENABLED;
import static allen.town.podcast.storage.db.DbSchema.KEY_NEXT_PAGE_LINK;
import static allen.town.podcast.storage.db.DbSchema.KEY_PASSWORD;
import static allen.town.podcast.storage.db.DbSchema.KEY_PAYMENT_LINK;
import static allen.town.podcast.storage.db.DbSchema.KEY_PLAYBACK_COMPLETION_DATE;
import static allen.town.podcast.storage.db.DbSchema.KEY_PUBDATE;
import static allen.town.podcast.storage.db.DbSchema.KEY_READ;
import static allen.town.podcast.storage.db.DbSchema.KEY_SKIP_SILENCE_ENABLED;
import static allen.town.podcast.storage.db.DbSchema.KEY_SORT_ORDER;
import static allen.town.podcast.storage.db.DbSchema.KEY_TITLE;
import static allen.town.podcast.storage.db.DbSchema.KEY_TYPE;
import static allen.town.podcast.storage.db.DbSchema.KEY_USERNAME;
import static allen.town.podcast.storage.db.DbSchema.KEY_USE_FEED_EFFECT;
import static allen.town.podcast.storage.db.DbSchema.TABLE_NAME_DOWNLOAD_LOG;
import static allen.town.podcast.storage.db.DbSchema.TABLE_NAME_FAVORITES;
import static allen.town.podcast.storage.db.DbSchema.TABLE_NAME_FEEDS;
import static allen.town.podcast.storage.db.DbSchema.TABLE_NAME_FEED_ITEMS;
import static allen.town.podcast.storage.db.DbSchema.TABLE_NAME_FEED_MEDIA;
import static allen.town.podcast.storage.db.DbSchema.TABLE_NAME_QUEUE;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import allen.town.podcast.model.feed.Feed;
import allen.town.podcast.model.feed.FeedCounter;
import allen.town.podcast.model.feed.FeedFunding;
import allen.town.podcast.model.feed.FeedItem;
import allen.town.podcast.model.feed.FeedPreferences;
import allen.town.podcast.model.feed.SortOrder;

/**
 * Owns every statement against the {@code feeds} table: inserting and updating a feed row and its
 * preferences, the per-feed flags (custom title, iTunes id, sort order, item filter, subscribed and
 * last-update-failed), the cursors that read feed rows back with the standard projection, the
 * search over feeds, and the aggregate counters that group episodes by feed. It also provides the
 * single-row delete used by the cross-table feed removal that {@link Db} runs in one transaction.
 */
class FeedDao extends Dao {

    FeedDao(SQLiteDatabase db) {
        super(db);
    }

    /**
     * Inserts or updates a feed entry
     *
     * @return the id of the entry
     */
    long setFeed(Feed feed) {
        ContentValues values = new ContentValues();
        values.put(KEY_TITLE, feed.getFeedTitle());
        values.put(KEY_LINK, feed.getLink());
        values.put(KEY_DESCRIPTION, feed.getDescription());
        values.put(KEY_IS_SUBSCRIBED, feed.isSubscribed());
        values.put(KEY_ITUNES_FEED_ID, feed.getItunesId());
        values.put(KEY_PAYMENT_LINK, FeedFunding.getPaymentLinksAsString(feed.getPaymentLinks()));
        values.put(KEY_AUTHOR, feed.getAuthor());
        values.put(KEY_LANGUAGE, feed.getLanguage());
        values.put(KEY_IMAGE_URL, feed.getImageUrl());

        values.put(KEY_FILE_URL, feed.getFile_url());
        values.put(KEY_DOWNLOAD_URL, feed.getDownload_url());
        values.put(KEY_LASTUPDATE, feed.getLastUpdate());
        values.put(KEY_TYPE, feed.getType());
        values.put(KEY_FEED_IDENTIFIER, feed.getFeedIdentifier());

        values.put(KEY_IS_PAGED, feed.isPaged());
        values.put(KEY_NEXT_PAGE_LINK, feed.getNextPageLink());
        if (feed.getItemFilter() != null && feed.getItemFilter().getValues().length > 0) {
            values.put(KEY_HIDE, TextUtils.join(",", feed.getItemFilter().getValues()));
        } else {
            values.put(KEY_HIDE, "");
        }
        values.put(KEY_SORT_ORDER, toCodeString(feed.getSortOrder()));
        values.put(KEY_LAST_UPDATE_FAILED, feed.hasLastUpdateFailed());
        if (feed.getId() == 0) {
            // Create new entry
            Log.d(TAG, "insert new feed into db");
            feed.setId(db.insert(TABLE_NAME_FEEDS, null, values));
        } else {
            Log.d(TAG, "update existing feed in db");
            db.update(TABLE_NAME_FEEDS, values, KEY_ID + "=?",
                    new String[]{String.valueOf(feed.getId())});
        }
        return feed.getId();
    }

    void setFeedPreferences(FeedPreferences prefs) {
        if (prefs.getFeedID() == 0) {
            throw new IllegalArgumentException("Feed ID of preference must not be null");
        }
        ContentValues values = new ContentValues();
        values.put(KEY_AUTO_DOWNLOAD_ENABLED, prefs.getAutoDownload());
        values.put(KEY_SKIP_SILENCE_ENABLED, prefs.isSkipSilence());
        values.put(KEY_USE_FEED_EFFECT, prefs.isUseFeedEffect());
        values.put(KEY_LOUDNESS_ENABLED, prefs.isLoudness());
        values.put(KEY_MONO_ENABLED, prefs.isMono());
        values.put(KEY_KEEP_UPDATED, prefs.getKeepUpdated());
        values.put(KEY_AUTO_DELETE_ACTION, prefs.getAutoDeleteAction().ordinal());
        values.put(KEY_FEED_VOLUME_ADAPTION, prefs.getVolumeAdaptionSetting().toInteger());
        values.put(KEY_USERNAME, prefs.getUsername());
        values.put(KEY_PASSWORD, prefs.getPassword());
        values.put(KEY_INCLUDE_FILTER, prefs.getFilter().getIncludeFilterRaw());
        values.put(KEY_EXCLUDE_FILTER, prefs.getFilter().getExcludeFilterRaw());
        values.put(KEY_MINIMAL_DURATION_FILTER, prefs.getFilter().getMinimalDurationFilter());
        values.put(KEY_FEED_PLAYBACK_SPEED, prefs.getFeedPlaybackSpeed());
        values.put(KEY_FEED_TAGS, prefs.getTagsAsString());
        values.put(KEY_FEED_SKIP_INTRO, prefs.getFeedSkipIntro());
        values.put(KEY_FEED_SKIP_ENDING, prefs.getFeedSkipEnding());
        values.put(KEY_EPISODE_NOTIFICATION, prefs.getShowEpisodeNotification());
        // Boolean → INTEGER 1/0, and null stays NULL: "follow the global default".
        Boolean adSkipOverride = prefs.getAdSkipOverride();
        if (adSkipOverride == null) {
            values.putNull(KEY_FEED_AD_SKIP_OVERRIDE);
        } else {
            values.put(KEY_FEED_AD_SKIP_OVERRIDE, adSkipOverride);
        }
        db.update(TABLE_NAME_FEEDS, values, KEY_ID + "=?", new String[]{String.valueOf(prefs.getFeedID())});
    }

    void setFeedItemFilter(long feedId, Set<String> filterValues) {
        String valuesList = TextUtils.join(",", filterValues);
        Log.d(TAG, String.format(Locale.US,
                "setFeedItemFilter() called with: feedId = [%d], filterValues = [%s]", feedId, valuesList));
        ContentValues values = new ContentValues();
        values.put(KEY_HIDE, valuesList);
        db.update(TABLE_NAME_FEEDS, values, KEY_ID + "=?", new String[]{String.valueOf(feedId)});
    }

    void setFeedItemSortOrder(long feedId, @Nullable SortOrder sortOrder) {
        ContentValues values = new ContentValues();
        values.put(KEY_SORT_ORDER, toCodeString(sortOrder));
        db.update(TABLE_NAME_FEEDS, values, KEY_ID + "=?", new String[]{String.valueOf(feedId)});
    }

    /**
     * Updates the download URL of a Feed.
     */
    void setFeedDownloadUrl(String original, String updated) {
        ContentValues values = new ContentValues();
        values.put(KEY_DOWNLOAD_URL, updated);
        db.update(TABLE_NAME_FEEDS, values, KEY_DOWNLOAD_URL + "=?", new String[]{original});
    }

    void setFeedLastUpdateFailed(long feedId, boolean failed) {
        final String sql = "UPDATE " + TABLE_NAME_FEEDS
                + " SET " + KEY_LAST_UPDATE_FAILED + "=" + (failed ? "1" : "0")
                + " WHERE " + KEY_ID + "=" + feedId;
        db.execSQL(sql);
    }

    void setFeedCustomTitle(long feedId, String customTitle) {
        ContentValues values = new ContentValues();
        values.put(KEY_CUSTOM_TITLE, customTitle);
        db.update(TABLE_NAME_FEEDS, values, KEY_ID + "=?", new String[]{String.valueOf(feedId)});
    }

    void subscribeFeed(long feedId) {
        ContentValues values = new ContentValues();
        values.put(KEY_IS_SUBSCRIBED, true);
        db.update(TABLE_NAME_FEEDS, values, KEY_ID + "=?", new String[]{String.valueOf(feedId)});
    }

    void setFeedItunesId(long feedId, String itunesId) {
        ContentValues values = new ContentValues();
        values.put(KEY_ITUNES_FEED_ID, itunesId);
        db.update(TABLE_NAME_FEEDS, values, KEY_ID + "=?", new String[]{String.valueOf(feedId)});
    }

    /**
     * Removes the feed row itself and its download log entries. The items of the feed are removed
     * separately; see {@link Db#removeFeed(Feed)}, which wraps both in one transaction.
     */
    void deleteFeedRow(Feed feed) {
        // delete download log entries for feed
        db.delete(TABLE_NAME_DOWNLOAD_LOG, KEY_FEEDFILE + "=? AND " + KEY_FEEDFILETYPE + "=?",
                new String[]{String.valueOf(feed.getId()), String.valueOf(Feed.FEEDFILETYPE_FEED)});

        db.delete(TABLE_NAME_FEEDS, KEY_ID + "=?",
                new String[]{String.valueOf(feed.getId())});
    }

    /**
     * Empties every table of the database.
     */
    void deleteAllTables() {
        for (String tableName : ALL_TABLES) {
            db.delete(tableName, "1", null);
        }
    }

    final Cursor getSubscribedFeedsCountCursor() {
        return db.query(TABLE_NAME_FEEDS, new String[]{"count(*)"}, KEY_IS_SUBSCRIBED + "=?", new String[]{"1"}, null, null,
                KEY_TITLE + " COLLATE NOCASE ASC");
    }

    /**
     * Deletes feeds that are not subscribed and have no items in the playlist or the favorites.
     * Do not use this, the wrapped removeFeed is more appropriate.
     */
    void removeFeedsNotSubAndNotInPlaylistAndFav() {
        db.execSQL("delete from " + TABLE_NAME_FEEDS
                        + " where " + KEY_ID + " in ("
                        + " select " + KEY_ID + " from " + TABLE_NAME_FEEDS + " where " + KEY_IS_SUBSCRIBED + " =? AND "
                        + KEY_ID + " not in (" + " select " + KEY_FEED + " from " + TABLE_NAME_FAVORITES + ") AND "
                        + KEY_ID + " not in (" + " select " + KEY_FEED + " from " + TABLE_NAME_QUEUE + "))"
                , new String[]{"0"});
    }

    /**
     * Returns the feeds that are not subscribed and have no items in the playlist, the favorites
     * or the playback history.
     */
    Cursor getFeedsNotSubAndNotInPlaylistAndFavCursor() {
        return db.rawQuery("select " + KEY_ID + " from " + TABLE_NAME_FEEDS + " where " + KEY_IS_SUBSCRIBED + " =? AND "
                        + KEY_ID + " not in (" + " select " + KEY_FEED + " from " + TABLE_NAME_FAVORITES + ") AND "
                        + KEY_ID + " not in (" + " select " + KEY_FEED + " from " + TABLE_NAME_QUEUE + ") AND "
                        + KEY_ID + " not in (" + " select " + KEY_FEED + " from " + TABLE_NAME_FEED_ITEMS
                        + " inner join (select " + KEY_FEEDITEM + " from " + TABLE_NAME_FEED_MEDIA + " where "
                        + KEY_PLAYBACK_COMPLETION_DATE + " >0 ) m "
                        + " on m." + KEY_FEEDITEM + " = " + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + " group by " + KEY_FEED
                        + ") "
                , new String[]{"0"});
    }

    /**
     * Get all subscribed Feeds from the Feed Table.
     *
     * @return The cursor of the query
     */
    final Cursor getSubscribedFeedsCursor() {
        return db.query(TABLE_NAME_FEEDS, FEED_SEL_STD, KEY_IS_SUBSCRIBED + "=?", new String[]{"1"}, null, null,
                KEY_TITLE + " COLLATE NOCASE ASC");
    }

    /**
     * Get all Feeds from the Feed Table
     *
     * @return The cursor of the query
     */
    final Cursor getAllFeedsCursor() {
        return db.query(TABLE_NAME_FEEDS, FEED_SEL_STD, null, null, null, null,
                KEY_TITLE + " COLLATE NOCASE ASC");
    }

    final Cursor getFeedCursorDownloadUrls() {
        return db.query(TABLE_NAME_FEEDS, new String[]{KEY_ID, KEY_DOWNLOAD_URL}, null, null, null, null, null);
    }

    /**
     * Returns the download URLs of the subscribed feeds.
     */
    final Cursor getSubFeedCursorDownloadUrls() {
        return db.query(TABLE_NAME_FEEDS, new String[]{KEY_ID, KEY_DOWNLOAD_URL}, KEY_IS_SUBSCRIBED + " =?", new String[]{"1"}, null, null, null);
    }

    final Cursor getFeedCursor(final long id) {
        return db.query(TABLE_NAME_FEEDS, FEED_SEL_STD, KEY_ID + "=" + id, null,
                null, null, null);
    }

    final Cursor getFeedCursorByFeedUrl(final String url) {
        return db.query(TABLE_NAME_FEEDS, FEED_SEL_STD, KEY_DOWNLOAD_URL + "=?", new String[]{url},
                null, null, null);
    }

    final Cursor getFeedCursorByItunesFeedId(final String itunesId) {
        return db.query(TABLE_NAME_FEEDS, FEED_SEL_STD, KEY_ITUNES_FEED_ID + "=?", new String[]{itunesId},
                null, null, null);
    }

    Cursor getImageAuthenticationCursor(final String imageUrl) {
        String downloadUrl = DatabaseUtils.sqlEscapeString(imageUrl);
        final String query = ""
                + "SELECT " + KEY_USERNAME + "," + KEY_PASSWORD + " FROM " + TABLE_NAME_FEED_ITEMS
                + " INNER JOIN " + TABLE_NAME_FEEDS
                + " ON " + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + " = " + TABLE_NAME_FEEDS + "." + KEY_ID
                + " WHERE " + TABLE_NAME_FEED_ITEMS + "." + KEY_IMAGE_URL + "=" + downloadUrl
                + " UNION SELECT " + KEY_USERNAME + "," + KEY_PASSWORD + " FROM " + TABLE_NAME_FEEDS
                + " WHERE " + TABLE_NAME_FEEDS + "." + KEY_IMAGE_URL + "=" + downloadUrl;
        return db.rawQuery(query, null);
    }

    final LongIntMap getFeedCounters(FeedCounter setting, long... feedIds) {
        String whereRead;
        switch (setting) {
            case SHOW_NEW_UNPLAYED_SUM:
                whereRead = "(" + KEY_READ + "=" + FeedItem.NEW
                        + " OR " + KEY_READ + "=" + FeedItem.UNPLAYED + ")";
                break;
            case SHOW_NEW:
                whereRead = KEY_READ + "=" + FeedItem.NEW;
                break;
            case SHOW_UNPLAYED:
                whereRead = KEY_READ + "=" + FeedItem.UNPLAYED;
                break;
            case SHOW_DOWNLOADED:
                whereRead = KEY_DOWNLOADED + "=1";
                break;
            case SHOW_DOWNLOADED_UNPLAYED:
                whereRead = "(" + KEY_READ + "=" + FeedItem.NEW
                        + " OR " + KEY_READ + "=" + FeedItem.UNPLAYED + ")"
                        + " AND " + KEY_DOWNLOADED + "=1";
                break;
            case SHOW_NONE:
                // deliberate fall-through
            default: // NONE
                return new LongIntMap(0);
        }
        return conditionalFeedCounterRead(whereRead, feedIds);
    }

    private LongIntMap conditionalFeedCounterRead(String whereRead, long... feedIds) {
        String limitFeeds = "";
        if (feedIds.length > 0) {
            // work around TextUtils.join wanting only boxed items
            // and StringUtils.join() causing NoSuchMethodErrors on MIUI
            StringBuilder builder = new StringBuilder();
            for (long id : feedIds) {
                builder.append(id);
                builder.append(',');
            }
            // there's an extra ',', get rid of it
            builder.deleteCharAt(builder.length() - 1);
            limitFeeds = KEY_FEED + " IN (" + builder.toString() + ") AND ";
        }

        final String query = "SELECT " + KEY_FEED + ", COUNT(" + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + ") AS count "
                + " FROM " + TABLE_NAME_FEED_ITEMS
                + " LEFT JOIN " + TABLE_NAME_FEED_MEDIA + " ON "
                + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + "=" + TABLE_NAME_FEED_MEDIA + "." + KEY_FEEDITEM
                + JOIN_FEED_ITEM_AND_FEED
                + " WHERE " + limitFeeds + " "
                + whereRead
                + " AND " + TABLE_NAME_FEEDS + "." + KEY_IS_SUBSCRIBED + "=1"
                + " GROUP BY " + KEY_FEED;

        Cursor c = db.rawQuery(query, null);
        LongIntMap result = new LongIntMap(c.getCount());
        if (c.moveToFirst()) {
            do {
                long feedId = c.getLong(0);
                int count = c.getInt(1);
                result.put(feedId, count);
            } while (c.moveToNext());
        }
        c.close();
        return result;
    }

    final LongIntMap getPlayedEpisodesCounters(long... feedIds) {
        String whereRead = KEY_READ + "=" + FeedItem.PLAYED;
        return conditionalFeedCounterRead(whereRead, feedIds);
    }

    final Map<Long, Long> getMostRecentItemDates() {
        final String query = "SELECT " + KEY_FEED + ","
                + " MAX(" + TABLE_NAME_FEED_ITEMS + "." + KEY_PUBDATE + ") AS most_recent_pubdate"
                + " FROM " + TABLE_NAME_FEED_ITEMS
                + " GROUP BY " + KEY_FEED;

        Cursor c = db.rawQuery(query, null);
        Map<Long, Long> result = new HashMap<>();
        if (c.moveToFirst()) {
            do {
                long feedId = c.getLong(0);
                long date = c.getLong(1);
                result.put(feedId, date);
            } while (c.moveToNext());
        }
        c.close();
        return result;
    }

    /**
     * Searches for the given query in various values of all feeds.
     *
     * @return A cursor with all search results in SEL_FI_EXTRA selection.
     */
    Cursor searchFeeds(String searchQuery) {
        String[] queryWords = prepareSearchQuery(searchQuery);

        String queryStart = "SELECT * FROM " + TABLE_NAME_FEEDS + " WHERE ";
        StringBuilder sb = new StringBuilder(queryStart);

        for (int i = 0; i < queryWords.length; i++) {
            sb
                    .append("(")
                    .append(KEY_TITLE).append(" LIKE '%").append(queryWords[i])
                    .append("%' OR ")
                    .append(KEY_CUSTOM_TITLE).append(" LIKE '%").append(queryWords[i])
                    .append("%' OR ")
                    .append(KEY_AUTHOR).append(" LIKE '%").append(queryWords[i])
                    .append("%' OR ")
                    .append(KEY_DESCRIPTION).append(" LIKE '%").append(queryWords[i])
                    .append("%') ");

            if (i != queryWords.length - 1) {
                sb.append("AND ");
            }
        }
        sb.append(" AND " + KEY_IS_SUBSCRIBED + " =1 ");
        sb.append("ORDER BY " + KEY_TITLE + " ASC LIMIT 300");

        return db.rawQuery(sb.toString(), null);
    }
}
