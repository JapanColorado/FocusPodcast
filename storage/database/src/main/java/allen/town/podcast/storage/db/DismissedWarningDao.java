package allen.town.podcast.storage.db;

import static allen.town.podcast.storage.db.DbSchema.KEY_DISMISSED_AT;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEED;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEEDFILE;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEEDFILETYPE;
import static allen.town.podcast.storage.db.DbSchema.KEY_ID;
import static allen.town.podcast.storage.db.DbSchema.KEY_REASON;
import static allen.town.podcast.storage.db.DbSchema.KEY_TITLE;
import static allen.town.podcast.storage.db.DbSchema.KEY_WARNING_KEY;
import static allen.town.podcast.storage.db.DbSchema.TABLE_NAME_DISMISSED_FEED_WARNINGS;
import static allen.town.podcast.storage.db.DbSchema.TABLE_NAME_DOWNLOAD_LOG;
import static allen.town.podcast.storage.db.DbSchema.TABLE_NAME_FEEDS;

import android.content.ContentValues;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import allen.town.podcast.model.feed.Feed;

/**
 * Owns the {@code dismissed_feed_warnings} table: the non-fatal feed warnings (identified by feed,
 * reason code and warning key) that the user chose never to see again. {@link DownloadLogDao}
 * consults it before storing a warning; dismissing also removes the matching log rows so the
 * warning disappears at once.
 */
class DismissedWarningDao extends Dao {

    DismissedWarningDao(SQLiteDatabase db) {
        super(db);
    }

    boolean isDismissed(long feedId, int reasonCode, String warningKey) {
        return DatabaseUtils.queryNumEntries(db, TABLE_NAME_DISMISSED_FEED_WARNINGS,
                KEY_FEED + "=? AND " + KEY_REASON + "=? AND " + KEY_WARNING_KEY + "=?",
                new String[]{String.valueOf(feedId), String.valueOf(reasonCode), warningKey}) > 0;
    }

    /** Records the dismissal and deletes the warning's rows from the download log. */
    void dismiss(long feedId, int reasonCode, String warningKey, String title) {
        inTransaction("dismissFeedWarning", () -> {
            ContentValues values = new ContentValues();
            values.put(KEY_FEED, feedId);
            values.put(KEY_REASON, reasonCode);
            values.put(KEY_WARNING_KEY, warningKey);
            values.put(KEY_TITLE, title);
            values.put(KEY_DISMISSED_AT, System.currentTimeMillis());
            db.insertWithOnConflict(TABLE_NAME_DISMISSED_FEED_WARNINGS, null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
            deleteLogRows(feedId, reasonCode, warningKey);
        });
    }

    /** Deletes the download-log rows of one feed warning. */
    void deleteLogRows(long feedId, int reasonCode, String warningKey) {
        db.delete(TABLE_NAME_DOWNLOAD_LOG,
                KEY_FEEDFILE + "=? AND " + KEY_FEEDFILETYPE + "=? AND " + KEY_REASON + "=? AND "
                        + KEY_WARNING_KEY + "=?",
                new String[]{String.valueOf(feedId), String.valueOf(Feed.FEEDFILETYPE_FEED),
                        String.valueOf(reasonCode), warningKey});
    }

    /** Takes back one dismissal (the snackbar's Undo). */
    void undismiss(long feedId, int reasonCode, String warningKey) {
        db.delete(TABLE_NAME_DISMISSED_FEED_WARNINGS,
                KEY_FEED + "=? AND " + KEY_REASON + "=? AND " + KEY_WARNING_KEY + "=?",
                new String[]{String.valueOf(feedId), String.valueOf(reasonCode), warningKey});
    }

    int countForFeed(long feedId) {
        return (int) DatabaseUtils.queryNumEntries(db, TABLE_NAME_DISMISSED_FEED_WARNINGS,
                KEY_FEED + "=?", new String[]{String.valueOf(feedId)});
    }

    /** Forgets every dismissal of one feed, so its warnings show again from the next refresh. */
    void deleteForFeed(long feedId) {
        db.delete(TABLE_NAME_DISMISSED_FEED_WARNINGS, KEY_FEED + "=?",
                new String[]{String.valueOf(feedId)});
    }

    /** Drops the dismissals of feeds that no longer exist. */
    void deleteOrphans() {
        db.delete(TABLE_NAME_DISMISSED_FEED_WARNINGS,
                KEY_FEED + " NOT IN (SELECT " + KEY_ID + " FROM " + TABLE_NAME_FEEDS + ")", null);
    }
}
