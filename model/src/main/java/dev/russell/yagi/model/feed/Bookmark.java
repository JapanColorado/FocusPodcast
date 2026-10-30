package dev.russell.yagi.model.feed;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A position inside an episode that the user marked, with an optional note. Bookmarks belong to
 * their episode and are removed together with it.
 */
public class Bookmark {

    private long id;
    private final long feedItemId;
    private final long positionMs;
    @Nullable
    private String note;
    private final long createdAt;
    /** The episode, filled in only by readers that list bookmarks together with their episodes. */
    @Nullable
    private FeedItem item;

    /** A bookmark not stored yet; {@link #getId()} is 0 until it is. */
    public Bookmark(long feedItemId, long positionMs, @Nullable String note, long createdAt) {
        this(0, feedItemId, positionMs, note, createdAt);
    }

    public Bookmark(long id, long feedItemId, long positionMs, @Nullable String note, long createdAt) {
        this.id = id;
        this.feedItemId = feedItemId;
        this.positionMs = positionMs;
        this.note = note;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getFeedItemId() {
        return feedItemId;
    }

    public long getPositionMs() {
        return positionMs;
    }

    /** The note, or null when the bookmark has none. */
    @Nullable
    public String getNote() {
        return note;
    }

    public void setNote(@Nullable String note) {
        this.note = note;
    }

    public boolean hasNote() {
        return note != null && !note.trim().isEmpty();
    }

    /** When the bookmark was made, in milliseconds since the epoch. */
    public long getCreatedAt() {
        return createdAt;
    }

    @Nullable
    public FeedItem getItem() {
        return item;
    }

    public void setItem(@Nullable FeedItem item) {
        this.item = item;
    }

    @NonNull
    @Override
    public String toString() {
        return "Bookmark{" + id + " item=" + feedItemId + " at " + positionMs + "}";
    }
}
