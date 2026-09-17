package allen.town.podcast.storage.db;

import static allen.town.podcast.storage.db.DbSchema.KEY_AD_CONFIDENCE;
import static allen.town.podcast.storage.db.DbSchema.KEY_AD_ENABLED;
import static allen.town.podcast.storage.db.DbSchema.KEY_AD_END_MS;
import static allen.town.podcast.storage.db.DbSchema.KEY_AD_SOURCE;
import static allen.town.podcast.storage.db.DbSchema.KEY_AD_START_MS;
import static allen.town.podcast.storage.db.DbSchema.KEY_FEEDITEM;
import static allen.town.podcast.storage.db.DbSchema.KEY_ID;
import static allen.town.podcast.storage.db.DbSchema.TABLE_NAME_AD_SEGMENTS;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Locale;

import allen.town.podcast.model.feed.AdSegment;

/**
 * Owns every statement against the {@code ad_segments} table: the cursor that reads one episode's
 * advertisement ranges back in playback order, the transactional replacement of everything one
 * producer ({@link AdSegment.Source}) contributed for an episode, the single-row insert used when
 * the user marks a range by hand, the enabled toggle, and the deletes — both of one segment and of
 * every segment belonging to a set of episodes that is being removed.
 *
 * <p>Segments of different sources never overwrite each other: the detector re-running only
 * replaces its own {@link AdSegment.Source#DETECTED} rows and leaves chapter-derived and manual
 * ones alone.</p>
 */
class AdSegmentDao extends Dao {

    AdSegmentDao(SQLiteDatabase db) {
        super(db);
    }

    /** All segments of one episode, earliest start first. */
    final Cursor getAdSegmentsCursor(final long feedItemId) {
        return db.query(TABLE_NAME_AD_SEGMENTS, null, KEY_FEEDITEM + "=?",
                new String[]{String.valueOf(feedItemId)}, null, null, KEY_AD_START_MS + " ASC");
    }

    /**
     * Replaces everything {@code source} contributed for {@code feedItemId} with {@code segments}
     * in one transaction, so a reader never sees the episode without its segments.
     */
    void replaceAdSegments(final long feedItemId, @NonNull final AdSegment.Source source,
                           @NonNull final List<AdSegment> segments) {
        inTransaction("replaceAdSegments", () -> {
            db.delete(TABLE_NAME_AD_SEGMENTS,
                    KEY_FEEDITEM + "=? AND " + KEY_AD_SOURCE + "=?",
                    new String[]{String.valueOf(feedItemId), String.valueOf(source.toInteger())});
            for (AdSegment segment : segments) {
                db.insert(TABLE_NAME_AD_SEGMENTS, null, toContentValues(segment, feedItemId));
            }
        });
    }

    /** Inserts one segment and returns its new row id, which is also written back onto the object. */
    long insertAdSegment(@NonNull final AdSegment segment) {
        long id = db.insert(TABLE_NAME_AD_SEGMENTS, null,
                toContentValues(segment, segment.getFeedItemId()));
        if (id > 0) {
            segment.setId(id);
        }
        return id;
    }

    void setAdSegmentEnabled(final long id, final boolean enabled) {
        ContentValues values = new ContentValues();
        values.put(KEY_AD_ENABLED, enabled);
        db.update(TABLE_NAME_AD_SEGMENTS, values, KEY_ID + "=?", new String[]{String.valueOf(id)});
    }

    void deleteAdSegment(final long id) {
        db.delete(TABLE_NAME_AD_SEGMENTS, KEY_ID + "=?", new String[]{String.valueOf(id)});
    }

    /**
     * Removes the segments of every listed episode. Called from the episode delete cascade so that
     * segments never outlive the item they describe.
     */
    void deleteAdSegmentsOfItems(final long[] itemIds) {
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
                TABLE_NAME_AD_SEGMENTS, KEY_FEEDITEM, ids));
    }

    private static ContentValues toContentValues(@NonNull AdSegment segment, long feedItemId) {
        ContentValues values = new ContentValues();
        values.put(KEY_FEEDITEM, feedItemId);
        values.put(KEY_AD_START_MS, segment.getStartMs());
        values.put(KEY_AD_END_MS, segment.getEndMs());
        values.put(KEY_AD_SOURCE, segment.getSource().toInteger());
        values.put(KEY_AD_CONFIDENCE, segment.getConfidence());
        values.put(KEY_AD_ENABLED, segment.isEnabled());
        return values;
    }
}
