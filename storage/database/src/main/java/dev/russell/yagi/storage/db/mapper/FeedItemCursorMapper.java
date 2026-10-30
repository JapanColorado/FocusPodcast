package dev.russell.yagi.storage.db.mapper;

import android.database.Cursor;
import androidx.annotation.NonNull;
import dev.russell.yagi.model.feed.FeedItem;
import dev.russell.yagi.storage.db.Db;

import java.util.Date;

/**
 * Converts a {@link Cursor} to a {@link FeedItem} object.
 */
public abstract class FeedItemCursorMapper {
    /**
     * Create a {@link FeedItem} instance from a database row (cursor).
     */
    @NonNull
    public static FeedItem convert(@NonNull Cursor cursor) {
        int indexId = cursor.getColumnIndexOrThrow(Db.SELECT_KEY_ITEM_ID);
        int indexTitle = cursor.getColumnIndexOrThrow(Db.KEY_TITLE);
        int indexLink = cursor.getColumnIndexOrThrow(Db.KEY_LINK);
        int indexPubDate = cursor.getColumnIndexOrThrow(Db.KEY_PUBDATE);
        int indexPaymentLink = cursor.getColumnIndexOrThrow(Db.KEY_PAYMENT_LINK);
        int indexFeedId = cursor.getColumnIndexOrThrow(Db.KEY_FEED);
        int indexHasChapters = cursor.getColumnIndexOrThrow(Db.KEY_HAS_CHAPTERS);
        int indexRead = cursor.getColumnIndexOrThrow(Db.KEY_READ);
        int indexItemIdentifier = cursor.getColumnIndexOrThrow(Db.KEY_ITEM_IDENTIFIER);
        int indexAutoDownload = cursor.getColumnIndexOrThrow(Db.KEY_AUTO_DOWNLOAD_ATTEMPTS);
        int indexImageUrl = cursor.getColumnIndexOrThrow(Db.KEY_IMAGE_URL);
        int indexPodcastIndexChapterUrl = cursor.getColumnIndexOrThrow(Db.KEY_PODCASTINDEX_CHAPTER_URL);

        long id = cursor.getInt(indexId);
        String title = cursor.getString(indexTitle);
        String link = cursor.getString(indexLink);
        Date pubDate = new Date(cursor.getLong(indexPubDate));
        String paymentLink = cursor.getString(indexPaymentLink);
        long feedId = cursor.getLong(indexFeedId);
        boolean hasChapters = cursor.getInt(indexHasChapters) > 0;
        int state = cursor.getInt(indexRead);
        String itemIdentifier = cursor.getString(indexItemIdentifier);
        long autoDownload = cursor.getLong(indexAutoDownload);
        String imageUrl = cursor.getString(indexImageUrl);
        String podcastIndexChapterUrl = cursor.getString(indexPodcastIndexChapterUrl);

        FeedItem item = new FeedItem(id, title, link, pubDate, paymentLink, feedId,
                hasChapters, imageUrl, state, itemIdentifier, autoDownload, podcastIndexChapterUrl);
        // Version 7 columns; projections that do not select them leave the numbers unknown (0).
        int indexDisc = cursor.getColumnIndex(Db.KEY_DISC_NUMBER);
        if (indexDisc >= 0) {
            item.setDiscNumber(cursor.getInt(indexDisc));
        }
        int indexTrack = cursor.getColumnIndex(Db.KEY_TRACK_NUMBER);
        if (indexTrack >= 0) {
            item.setTrackNumber(cursor.getInt(indexTrack));
        }
        // Version 9 columns, likewise optional.
        int indexTranscriptUrl = cursor.getColumnIndex(Db.KEY_TRANSCRIPT_URL);
        int indexTranscriptType = cursor.getColumnIndex(Db.KEY_TRANSCRIPT_TYPE);
        if (indexTranscriptUrl >= 0) {
            item.setTranscript(cursor.getString(indexTranscriptUrl),
                    indexTranscriptType >= 0 ? cursor.getString(indexTranscriptType) : null);
        }
        return item;
    }
}
