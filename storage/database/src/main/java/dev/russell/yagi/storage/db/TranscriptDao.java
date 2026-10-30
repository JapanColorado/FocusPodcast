package dev.russell.yagi.storage.db;

import static dev.russell.yagi.storage.db.DbSchema.KEY_FEEDITEM;
import static dev.russell.yagi.storage.db.DbSchema.KEY_TRANSCRIPT_FETCHED_AT;
import static dev.russell.yagi.storage.db.DbSchema.KEY_TRANSCRIPT_RAW;
import static dev.russell.yagi.storage.db.DbSchema.KEY_TRANSCRIPT_TEXT;
import static dev.russell.yagi.storage.db.DbSchema.KEY_TRANSCRIPT_TYPE;
import static dev.russell.yagi.storage.db.DbSchema.KEY_TRANSCRIPT_URL;
import static dev.russell.yagi.storage.db.DbSchema.TABLE_NAME_TRANSCRIPTS;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Owns the {@code transcripts} table: the cache of downloaded episode transcripts, one row per
 * episode. A row is replaced whole whenever a transcript is fetched again, and removed together
 * with its episode.
 */
class TranscriptDao extends Dao {

    TranscriptDao(SQLiteDatabase db) {
        super(db);
    }

    /**
     * The cached transcript of one episode: columns {@code transcript_url}, {@code transcript_type},
     * {@code raw}, {@code text} and {@code fetched_at}; no row when nothing is cached.
     */
    final Cursor getTranscriptCursor(final long feedItemId) {
        return db.query(TABLE_NAME_TRANSCRIPTS,
                new String[]{KEY_TRANSCRIPT_URL, KEY_TRANSCRIPT_TYPE, KEY_TRANSCRIPT_RAW,
                        KEY_TRANSCRIPT_TEXT, KEY_TRANSCRIPT_FETCHED_AT},
                KEY_FEEDITEM + "=?", new String[]{String.valueOf(feedItemId)}, null, null, null);
    }

    /** Stores (or replaces) the transcript of one episode. */
    void setTranscript(final long feedItemId, @Nullable final String url, @Nullable final String type,
                       @NonNull final String raw, @NonNull final String text, final long fetchedAt) {
        ContentValues values = new ContentValues();
        values.put(KEY_FEEDITEM, feedItemId);
        values.put(KEY_TRANSCRIPT_URL, url);
        values.put(KEY_TRANSCRIPT_TYPE, type);
        values.put(KEY_TRANSCRIPT_RAW, raw);
        values.put(KEY_TRANSCRIPT_TEXT, text);
        values.put(KEY_TRANSCRIPT_FETCHED_AT, fetchedAt);
        db.insertWithOnConflict(TABLE_NAME_TRANSCRIPTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Removes the cached transcripts of every listed episode (the episode delete cascade). */
    void deleteTranscriptsOfItems(final long[] itemIds) {
        if (itemIds == null || itemIds.length == 0) {
            return;
        }
        StringBuilder ids = new StringBuilder();
        for (long itemId : itemIds) {
            if (ids.length() != 0) {
                ids.append(",");
            }
            ids.append(itemId);
        }
        db.execSQL(String.format(Locale.US, "DELETE FROM %s WHERE %s IN (%s)",
                TABLE_NAME_TRANSCRIPTS, KEY_FEEDITEM, ids));
    }
}
