package allen.town.podcast.core.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One downloaded media row as {@link DBReader#getDownloadedMediaWithFeed()} reads it for the
 * storage overview: where the file is, what the database thinks its size is, whether the episode
 * is played or a favourite, and which feed it belongs to.
 */
public final class DownloadedMedia {
    private static final String CONTENT_URI_PREFIX = "content://";

    public final long mediaId;
    @Nullable
    public final String fileUrl;
    /** The size recorded in the database; may be 0 or stale, the overview measures the file. */
    public final long dbSize;
    public final boolean played;
    public final boolean favorite;
    public final long feedId;
    @Nullable
    public final String feedTitle;
    @Nullable
    public final String feedImageUrl;
    public final boolean feedSubscribed;
    public final boolean feedAudiobook;

    public DownloadedMedia(long mediaId, @Nullable String fileUrl, long dbSize, boolean played,
                           boolean favorite, long feedId, @Nullable String feedTitle,
                           @Nullable String feedImageUrl, boolean feedSubscribed,
                           boolean feedAudiobook) {
        this.mediaId = mediaId;
        this.fileUrl = fileUrl;
        this.dbSize = dbSize;
        this.played = played;
        this.favorite = favorite;
        this.feedId = feedId;
        this.feedTitle = feedTitle;
        this.feedImageUrl = feedImageUrl;
        this.feedSubscribed = feedSubscribed;
        this.feedAudiobook = feedAudiobook;
    }

    /** Files of local folder podcasts are content URIs: they are the user's, not the app's storage. */
    public boolean isContentUri() {
        return fileUrl != null && fileUrl.startsWith(CONTENT_URI_PREFIX);
    }

    @NonNull
    @Override
    public String toString() {
        return "DownloadedMedia{" + mediaId + ", feed " + feedId + ", " + fileUrl + "}";
    }
}
