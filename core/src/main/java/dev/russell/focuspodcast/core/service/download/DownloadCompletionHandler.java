package dev.russell.focuspodcast.core.service.download;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import dev.russell.focuspodcast.core.service.download.handler.FailedDownloadHandler;
import dev.russell.focuspodcast.core.service.download.handler.FeedSyncTask;
import dev.russell.focuspodcast.core.service.download.handler.MediaDownloadedHandler;
import dev.russell.focuspodcast.core.storage.DBReader;
import dev.russell.focuspodcast.core.storage.DBWriter;
import dev.russell.focuspodcast.core.util.NetworkUtils;
import dev.russell.focuspodcast.event.AudioOnlyDownloadUnavailableEvent;
import dev.russell.focuspodcast.model.download.DownloadError;
import dev.russell.focuspodcast.model.download.DownloadStatus;
import dev.russell.focuspodcast.model.feed.Feed;
import dev.russell.focuspodcast.model.feed.FeedItem;
import dev.russell.focuspodcast.model.feed.FeedMedia;

/**
 * Decides what happens once a {@link Downloader} has stopped: it runs the feed sync or the media
 * handler for a successful download, classifies a failure as final or worth one automatic retry,
 * writes the {@link DownloadStatus} rows to the database and collects them for the end-of-run
 * report that {@link DownloadService} shows when it stops. It owns nothing about scheduling —
 * the service still decides when a retry is actually re-submitted — and nothing about
 * notifications beyond asking the {@link DownloadNotifier} for the authentication prompt.
 */
class DownloadCompletionHandler {
    private static final String TAG = "DownloadService";
    /** Maximum number of automatic re-submissions of a media download after a transient error. */
    private static final int MAX_RETRIES = 1;
    /**
     * A video whose audio cannot be downloaded on its own is downloaded whole without asking up
     * to this size; above it (or when the size is unknown) the listener is asked first.
     */
    static final long FULL_DOWNLOAD_WITHOUT_ASKING_LIMIT = 2L * 1024 * 1024 * 1024;

    private final Context context;
    private final NewEpisodesNotification newEpisodesNotification;
    private final DownloadNotifier notifier;
    private final List<DownloadStatus> reportQueue = new ArrayList<>();

    DownloadCompletionHandler(@NonNull Context context,
                              @NonNull NewEpisodesNotification newEpisodesNotification,
                              @NonNull DownloadNotifier notifier) {
        this.context = context;
        this.newEpisodesNotification = newEpisodesNotification;
        this.notifier = notifier;
    }

    /** The statuses accumulated since the service started, for the end-of-run report. */
    List<DownloadStatus> getReportQueue() {
        return reportQueue;
    }

    /**
     * Returns true for errors that are typically transient (connection dropped, timeout, DNS
     * hiccup) and therefore worth one automatic retry.
     */
    static boolean isTransientError(@Nullable DownloadError reason) {
        return reason == DownloadError.ERROR_CONNECTION_ERROR
                || reason == DownloadError.ERROR_IO_ERROR
                || reason == DownloadError.ERROR_UNKNOWN_HOST;
    }

    /**
     * @return false if the media handler rejected the downloaded file, in which case the
     *         downloader's result has been switched to the failure and the caller must run
     *         the failure path.
     */
    boolean handleSuccessfulDownload(Downloader downloader) {
        DownloadRequest request = downloader.getDownloadRequest();
        DownloadStatus status = downloader.getResult();
        final int type = status.getFeedfileType();

        if (type == Feed.FEEDFILETYPE_FEED) {
            Log.d(TAG, "feed completed download");
            FeedSyncTask feedSyncTask = new FeedSyncTask(context, request);
            boolean success = feedSyncTask.run();

            if (success) {
                if (request.getFeedfileId() == 0) {
                    return true; // No download logs for new subscriptions
                }
                // we create a 'successful' download log if the feed's last refresh failed
                List<DownloadStatus> log = DBReader.getFeedDownloadLog(request.getFeedfileId());
                if (log.size() > 0 && !log.get(0).isSuccessful()) {
                    saveDownloadStatus(feedSyncTask.getDownloadStatus());
                }
                if (!request.isInitiatedByUser()) {
                    // Was stored in the database before and not initiated manually
                    newEpisodesNotification.showIfNeeded(context, feedSyncTask.getSavedFeed());
                }
                if (downloader.permanentRedirectUrl != null) {
                    DBWriter.updateFeedDownloadURL(request.getSource(), downloader.permanentRedirectUrl);
                } else if (feedSyncTask.getRedirectUrl() != null
                        && !feedSyncTask.getRedirectUrl().equals(request.getSource())) {
                    DBWriter.updateFeedDownloadURL(request.getSource(), feedSyncTask.getRedirectUrl());
                }
            } else {
                int pageNr = request.getArguments() == null ? 0
                        : request.getArguments().getInt(DownloadRequest.REQUEST_ARG_PAGE_NR, 0);
                if (pageNr == 0) {
                    // a failed extra page must not flag the feed itself as failed
                    DBWriter.setFeedLastUpdateFailed(request.getFeedfileId(), true);
                }
                saveDownloadStatus(feedSyncTask.getDownloadStatus());
            }
        } else if (type == FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
            Log.d(TAG, "FeedMedia completed download");
            MediaDownloadedHandler handler = new MediaDownloadedHandler(context, status, request);
            handler.run();
            DownloadStatus updated = handler.getUpdatedStatus();
            if (!updated.isSuccessful()) {
                // e.g. file missing/incomplete on disk: treat like a failed download so that
                // the log, the failed-attempt counter and the retry policy all apply.
                downloader.getResult().setFailed(updated.getReason(), updated.getReasonDetailed());
                return false;
            }
            saveDownloadStatus(updated);
        }
        return true;
    }

    /**
     * Drops what a cancelled media download left behind. Skipped when the same episode is
     * already being downloaded again, since that request owns the file and file_url now.
     */
    void discardCancelled(DownloadRequest request) {
        if (!DownloadService.isDownloadingFile(request.getSource())) {
            FailedDownloadHandler.discardPartialMedia(request);
        }
    }

    /**
     * For an audio-only download that found the audio cannot be cut out of the file: a request
     * for the whole file if it may be downloaded without asking (small enough, and not on a
     * metered network the listener does not download over), else null. The caller submits the
     * returned request in place of a retry and drops the empty audio-only file.
     */
    @Nullable
    DownloadRequest fullDownloadInstead(Downloader downloader) {
        DownloadRequest request = downloader.getDownloadRequest();
        if (downloader.cancelled
                || downloader.getResult().getReason() != DownloadError.ERROR_AUDIO_ONLY_UNSUPPORTED
                || request.getSize() <= 0 || request.getSize() > FULL_DOWNLOAD_WITHOUT_ASKING_LIMIT
                || !NetworkUtils.isEpisodeDownloadAllowed()) {
            return null;
        }
        FeedMedia media = DBReader.getFeedMedia(request.getFeedfileId());
        if (media == null || media.getItem() == null) {
            return null;
        }
        Log.d(TAG, "audio only unavailable, downloading the whole file: " + request.getSource());
        DownloadRequest full = DownloadRequestCreator.createFullDownload(media)
                .withInitiatedByUser(request.isInitiatedByUser())
                .build();
        full.setMediaEnqueued(request.isMediaEnqueued());
        return full;
    }

    /**
     * Handles a download that did not succeed.
     *
     * @return true if the request should be re-submitted automatically (the caller takes care
     *         of that once the current downloader has been removed from the list), false if
     *         the failure is final. A FeedItemEvent for the media is posted by the caller in
     *         every case, so this method does not need to.
     */
    boolean handleFailedDownload(Downloader downloader) {
        DownloadStatus status = downloader.getResult();
        DownloadRequest request = downloader.getDownloadRequest();
        final int type = status.getFeedfileType();

        if (status.isCancelled()) {
            discardCancelled(request);
            return false;
        }

        if (status.getReason() == DownloadError.ERROR_UNAUTHORIZED) {
            notifier.postAuthenticationNotification(request);
            FailedDownloadHandler.discardPartialMedia(request);
            return false;
        }

        boolean retryBudgetLeft = request.getRetryCount() < MAX_RETRIES;
        if (status.getReason() == DownloadError.ERROR_HTTP_DATA_ERROR && isHttpCode(status, 416)) {
            // The server rejected our Range request for the partial file: discard it and
            // start over from scratch.
            Log.d(TAG, "invalid range restarting download");
            FileUtils.deleteQuietly(new File(request.getDestination()));
            if (retryBudgetLeft) {
                return true;
            }
        } else if (type == FeedMedia.FEEDFILETYPE_FEEDMEDIA
                && (isTransientError(status.getReason()) || status.getReason() == DownloadError.ERROR_IO_WRONG_SIZE)
                && retryBudgetLeft
                && NetworkUtils.networkAvailable()) {
            Log.w(TAG, "transient error " + status.getReason() + ", will retry: " + request.getSource());
            return true;
        }

        // Final failure: log it, run the handler and count the attempt (this also drives the
        // auto-download back-off). Every branch above that does not retry ends up here.
        Log.e(TAG, "download failed: " + status.getReason());
        saveDownloadStatus(status);
        new FailedDownloadHandler(request).run();

        if (status.getReason() == DownloadError.ERROR_AUDIO_ONLY_UNSUPPORTED && request.isInitiatedByUser()) {
            // Too large to download whole without asking: the app offers the full video.
            EventBus.getDefault().post(new AudioOnlyDownloadUnavailableEvent(
                    request.getFeedfileId(), request.getSize()));
        }

        if (type == FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
            FeedItem item = DownloadService.getFeedItemFromId(status.getFeedfileId());
            if (item != null) {
                item.increaseFailedAutoDownloadAttempts(System.currentTimeMillis());
                DBWriter.setFeedItem(item);
            }
        }
        return false;
    }

    private static boolean isHttpCode(@NonNull DownloadStatus status, int code) {
        try {
            return Integer.parseInt(status.getReasonDetailed()) == code;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Adds a new DownloadStatus object to the list of completed downloads and
     * saves it in the database
     *
     * @param status the download that is going to be saved
     */
    private void saveDownloadStatus(@NonNull DownloadStatus status) {
        reportQueue.add(status);
        DBWriter.addDownloadStatus(status);
    }
}
