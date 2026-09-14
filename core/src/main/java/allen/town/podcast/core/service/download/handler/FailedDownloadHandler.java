package allen.town.podcast.core.service.download.handler;

import android.util.Log;
import allen.town.podcast.model.feed.Feed;
import allen.town.podcast.model.feed.FeedMedia;
import allen.town.podcast.core.service.download.DownloadRequest;
import allen.town.podcast.core.storage.DBReader;
import allen.town.podcast.core.storage.DBWriter;

/**
 * Handles failed downloads.
 * <p/>
 * For feeds, the feed is flagged so that the next refresh is forced.
 * <p/>
 * For media, the partially downloaded file and its file_url are kept so that the next attempt
 * can resume with a Range request, but the media must never be flagged as downloaded.
 */
public class FailedDownloadHandler implements Runnable {
    private static final String TAG = "FailedDownloadHandler";
    private final DownloadRequest request;

    public FailedDownloadHandler(DownloadRequest request) {
        this.request = request;
    }

    @Override
    public void run() {
        if (request.getFeedfileType() == Feed.FEEDFILETYPE_FEED) {
            DBWriter.setFeedLastUpdateFailed(request.getFeedfileId(), true);
        } else if (request.getFeedfileType() == FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
            FeedMedia media = DBReader.getFeedMedia(request.getFeedfileId());
            if (media != null && media.isDownloaded()) {
                // Defensive: a failed download must not leave the media flagged as downloaded.
                Log.w(TAG, "clearing downloaded flag after failed download of " + request.getSource());
                media.setDownloaded(false);
                DBWriter.setFeedMedia(media);
            }
        } else if (request.isDeleteOnFailure()) {
            Log.d(TAG, "delete on failure");
        }
    }
}
