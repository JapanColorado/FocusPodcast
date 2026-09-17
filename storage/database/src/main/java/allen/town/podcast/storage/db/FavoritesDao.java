package allen.town.podcast.storage.db;

import static allen.town.podcast.storage.db.DbSchema.KEY_FEED;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEEDITEM;
import static allen.town.podcast.storage.db.DbSchema.KEY_ID;
import static allen.town.podcast.storage.db.DbSchema.KEY_PUBDATE;
import static allen.town.podcast.storage.db.DbSchema.SELECT_FEED_ITEMS_AND_MEDIA;
import static allen.town.podcast.storage.db.DbSchema.SELECT_KEY_ITEM_ID;
import static allen.town.podcast.storage.db.DbSchema.TABLE_NAME_FAVORITES;
import static allen.town.podcast.storage.db.DbSchema.TABLE_NAME_FEED_ITEMS;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.List;
import java.util.Locale;

import allen.town.podcast.model.feed.FeedItem;

/**
 * Owns every statement against the {@code favorites} table: replacing the whole favorites list in
 * one transaction, adding or removing a single item (adding is a no-op when the item is already
 * there), and the paged cursor that reads favorites back joined with their episodes and media,
 * newest publication date first.
 */
class FavoritesDao extends Dao {

    FavoritesDao(SQLiteDatabase db) {
        super(db);
    }

    void setFavorites(List<FeedItem> favorites) {
        ContentValues values = new ContentValues();
        inTransaction("setFavorites", () -> {
            db.delete(TABLE_NAME_FAVORITES, null, null);
            for (int i = 0; i < favorites.size(); i++) {
                FeedItem item = favorites.get(i);
                values.put(KEY_ID, i);
                values.put(KEY_FEEDITEM, item.getId());
                values.put(KEY_FEED, item.getFeed().getId());
                db.insertWithOnConflict(TABLE_NAME_FAVORITES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
        });
    }

    /**
     * Adds the item to favorites
     */
    void addFavoriteItem(FeedItem item) {
        // don't add an item that's already there...
        if (isItemInFavorites(item)) {
            Log.d(TAG, "item already in favorites");
            return;
        }
        ContentValues values = new ContentValues();
        values.put(KEY_FEEDITEM, item.getId());
        values.put(KEY_FEED, item.getFeedId());
        db.insert(TABLE_NAME_FAVORITES, null, values);
    }

    void removeFavoriteItem(FeedItem item) {
        String deleteClause = String.format("DELETE FROM %s WHERE %s=%s AND %s=%s",
                TABLE_NAME_FAVORITES,
                KEY_FEEDITEM, item.getId(),
                KEY_FEED, item.getFeedId());
        db.execSQL(deleteClause);
    }

    private boolean isItemInFavorites(FeedItem item) {
        String query = String.format(Locale.US, "SELECT %s from %s WHERE %s=%d",
                KEY_ID, TABLE_NAME_FAVORITES, KEY_FEEDITEM, item.getId());
        Cursor c = db.rawQuery(query, null);
        int count = c.getCount();
        c.close();
        return count > 0;
    }

    final Cursor getFavoritesCursor(int offset, int limit) {
        final String query = SELECT_FEED_ITEMS_AND_MEDIA
                + " INNER JOIN " + TABLE_NAME_FAVORITES
                + " ON " + SELECT_KEY_ITEM_ID + " = " + TABLE_NAME_FAVORITES + "." + KEY_FEEDITEM
                + " ORDER BY " + TABLE_NAME_FEED_ITEMS + "." + KEY_PUBDATE + " DESC"
                + " LIMIT " + offset + ", " + limit;
        return db.rawQuery(query, null);
    }
}
