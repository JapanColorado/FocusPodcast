package allen.town.podcast.storage.db;

import static allen.town.podcast.storage.db.DbSchema.KEY_FEED;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEEDITEM;
import static allen.town.podcast.storage.db.DbSchema.KEY_ID;
import static allen.town.podcast.storage.db.DbSchema.SELECT_FEED_ITEMS_AND_MEDIA;
import static allen.town.podcast.storage.db.DbSchema.SELECT_KEY_ITEM_ID;
import static allen.town.podcast.storage.db.DbSchema.TABLE_NAME_QUEUE;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.List;

import allen.town.podcast.model.feed.FeedItem;

/**
 * Owns every statement against the {@code playlist} table: replacing the whole queue in one
 * transaction (the row id doubles as the position, so the queue is rewritten rather than patched),
 * clearing it, and the cursors that read it back — the queue joined with episodes and media, the
 * bare item ids in queue order, the item that follows a given one, and the queue size.
 */
class QueueDao extends Dao {

    QueueDao(SQLiteDatabase db) {
        super(db);
    }

    void setQueue(List<FeedItem> queue) {
        ContentValues values = new ContentValues();
        inTransaction("setQueue", () -> {
            db.delete(TABLE_NAME_QUEUE, null, null);
            for (int i = 0; i < queue.size(); i++) {
                FeedItem item = queue.get(i);
                values.put(KEY_ID, i);
                values.put(KEY_FEEDITEM, item.getId());
                values.put(KEY_FEED, item.getFeed().getId());
                db.insertWithOnConflict(TABLE_NAME_QUEUE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
        });
    }

    void clearQueue() {
        db.delete(TABLE_NAME_QUEUE, null, null);
    }

    /**
     * Returns a cursor which contains all feed items in the queue. The returned
     * cursor uses the FEEDITEM_SEL_FI_SMALL selection.
     */
    final Cursor getQueueCursor() {
        final String query = SELECT_FEED_ITEMS_AND_MEDIA
                + " INNER JOIN " + TABLE_NAME_QUEUE
                + " ON " + SELECT_KEY_ITEM_ID + " = " + TABLE_NAME_QUEUE + "." + KEY_FEEDITEM
                + " ORDER BY " + TABLE_NAME_QUEUE + "." + KEY_ID;
        return db.rawQuery(query, null);
    }

    Cursor getQueueIDCursor() {
        return db.query(TABLE_NAME_QUEUE, new String[]{KEY_FEEDITEM}, null, null, null, null, KEY_ID + " ASC", null);
    }

    Cursor getNextInQueue(final FeedItem item) {
        final String query = SELECT_FEED_ITEMS_AND_MEDIA
                + "INNER JOIN " + TABLE_NAME_QUEUE
                + " ON " + SELECT_KEY_ITEM_ID + " = " + TABLE_NAME_QUEUE + "." + KEY_FEEDITEM
                + " WHERE " + TABLE_NAME_QUEUE + "." + KEY_ID + " > (SELECT " + TABLE_NAME_QUEUE + "." + KEY_ID
                + " FROM " + TABLE_NAME_QUEUE + " WHERE " + TABLE_NAME_QUEUE + "." + KEY_FEEDITEM + " = "
                + item.getId()
                + ")"
                + " ORDER BY " + TABLE_NAME_QUEUE + "." + KEY_ID
                + " LIMIT 1";
        return db.rawQuery(query, null);
    }

    int getQueueSize() {
        final String query = String.format("SELECT COUNT(%s) FROM %s", KEY_ID, TABLE_NAME_QUEUE);
        Cursor c = db.rawQuery(query, null);
        int result = 0;
        if (c.moveToFirst()) {
            result = c.getInt(0);
        }
        c.close();
        return result;
    }
}
