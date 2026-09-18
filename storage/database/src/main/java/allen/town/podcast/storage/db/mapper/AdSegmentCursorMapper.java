package allen.town.podcast.storage.db.mapper;

import android.database.Cursor;

import androidx.annotation.NonNull;

import allen.town.podcast.model.feed.AdSegment;
import allen.town.podcast.storage.db.Db;

/**
 * Converts a {@link Cursor} over the {@code ad_segments} table to an {@link AdSegment} object.
 */
public abstract class AdSegmentCursorMapper {
    /**
     * Create an {@link AdSegment} instance from a database row (cursor).
     */
    @NonNull
    public static AdSegment convert(@NonNull Cursor cursor) {
        int indexId = cursor.getColumnIndex(Db.KEY_ID);
        int indexFeedItem = cursor.getColumnIndex(Db.KEY_FEEDITEM);
        int indexStart = cursor.getColumnIndex(Db.KEY_AD_START_MS);
        int indexEnd = cursor.getColumnIndex(Db.KEY_AD_END_MS);
        int indexSource = cursor.getColumnIndex(Db.KEY_AD_SOURCE);
        int indexConfidence = cursor.getColumnIndex(Db.KEY_AD_CONFIDENCE);
        int indexEnabled = cursor.getColumnIndex(Db.KEY_AD_ENABLED);

        long id = cursor.getLong(indexId);
        long feedItemId = cursor.getLong(indexFeedItem);
        long startMs = cursor.getLong(indexStart);
        long endMs = cursor.getLong(indexEnd);
        AdSegment.Source source = AdSegment.Source.fromInteger(cursor.getInt(indexSource));
        float confidence = cursor.getFloat(indexConfidence);
        boolean enabled = cursor.getInt(indexEnabled) > 0;
        return new AdSegment(id, feedItemId, startMs, endMs, source, confidence, enabled);
    }
}
