package allen.town.podcast.core.service.download;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import allen.town.podcast.core.feed.LocalFeedUpdater;
import allen.town.podcast.core.storage.DBReader;
import allen.town.podcast.core.storage.DBTasks;
import allen.town.podcast.core.storage.DBWriter;
import allen.town.podcast.core.storage.EpisodeCleanupAlgorithmFactory;
import allen.town.podcast.event.FeedItemEvent;
import allen.town.podcast.model.feed.Feed;
import allen.town.podcast.model.feed.FeedItem;
import allen.town.podcast.model.feed.FeedMedia;

/**
 * Turns intents into running downloads and runs them to completion: it owns the single "EnqueueThread"
 * on which every change to the queue is serialized (enqueueing a batch, refreshing all feeds,
 * cancelling, and deciding whether the service may stop), the bodies that execute a download or a
 * local feed refresh on the download pool, and the automatic retry of a transient failure. It hands
 * a finished downloader to the {@link DownloadCompletionHandler}, keeps the {@link DownloadQueue}
 * in step, and asks the owning {@link DownloadService} to re-check whether it can stop.
 */
class DownloadPipeline {
    private static final String TAG = "DownloadService";
    /** Delay before an automatic retry is started. */
    private static final long RETRY_DELAY_MS = 3000;

    private final Context context;
    private final DownloadQueue queue;
    private final DownloadNotifier notifier;
    private final DownloadCompletionHandler completionHandler;
    private final Runnable stopServiceIfEverythingDone;
    private final ExecutorService downloadEnqueueExecutor;

    DownloadPipeline(@NonNull Context context, @NonNull DownloadQueue queue, @NonNull DownloadNotifier notifier,
                     @NonNull DownloadCompletionHandler completionHandler,
                     @NonNull Runnable stopServiceIfEverythingDone) {
        this.context = context;
        this.queue = queue;
        this.notifier = notifier;
        this.completionHandler = completionHandler;
        this.stopServiceIfEverythingDone = stopServiceIfEverythingDone;
        downloadEnqueueExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "EnqueueThread");
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
    }

    /** Runs a task on the enqueue thread before any request can be submitted there. */
    void submitBeforeAnyRequest(@NonNull Runnable task) {
        downloadEnqueueExecutor.execute(task);
    }

    /** Stops accepting work on the enqueue thread. */
    void shutdownNow() {
        downloadEnqueueExecutor.shutdownNow();
    }

    /** Enqueues the requests carried by a start intent. */
    void enqueue(@NonNull Intent intent) {
        downloadEnqueueExecutor.execute(() -> onDownloadQueued(intent));
    }

    /** Enqueues a refresh of every feed that is kept updated. */
    void enqueueAllFeeds(@NonNull Intent intent) {
        downloadEnqueueExecutor.execute(() -> enqueueAll(intent));
    }

    /** Cancels the download of one url, on the enqueue thread. */
    void cancel(String url) {
        downloadEnqueueExecutor.execute(() -> {
            queue.cancel(context.getApplicationContext(), url);
            notifier.postDownloaders();
            stopServiceIfEverythingDone.run();
        });
    }

    /** Cancels every download, on the enqueue thread. */
    void cancelAll() {
        downloadEnqueueExecutor.execute(() -> {
            queue.cancelAll();
            notifier.postDownloaders();
            stopServiceIfEverythingDone.run();
        });
    }

    /**
     * This method MUST NOT, in any case, throw an exception.
     * Otherwise, it hangs up the refresh thread pool.
     */
    private void performDownload(Downloader downloader) {
        DownloadRequest request = downloader.getDownloadRequest();
        boolean retry = false;
        try {
            if (request.getRetryCount() > 0 && !downloader.cancelled) {
                // short, interruptible back-off before an automatic retry
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    downloader.cancel();
                }
            }
            if (downloader.cancelled || queue.isShutdown()) {
                if (downloader.cancelled) {
                    // cancelled before it started: writeFileUrl() already created an empty file
                    completionHandler.discardCancelled(request);
                }
                return;
            }
            try {
                downloader.call();
            } catch (Exception e) {
                Log.e(TAG, "download threw", e);
            }
            try {
                if (downloader.getResult().isSuccessful()) {
                    retry = !completionHandler.handleSuccessfulDownload(downloader)
                            && completionHandler.handleFailedDownload(downloader);
                } else {
                    retry = completionHandler.handleFailedDownload(downloader);
                }
            } catch (Exception e) {
                Log.e(TAG, "download result handling threw", e);
            }
        } finally {
            // Always remove the downloader on this thread. Routing the removal through the
            // enqueue executor made it depend on that thread being alive and unblocked, which
            // is how entries ended up stuck in the "downloading" list forever.
            if (retry && !downloader.cancelled && !queue.isShutdown()) {
                request.setRetryCount(request.getRetryCount() + 1);
                request.setSoFar(0);
                request.setProgressPercent(0);
                Log.d(TAG, "retrying download (attempt " + request.getRetryCount() + "): " + request.getSource());
                // Add the replacement before removing this entry so that the list is never
                // momentarily empty (which would let a concurrent stop check end the service).
                // Flagging this finished downloader as cancelled hides it from isDownloadingFile()
                // so that addNewRequest() accepts the same source again.
                downloader.cancel();
                try {
                    addNewRequest(request);
                } catch (Exception e) {
                    Log.e(TAG, "unable to schedule retry", e);
                }
            }
            queue.remove(downloader);
            notifier.postDownloaders();
            notifyItemChanged(request);
            scheduleStopCheck();
        }
    }

    /**
     * This method MUST NOT, in any case, throw an exception.
     * Otherwise, it hangs up the refresh thread pool.
     */
    private void performLocalFeedRefresh(Downloader downloader, DownloadRequest request) {
        try {
            Feed feed = DBReader.getFeed(request.getFeedfileId());
            LocalFeedUpdater.updateFeed(feed, context, (scanned, totalFiles) -> {
                request.setSize(totalFiles);
                request.setSoFar(scanned);
                request.setProgressPercent((int) (100.0 * scanned / totalFiles));
            });
        } catch (Exception e) {
            // Must not propagate: this runs on the shared download executor and an escaping
            // exception would kill that worker (see the contract in the javadoc above). The feed's
            // own failure state is recorded by LocalFeedUpdater.
            Log.e(TAG, "Local feed refresh failed for " + request.getSource(), e);
        } finally {
            queue.remove(downloader);
            notifier.postDownloaders();
            scheduleStopCheck();
        }
    }

    /**
     * Posts a FeedItemEvent for the media of a request so that list rows stop showing a
     * download progress once the download has ended, whatever the outcome was.
     */
    private void notifyItemChanged(@NonNull DownloadRequest request) {
        if (request.getFeedfileType() != FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
            return;
        }
        try {
            FeedItem item = DownloadService.getFeedItemFromId(request.getFeedfileId());
            if (item != null) {
                EventBus.getDefault().post(FeedItemEvent.updated(item));
            }
        } catch (Exception e) {
            Log.e(TAG, "unable to notify item change", e);
        }
    }

    /**
     * Decides whether the service can stop. The decision is serialized on the enqueue executor
     * so that it cannot race with a request that is currently being added; if that executor is
     * already gone, the check runs inline.
     */
    private void scheduleStopCheck() {
        if (downloadEnqueueExecutor.isShutdown()) {
            stopServiceIfEverythingDone.run();
            return;
        }
        try {
            downloadEnqueueExecutor.execute(stopServiceIfEverythingDone);
        } catch (RejectedExecutionException e) {
            stopServiceIfEverythingDone.run();
        }
    }

    private void onDownloadQueued(Intent intent) {
        List<DownloadRequest> requests = intent.getParcelableArrayListExtra(DownloadService.EXTRA_REQUESTS);
        if (requests == null) {
            throw new IllegalArgumentException("ACTION_ENQUEUE_DOWNLOAD intent needs request extra");
        }
        Log.d(TAG, "receive download enqueue request " + requests.size());

        if (intent.getBooleanExtra(DownloadService.EXTRA_CLEANUP_MEDIA, false)) {
            EpisodeCleanupAlgorithmFactory.build()
                    .makeRoomForEpisodes(context.getApplicationContext(), requests.size());
        }

        for (DownloadRequest request : requests) {
            addNewRequest(request);
        }
        notifier.postDownloaders();
        stopServiceIfEverythingDone.run();

        // Add to-download items to the queue before actual download completed
        // so that the resulting queue order is the same as when download is clicked
        enqueueFeedItems(requests);
    }

    private void enqueueFeedItems(@NonNull List<DownloadRequest> requests) {
        List<FeedItem> feedItems = new ArrayList<>();
        for (DownloadRequest request : requests) {
            if (request.getFeedfileType() == FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
                long mediaId = request.getFeedfileId();
                FeedMedia media = DBReader.getFeedMedia(mediaId);
                if (media == null) {
                    Log.w(TAG, "enqueueFeedItems() : FeedFile Id " + mediaId + " is not found. ignore it.");
                    continue;
                }
                feedItems.add(media.getItem());
            }
        }
        List<FeedItem> actuallyEnqueued = Collections.emptyList();
        try {
            actuallyEnqueued = DBTasks.enqueueFeedItemsToDownload(context.getApplicationContext(), feedItems);
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // Safe to continue: the downloads themselves still run. Only the "was enqueued by this
            // download" bookkeeping is missing, so cancelling one of them will not undo the enqueue.
            Log.e(TAG, "Failed to enqueue items for download", e);
        }

        for (DownloadRequest request : requests) {
            if (request.getFeedfileType() != FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
                continue;
            }
            final long mediaId = request.getFeedfileId();
            for (FeedItem item : actuallyEnqueued) {
                if (item.getMedia() != null && item.getMedia().getId() == mediaId) {
                    request.setMediaEnqueued(true);
                }
            }
        }
    }

    private void enqueueAll(Intent intent) {
        boolean initiatedByUser = intent.getBooleanExtra(DownloadService.EXTRA_INITIATED_BY_USER, false);
        List<Feed> feeds = DBReader.getFeedList();
        for (Feed feed : feeds) {
            if (feed.getPreferences().getKeepUpdated()) {
                DownloadRequest.Builder builder = DownloadRequestCreator.create(feed);
                builder.withInitiatedByUser(initiatedByUser);
                if (feed.hasLastUpdateFailed()) {
                    builder.setForce(true);
                }
                addNewRequest(builder.build());
            }
        }
        notifier.postDownloaders();
        stopServiceIfEverythingDone.run();
    }

    private void addNewRequest(@NonNull DownloadRequest request) {
        // The queue is the monitor that also guards cancellation, so a retry submitted from a
        // download thread cannot interleave with a cancel running on the enqueue thread.
        synchronized (queue) {
            addNewRequestLocked(request);
        }
    }

    private void addNewRequestLocked(@NonNull DownloadRequest request) {
        if (DownloadService.isDownloadingFile(request.getSource())) {
            Log.d(TAG, "already in queue");
            return;
        } else if (queue.isShutdown()) {
            Log.d(TAG, "service is already shutting down.");
            return;
        }
        Log.d(TAG, "add new request -> " + request.getSource());
        if (request.getSource().startsWith(Feed.PREFIX_LOCAL_FOLDER)) {
            Downloader downloader = new LocalFeedStubDownloader(context.getApplicationContext(), request);
            queue.submit(downloader, () -> performLocalFeedRefresh(downloader, request));
        } else {
            writeFileUrl(request);
            Downloader downloader = DownloadService.downloaderFactory.create(context.getApplicationContext(), request);
            if (downloader != null) {
                queue.submit(downloader, () -> performDownload(downloader));
            }
        }
    }

    /**
     * Creates the destination file and writes FeedMedia File_url directly after starting download
     * to make it possible to resume download after the service was killed by the system.
     */
    private void writeFileUrl(DownloadRequest request) {
        if (request.getFeedfileType() != FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
            return;
        }

        File dest = new File(request.getDestination());
        if (!dest.exists()) {
            try {
                dest.createNewFile();
            } catch (IOException e) {
                Log.e(TAG, "unable to create file");
            }
        }

        if (dest.exists()) {
            Log.d(TAG, "save to file");
            FeedMedia media = DBReader.getFeedMedia(request.getFeedfileId());
            if (media == null) {
                Log.d(TAG, "no media");
                return;
            }
            media.setFile_url(request.getDestination());
            try {
                DBWriter.setFeedMedia(media).get();
            } catch (InterruptedException e) {
                Log.e(TAG, "save file failed");
            } catch (ExecutionException e) {
                Log.e(TAG, "save file failed " + e.getMessage());
            }
        }
    }
}
