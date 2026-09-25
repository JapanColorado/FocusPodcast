package allen.town.podcast.core.service.download.handler;

import android.util.Log;

import java.io.File;

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
 * For media, the failure is final (automatic retries resume the partial file before this runs),
 * so the partial file is deleted and file_url cleared. Leaving them behind made the episode look
 * half downloaded: a file on disk and a file_url, but no downloaded flag.
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
            discardPartialMedia(request);
        } else if (request.isDeleteOnFailure()) {
            Log.d(TAG, "delete on failure");
        }
    }

    /**
     * Deletes the partial file of a media download that failed for good or was cancelled, and
     * clears the media's file_url (which also clears its downloaded flag) so that the database
     * matches the disk again.
     */
    public static void discardPartialMedia(DownloadRequest request) {
        if (request.getFeedfileType() != FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
            return;
        }
        String destination = request.getDestination();
        if (destination != null) {
            File partial = new File(destination);
            if (partial.exists() && !partial.delete()) {
                Log.w(TAG, "unable to delete partial download " + destination);
            }
        }
        FeedMedia media = DBReader.getFeedMedia(request.getFeedfileId());
        if (media == null || media.isContentUri()) {
            return;
        }
        if (destination != null && destination.equals(media.getFile_url())) {
            Log.d(TAG, "clearing file_url after failed download of " + request.getSource());
            media.setFile_url(null);
            DBWriter.setFeedMedia(media);
        } else if (media.isDownloaded()) {
            // The row points at some other file, which is not ours to delete, but a failed
            // download must still never leave the media flagged as downloaded.
            Log.w(TAG, "clearing downloaded flag after failed download of " + request.getSource());
            media.setDownloaded(false);
            DBWriter.setFeedMedia(media);
        }
    }
}
