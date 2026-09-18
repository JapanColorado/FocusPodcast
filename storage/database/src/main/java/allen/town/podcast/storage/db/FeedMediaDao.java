package allen.town.podcast.storage.db;

import static allen.town.podcast.storage.db.DbSchema.KEYS_FEED_MEDIA;
import static allen.town.podcast.storage.db.DbSchema.KEY_DOWNLOADED;
import static allen.town.podcast.storage.db.DbSchema.KEY_DOWNLOAD_URL;
import static allen.town.podcast.storage.db.DbSchema.KEY_DURATION;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEED;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEEDITEM;
import static allen.town.podcast.storage.db.DbSchema.KEY_FILE_URL;
import static allen.town.podcast.storage.db.DbSchema.KEY_HAS_EMBEDDED_PICTURE;
import static allen.town.podcast.storage.db.DbSchema.KEY_ID;
import static allen.town.podcast.storage.db.DbSchema.KEY_LAST_PLAYED_TIME;
import static allen.town.podcast.storage.db.DbSchema.KEY_MIME_TYPE;
import static allen.town.podcast.storage.db.DbSchema.KEY_PLAYBACK_COMPLETION_DATE;
import static allen.town.podcast.storage.db.DbSchema.KEY_PLAYED_DURATION;
import static allen.town.podcast.storage.db.DbSchema.KEY_POSITION;
import static allen.town.podcast.storage.db.DbSchema.KEY_SIZE;
import static allen.town.podcast.storage.db.DbSchema.TABLE_NAME_FEED_ITEMS;
import static allen.town.podcast.storage.db.DbSchema.TABLE_NAME_FEED_MEDIA;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.Locale;

import allen.town.podcast.model.feed.FeedMedia;

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
}
