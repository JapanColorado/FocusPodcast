package dev.russell.focuspodcast.storage.db.mapper;

import android.database.Cursor;

import androidx.annotation.NonNull;

import dev.russell.focuspodcast.model.feed.Bookmark;
import dev.russell.focuspodcast.storage.db.Db;

/**
 * Converts a {@link Cursor} over the {@code bookmarks} table to a {@link Bookmark} object.
 */
public abstract class BookmarkCursorMapper {
    /**
     * Create a {@link Bookmark} instance from a database row (cursor).
     */
    @NonNull
    public static Bookmark convert(@NonNull Cursor cursor) {
        int indexId = cursor.getColumnIndexOrThrow(Db.KEY_ID);
        int indexFeedItem = cursor.getColumnIndexOrThrow(Db.KEY_FEEDITEM);
        int indexPosition = cursor.getColumnIndexOrThrow(Db.KEY_BOOKMARK_POSITION_MS);
        int indexNote = cursor.getColumnIndexOrThrow(Db.KEY_BOOKMARK_NOTE);
        int indexCreatedAt = cursor.getColumnIndexOrThrow(Db.KEY_BOOKMARK_CREATED_AT);
        return new Bookmark(
                cursor.getLong(indexId),
                cursor.getLong(indexFeedItem),
                cursor.getLong(indexPosition),
                cursor.isNull(indexNote) ? null : cursor.getString(indexNote),
                cursor.getLong(indexCreatedAt));
    }
}
