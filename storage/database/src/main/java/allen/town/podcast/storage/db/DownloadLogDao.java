package allen.town.podcast.storage.db;

import static allen.town.podcast.storage.db.DbSchema.KEY_COMPLETION_DATE;
import static allen.town.podcast.storage.db.DbSchema.KEY_DOWNLOADSTATUS_TITLE;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEEDFILE;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEEDFILETYPE;
import static allen.town.podcast.storage.db.DbSchema.KEY_ID;
import static allen.town.podcast.storage.db.DbSchema.KEY_REASON;
import static allen.town.podcast.storage.db.DbSchema.KEY_REASON_DETAILED;
import static allen.town.podcast.storage.db.DbSchema.KEY_SUCCESSFUL;
import static allen.town.podcast.storage.db.DbSchema.SELECT_FEED_ITEMS_AND_MEDIA_AND_DOWNLOADLOG;
import static allen.town.podcast.storage.db.DbSchema.TABLE_NAME_DOWNLOAD_LOG;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import allen.town.podcast.model.download.DownloadError;
import allen.town.podcast.model.download.DownloadStatus;

/**
 * Owns every statement against the {@code download_log} table: recording a download status (a
 * successful download deletes its row, because only running and failed downloads interest the user,
 * and a repeated duplicate-episode warning replaces its previous row instead of accumulating), and
 * the cursors that read the log back for one feed file or as a limited recent list.
 */
class DownloadLogDao extends Dao {

    DownloadLogDao(SQLiteDatabase db) {
        super(db);
    }

    /**
     * Inserts or updates a download status.
     */
    long setDownloadStatus(DownloadStatus status) {
        if (status.getFeedfileId() == 0) {
//            feedId=0 means the feed was not subscribed at the time, recording the sync state is not what the user wants
            Log.d(TAG, "ignore feed id =0 download status " + status.getTitle());
            return 0;
        }
        if (status.isSuccessful()) {
            // On success, drop the record: the user only cares about downloads that are running or failed
            db.delete(TABLE_NAME_DOWNLOAD_LOG, KEY_ID + "=?",
                    new String[]{String.valueOf(status.getId())});
            return status.getId();
        }
        if (status.getReason() == DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE
                && status.getId() == 0) {
            // A duplicate-episode warning is re-reported on every refresh for as long as the
            // podcast host keeps the duplicate in the feed. Keep a single row per episode and
            // let it move to the top instead of accumulating one entry per refresh.
            db.delete(TABLE_NAME_DOWNLOAD_LOG,
                    KEY_FEEDFILE + "=? AND " + KEY_FEEDFILETYPE + "=? AND " + KEY_REASON + "=? AND " + KEY_DOWNLOADSTATUS_TITLE + "=?",
                    new String[]{String.valueOf(status.getFeedfileId()), String.valueOf(status.getFeedfileType()),
                            String.valueOf(status.getReason().getCode()), String.valueOf(status.getTitle())});
        }
        ContentValues values = new ContentValues();
        values.put(KEY_FEEDFILE, status.getFeedfileId());
        values.put(KEY_FEEDFILETYPE, status.getFeedfileType());
        values.put(KEY_REASON, status.getReason().getCode());
        values.put(KEY_SUCCESSFUL, status.isSuccessful());
        values.put(KEY_COMPLETION_DATE, status.getCompletionDate().getTime());
        values.put(KEY_REASON_DETAILED, status.getReasonDetailed());
        values.put(KEY_DOWNLOADSTATUS_TITLE, status.getTitle());
        if (status.getId() == 0) {
            status.setId(db.insert(TABLE_NAME_DOWNLOAD_LOG, null, values));
        } else {
            db.update(TABLE_NAME_DOWNLOAD_LOG, values, KEY_ID + "=?",
                    new String[]{String.valueOf(status.getId())});
        }
        return status.getId();
    }

    void clearDownloadLog() {
        db.delete(TABLE_NAME_DOWNLOAD_LOG, null, null);
    }

    final Cursor getDownloadLog(final int feedFileType, final long feedFileId) {
        final String query = "SELECT * FROM " + TABLE_NAME_DOWNLOAD_LOG
                + " WHERE " + KEY_FEEDFILE + "=" + feedFileId + " AND " + KEY_FEEDFILETYPE + "=" + feedFileType
                + " ORDER BY " + KEY_ID + " DESC";
        return db.rawQuery(query, null);
    }

    final Cursor getDownloadLogCursor(final int limit) {
        return db.query(TABLE_NAME_DOWNLOAD_LOG, null, null, null, null,
                null, KEY_COMPLETION_DATE + " DESC LIMIT " + limit);
    }

    final Cursor getFeedIdFromDownloadLogCursor(final int limit) {
        return db.rawQuery(SELECT_FEED_ITEMS_AND_MEDIA_AND_DOWNLOADLOG, null);
    }
}
