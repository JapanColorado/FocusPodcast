package dev.russell.focuspodcast.storage.db;

import static dev.russell.focuspodcast.storage.db.DbSchema.KEY_BOOKMARK_CREATED_AT;
import static dev.russell.focuspodcast.storage.db.DbSchema.KEY_BOOKMARK_NOTE;
import static dev.russell.focuspodcast.storage.db.DbSchema.KEY_BOOKMARK_POSITION_MS;
import static dev.russell.focuspodcast.storage.db.DbSchema.KEY_FEEDITEM;
import static dev.russell.focuspodcast.storage.db.DbSchema.KEY_ID;
import static dev.russell.focuspodcast.storage.db.DbSchema.TABLE_NAME_BOOKMARKS;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import dev.russell.focuspodcast.model.feed.Bookmark;

/**
 * Owns the {@code bookmarks} table: the cursors that read one episode's bookmarks in playback
 * order and every bookmark newest first, the insert (which keeps a given id, so a deleted
 * bookmark can be put back by an undo), the note update, and the deletes of one bookmark and of
 * every bookmark of a set of episodes that is being removed.
 */
class BookmarkDao extends Dao {

    BookmarkDao(SQLiteDatabase db) {
        super(db);
    }

    /** The bookmarks of one episode, earliest position first. */
    final Cursor getBookmarksOfItemCursor(final long feedItemId) {
        return db.query(TABLE_NAME_BOOKMARKS, null, KEY_FEEDITEM + "=?",
                new String[]{String.valueOf(feedItemId)}, null, null,
                KEY_BOOKMARK_POSITION_MS + " ASC, " + KEY_ID + " ASC");
    }

    /** Every bookmark, most recently made first. */
    final Cursor getAllBookmarksCursor() {
        return db.query(TABLE_NAME_BOOKMARKS, null, null, null, null, null,
                KEY_BOOKMARK_CREATED_AT + " DESC, " + KEY_ID + " DESC");
    }

    /**
     * Inserts one bookmark and returns its row id, which is also written back onto the object. A
     * bookmark that already has an id (one being restored) keeps it.
     */
    long insertBookmark(@NonNull final Bookmark bookmark) {
        ContentValues values = new ContentValues();
        if (bookmark.getId() > 0) {
            values.put(KEY_ID, bookmark.getId());
        }
        values.put(KEY_FEEDITEM, bookmark.getFeedItemId());
        values.put(KEY_BOOKMARK_POSITION_MS, bookmark.getPositionMs());
        values.put(KEY_BOOKMARK_NOTE, bookmark.getNote());
        values.put(KEY_BOOKMARK_CREATED_AT, bookmark.getCreatedAt());
        long id = db.insertWithOnConflict(TABLE_NAME_BOOKMARKS, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
        if (id > 0) {
            bookmark.setId(id);
        }
        return id;
    }

    /** Replaces the note of one bookmark; null removes it. */
    void setBookmarkNote(final long id, @Nullable final String note) {
        ContentValues values = new ContentValues();
        values.put(KEY_BOOKMARK_NOTE, note);
        db.update(TABLE_NAME_BOOKMARKS, values, KEY_ID + "=?", new String[]{String.valueOf(id)});
    }

    void deleteBookmark(final long id) {
        db.delete(TABLE_NAME_BOOKMARKS, KEY_ID + "=?", new String[]{String.valueOf(id)});
    }

    /** Removes the bookmarks of every listed episode (the episode delete cascade). */
    void deleteBookmarksOfItems(final long[] itemIds) {
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
                TABLE_NAME_BOOKMARKS, KEY_FEEDITEM, ids));
    }
}
