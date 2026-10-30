package dev.russell.yagi.storage.db;

import static dev.russell.yagi.storage.db.DbSchema.KEYS_FEED_MEDIA;
import static dev.russell.yagi.storage.db.DbSchema.KEY_DOWNLOADED;
import static dev.russell.yagi.storage.db.DbSchema.KEY_DOWNLOAD_URL;
import static dev.russell.yagi.storage.db.DbSchema.KEY_DURATION;
import static dev.russell.yagi.storage.db.DbSchema.KEY_FEED;
import static dev.russell.yagi.storage.db.DbSchema.KEY_FEEDITEM;
import static dev.russell.yagi.storage.db.DbSchema.KEY_FILE_URL;
import static dev.russell.yagi.storage.db.DbSchema.KEY_HAS_EMBEDDED_PICTURE;
import static dev.russell.yagi.storage.db.DbSchema.KEY_ID;
import static dev.russell.yagi.storage.db.DbSchema.KEY_LAST_PLAYED_TIME;
import static dev.russell.yagi.storage.db.DbSchema.KEY_MIME_TYPE;
import static dev.russell.yagi.storage.db.DbSchema.KEY_PLAYBACK_COMPLETION_DATE;
import static dev.russell.yagi.storage.db.DbSchema.KEY_PLAYED_DURATION;
import static dev.russell.yagi.storage.db.DbSchema.KEY_POSITION;
import static dev.russell.yagi.storage.db.DbSchema.KEY_SIZE;
import static dev.russell.yagi.storage.db.DbSchema.KEY_AUDIOBOOK;
import static dev.russell.yagi.storage.db.DbSchema.KEY_CUSTOM_TITLE;
import static dev.russell.yagi.storage.db.DbSchema.KEY_IMAGE_URL;
import static dev.russell.yagi.storage.db.DbSchema.KEY_IS_SUBSCRIBED;
import static dev.russell.yagi.storage.db.DbSchema.KEY_READ;
import static dev.russell.yagi.storage.db.DbSchema.KEY_TITLE;
import static dev.russell.yagi.storage.db.DbSchema.TABLE_NAME_FAVORITES;
import static dev.russell.yagi.storage.db.DbSchema.TABLE_NAME_FEEDS;
import static dev.russell.yagi.storage.db.DbSchema.TABLE_NAME_FEED_ITEMS;
import static dev.russell.yagi.storage.db.DbSchema.TABLE_NAME_FEED_MEDIA;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.Locale;

import dev.russell.yagi.model.feed.FeedMedia;

/**
 * Owns every statement against the {@code medias} table: inserting and updating a media row as a
 * whole, the three narrow updates that write only the download state, only the playback position
 * information or only the completion date (so a stale in-memory snapshot cannot clobber the rest of
 * the row), the feed-derived column update used by a refresh merge, and the cursors and counts that
 * read media rows back, including the playback history and the monthly listening statistics.
 */
class FeedMediaDao extends Dao {

    FeedMediaDao(SQLiteDatabase db) {
        super(db);
    }

    /**
     * Inserts or updates a media entry
     *
     * @return the id of the entry
     */
    long setMedia(FeedMedia media) {
        ContentValues values = new ContentValues();
        values.put(KEY_DURATION, media.getDuration());
        values.put(KEY_POSITION, media.getPosition());
        values.put(KEY_SIZE, media.getSize());
        values.put(KEY_MIME_TYPE, media.getMime_type());
        values.put(KEY_DOWNLOAD_URL, media.getDownload_url());
        values.put(KEY_DOWNLOADED, media.isDownloaded());
        values.put(KEY_FILE_URL, media.getFile_url());
        values.put(KEY_HAS_EMBEDDED_PICTURE, media.hasEmbeddedPicture());
        values.put(KEY_LAST_PLAYED_TIME, media.getLastPlayedTime());

        if (media.getPlaybackCompletionDate() != null) {
            values.put(KEY_PLAYBACK_COMPLETION_DATE, media.getPlaybackCompletionDate().getTime());
        } else {
            values.put(KEY_PLAYBACK_COMPLETION_DATE, 0);
        }
        if (media.getItem() != null) {
            values.put(KEY_FEEDITEM, media.getItem().getId());
        }
        if (media.getId() == 0) {
            media.setId(db.insert(TABLE_NAME_FEED_MEDIA, null, values));
        } else {
            db.update(TABLE_NAME_FEED_MEDIA, values, KEY_ID + "=?",
                    new String[]{String.valueOf(media.getId())});
        }
        return media.getId();
    }

    /**
     * Updates only the columns of a media row that come from the feed itself.
     */
    void setMediaFeedData(FeedMedia media) {
        ContentValues values = new ContentValues();
        values.put(KEY_SIZE, media.getSize());
        values.put(KEY_MIME_TYPE, media.getMime_type());
        values.put(KEY_DOWNLOAD_URL, media.getDownload_url());
        if (media.getDuration() > 0) {
            values.put(KEY_DURATION, media.getDuration());
        }
        if (media.getItem() != null) {
            values.put(KEY_FEEDITEM, media.getItem().getId());
        }
        db.update(TABLE_NAME_FEED_MEDIA, values, KEY_ID + "=?",
                new String[]{String.valueOf(media.getId())});
    }

    /**
     * Saves only the download-related columns (downloaded flag, file url, embedded picture)
     * so that a stale in-memory snapshot cannot clobber playback position etc.
     */
    void setFeedMediaDownloadState(FeedMedia media) {
        if (media.getId() != 0) {
            ContentValues values = new ContentValues();
            values.put(KEY_DOWNLOADED, media.isDownloaded());
            values.put(KEY_FILE_URL, media.getFile_url());
            values.put(KEY_HAS_EMBEDDED_PICTURE, media.hasEmbeddedPicture());
            db.update(TABLE_NAME_FEED_MEDIA, values, KEY_ID + "=?",
                    new String[]{String.valueOf(media.getId())});
        } else {
            Log.e(TAG, "setFeedMediaDownloadState: ID of media was 0");
        }
    }

    void setFeedMediaPlaybackInformation(FeedMedia media) {
        if (media.getId() != 0) {
            ContentValues values = new ContentValues();
            values.put(KEY_POSITION, media.getPosition());
            values.put(KEY_DURATION, media.getDuration());
            values.put(KEY_PLAYED_DURATION, media.getPlayedDuration());
            values.put(KEY_LAST_PLAYED_TIME, media.getLastPlayedTime());
            db.update(TABLE_NAME_FEED_MEDIA, values, KEY_ID + "=?",
                    new String[]{String.valueOf(media.getId())});
        } else {
            Log.e(TAG, "setFeedMediaPlaybackInformation: ID of media was 0");
        }
    }

    void setFeedMediaPlaybackCompletionDate(FeedMedia media) {
        if (media.getId() != 0) {
            ContentValues values = new ContentValues();
            values.put(KEY_PLAYBACK_COMPLETION_DATE, media.getPlaybackCompletionDate().getTime());
            values.put(KEY_PLAYED_DURATION, media.getPlayedDuration());
            db.update(TABLE_NAME_FEED_MEDIA, values, KEY_ID + "=?",
                    new String[]{String.valueOf(media.getId())});
        } else {
            Log.e(TAG, "setFeedMediaPlaybackCompletionDate: ID of media was 0");
        }
    }

    /**
     * Resets the playback duration of all podcasts to 0.
     */
    void resetAllMediaPlayedDuration() {
        inTransaction("resetAllMediaPlayedDuration", () -> {
            ContentValues values = new ContentValues();
            values.put(KEY_PLAYED_DURATION, 0);
            db.update(TABLE_NAME_FEED_MEDIA, values, null, new String[0]);
        });
    }

    /**
     * Resets the playback duration of every episode of one feed to 0, removing that feed
     * from the playback statistics without touching its playback history or positions.
     */
    void resetMediaPlayedDurationForFeed(long feedId) {
        inTransaction("resetMediaPlayedDurationForFeed", () -> {
            ContentValues values = new ContentValues();
            values.put(KEY_PLAYED_DURATION, 0);
            db.update(TABLE_NAME_FEED_MEDIA, values,
                    KEY_FEEDITEM + " IN (SELECT " + KEY_ID + " FROM " + TABLE_NAME_FEED_ITEMS
                            + " WHERE " + KEY_FEED + " = ?)",
                    new String[]{String.valueOf(feedId)});
        });
    }

    void clearPlaybackHistory() {
        ContentValues values = new ContentValues();
        values.put(KEY_PLAYBACK_COMPLETION_DATE, 0);
        db.update(TABLE_NAME_FEED_MEDIA, values, null, null);
    }

    /**
     * Wipes the playback history of one feed: completion dates, played durations and last
     * played times of all of its episodes. Playback positions and played flags are kept.
     */
    void clearPlaybackHistoryForFeed(long feedId) {
        inTransaction("clearPlaybackHistoryForFeed", () -> {
            ContentValues values = new ContentValues();
            values.put(KEY_PLAYBACK_COMPLETION_DATE, 0);
            values.put(KEY_PLAYED_DURATION, 0);
            values.put(KEY_LAST_PLAYED_TIME, 0);
            db.update(TABLE_NAME_FEED_MEDIA, values,
                    KEY_FEEDITEM + " IN (SELECT " + KEY_ID + " FROM " + TABLE_NAME_FEED_ITEMS
                            + " WHERE " + KEY_FEED + " = ?)",
                    new String[]{String.valueOf(feedId)});
        });
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
    final Cursor getCompletedMediaCursor(int offset, int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Limit must be >= 0");
        }

        return db.query(TABLE_NAME_FEED_MEDIA, null,
                KEY_PLAYBACK_COMPLETION_DATE + " > 0", null, null,
                null, String.format(Locale.US, "%s DESC LIMIT %d, %d", KEY_PLAYBACK_COMPLETION_DATE, offset, limit));
    }

    final long getCompletedMediaLength() {
        return DatabaseUtils.queryNumEntries(db, TABLE_NAME_FEED_MEDIA, KEY_PLAYBACK_COMPLETION_DATE + "> 0");
    }

    final Cursor getSingleFeedMediaCursor(long id) {
        final String query = "SELECT " + KEYS_FEED_MEDIA + " FROM " + TABLE_NAME_FEED_MEDIA
                + " WHERE " + KEY_ID + "=" + id;
        return db.rawQuery(query, null);
    }

    final Cursor getMonthlyStatisticsCursor() {
        final String query = "SELECT SUM(" + KEY_PLAYED_DURATION + ") AS total_duration"
                + ", strftime('%m', datetime(" + KEY_LAST_PLAYED_TIME + "/1000, 'unixepoch')) AS month"
                + ", strftime('%Y', datetime(" + KEY_LAST_PLAYED_TIME + "/1000, 'unixepoch')) AS year"
                + " FROM " + TABLE_NAME_FEED_MEDIA
                + " WHERE " + KEY_LAST_PLAYED_TIME + " > 0 AND " + KEY_PLAYED_DURATION + " > 0"
                + " GROUP BY year, month"
                + " ORDER BY year, month";
        return db.rawQuery(query, null);
    }

    final int getNumberOfDownloadedEpisodes() {
        final String query = "SELECT COUNT(DISTINCT " + KEY_ID + ") AS count FROM " + TABLE_NAME_FEED_MEDIA
                + " WHERE " + KEY_DOWNLOADED + " > 0";

        Cursor c = db.rawQuery(query, null);
        int result = 0;
        if (c.moveToFirst()) {
            result = c.getInt(0);
        }
        c.close();
        return result;
    }

    /** Column aliases of {@link #getDownloadedMediaWithFeedCursor()}. */
    static final String STORAGE_MEDIA_ID = "media_id";
    static final String STORAGE_FILE_URL = "media_file_url";
    static final String STORAGE_SIZE = "media_size";
    static final String STORAGE_READ = "item_read";
    static final String STORAGE_FAVORITE = "item_favorite";
    static final String STORAGE_FEED_ID = "feed_id";
    static final String STORAGE_FEED_TITLE = "feed_title";
    static final String STORAGE_FEED_CUSTOM_TITLE = "feed_custom_title";
    static final String STORAGE_FEED_IMAGE_URL = "feed_image_url";
    static final String STORAGE_FEED_SUBSCRIBED = "feed_subscribed";
    static final String STORAGE_FEED_AUDIOBOOK = "feed_audiobook";

    /**
     * Every downloaded media row together with the little the storage overview needs about its
     * episode (played, favourite) and feed (title, cover, subscribed, audiobook), in one query.
     */
    final Cursor getDownloadedMediaWithFeedCursor() {
        final String query = "SELECT "
                + TABLE_NAME_FEED_MEDIA + "." + KEY_ID + " AS " + STORAGE_MEDIA_ID + ", "
                + TABLE_NAME_FEED_MEDIA + "." + KEY_FILE_URL + " AS " + STORAGE_FILE_URL + ", "
                + TABLE_NAME_FEED_MEDIA + "." + KEY_SIZE + " AS " + STORAGE_SIZE + ", "
                + TABLE_NAME_FEED_ITEMS + "." + KEY_READ + " AS " + STORAGE_READ + ", "
                + "EXISTS (SELECT 1 FROM " + TABLE_NAME_FAVORITES + " WHERE "
                + TABLE_NAME_FAVORITES + "." + KEY_FEEDITEM + " = " + TABLE_NAME_FEED_ITEMS + "." + KEY_ID
                + ") AS " + STORAGE_FAVORITE + ", "
                + TABLE_NAME_FEEDS + "." + KEY_ID + " AS " + STORAGE_FEED_ID + ", "
                + TABLE_NAME_FEEDS + "." + KEY_TITLE + " AS " + STORAGE_FEED_TITLE + ", "
                + TABLE_NAME_FEEDS + "." + KEY_CUSTOM_TITLE + " AS " + STORAGE_FEED_CUSTOM_TITLE + ", "
                + TABLE_NAME_FEEDS + "." + KEY_IMAGE_URL + " AS " + STORAGE_FEED_IMAGE_URL + ", "
                + TABLE_NAME_FEEDS + "." + KEY_IS_SUBSCRIBED + " AS " + STORAGE_FEED_SUBSCRIBED + ", "
                + TABLE_NAME_FEEDS + "." + KEY_AUDIOBOOK + " AS " + STORAGE_FEED_AUDIOBOOK
                + " FROM " + TABLE_NAME_FEED_MEDIA
                + " INNER JOIN " + TABLE_NAME_FEED_ITEMS + " ON "
                + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + " = " + TABLE_NAME_FEED_MEDIA + "." + KEY_FEEDITEM
                + " INNER JOIN " + TABLE_NAME_FEEDS + " ON "
                + TABLE_NAME_FEEDS + "." + KEY_ID + " = " + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED
                + " WHERE " + TABLE_NAME_FEED_MEDIA + "." + KEY_DOWNLOADED + " > 0";
        return db.rawQuery(query, null);
    }
}
