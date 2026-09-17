package allen.town.podcast.core.service.download;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import allen.town.podcast.core.pref.Prefs;
import allen.town.podcast.core.storage.DBWriter;
import allen.town.podcast.event.FeedItemEvent;
import allen.town.podcast.model.feed.Feed;
import allen.town.podcast.model.feed.FeedItem;

/**
 * Owns the downloads that are enqueued or running: the list itself, the thread pool that executes
 * them, the queries the rest of the app uses to ask whether something is in flight
 * ({@link #isDownloadingFile}, {@link #isDownloadingFeeds}, {@link #findRequest}), the batch
 * hygiene applied before a request ever reaches an Intent ({@link #filterInFlight},
 * {@link #capToIntentLimit}) and cancellation. {@link DownloadService} keeps the public static
 * entry points and delegates them here; it also decides what "running" means, so the guards that
 * suppress a stale answer while no service is alive stay on the service and the queries below
 * answer purely from the list.
 */
class DownloadQueue {
    private static final String TAG = "DownloadQueue";

    /**
     * Android silently drops an intent whose payload exceeds the binder transaction limit, so a
     * batch bigger than this is trimmed rather than risking losing every request in it.
     */
    static final int MAX_REQUESTS_PER_INTENT = 100;

    /** Tells whether a download for the given URL is already in flight. */
    interface InFlightCheck {
        boolean isDownloading(String downloadUrl);
    }

    /**
     * The downloaders currently enqueued or running.
     *
     * <p>This list is static because the static query methods below are called from all over the
     * app without a service binding. It is owned by the running service instance: entries are
     * added in {@link #submit} and removed when a downloader finishes, is cancelled, or is
     * rejected by the executor, and the service clears whatever is left after shutting the
     * executors down. That clear is what keeps the list from carrying stale downloaders into the
     * next service instance (which would make the new instance believe work is still pending and
     * never stop). The entries are {@link Downloader} objects, not Contexts, so the static
     * reference does not leak the Service.</p>
     *
     * <p>It can be modified from another thread while iterating. Both possible race conditions are
     * not critical: remove while iterating means we think it is still downloading and don't start a
     * new download with the same file; add while iterating means we think it is not downloading and
     * might start a second download with the same file.</p>
     */
    static final List<Downloader> ENTRIES = new CopyOnWriteArrayList<>();

    private final ExecutorService downloadHandleExecutor;

    DownloadQueue() {
        downloadHandleExecutor = Executors.newFixedThreadPool(Prefs.getParallelDownloads(),
                r -> {
                    Thread t = new Thread(r, "DownloadThread");
                    t.setPriority(Thread.MIN_PRIORITY);
                    return t;
                });
    }

    /**
     * Drops the requests whose URL is already being downloaded, keeping the caller's order and
     * the first of any duplicates within the batch itself.
     */
    static ArrayList<DownloadRequest> filterInFlight(DownloadRequest[] requests, InFlightCheck inFlight) {
        ArrayList<DownloadRequest> accepted = new ArrayList<>();
        for (DownloadRequest request : requests) {
            if (inFlight.isDownloading(request.getSource())) {
                continue;
            }
            boolean duplicateInBatch = false;
            for (DownloadRequest alreadyAccepted : accepted) {
                if (alreadyAccepted.getSource().equals(request.getSource())) {
                    duplicateInBatch = true;
                    break;
                }
            }
            if (!duplicateInBatch) {
                accepted.add(request);
            }
        }
        return accepted;
    }

    /** Trims a batch to {@link #MAX_REQUESTS_PER_INTENT} entries, keeping the first ones. */
    static ArrayList<DownloadRequest> capToIntentLimit(List<DownloadRequest> requests) {
        if (requests.size() <= MAX_REQUESTS_PER_INTENT) {
            return new ArrayList<>(requests);
        }
        return new ArrayList<>(requests.subList(0, MAX_REQUESTS_PER_INTENT));
    }

    /** Whether a feed refresh is in flight. The caller checks that a service is alive. */
    static boolean isDownloadingFeeds() {
        for (Downloader downloader : ENTRIES) {
            if (downloader.request.getFeedfileType() == Feed.FEEDFILETYPE_FEED && !downloader.cancelled) {
                return true;
            }
        }
        return false;
    }

    /** Whether the given url is in flight. The caller checks that a service is alive. */
    static boolean isDownloadingFile(String downloadUrl) {
        for (Downloader downloader : ENTRIES) {
            if (downloader.request.getSource().equals(downloadUrl) && !downloader.cancelled) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the request currently downloading the given url, or null. Uses the same
     * definition of "downloading" as {@link #isDownloadingFile(String)} so callers that
     * check one and then dereference the other cannot observe a mismatch.
     */
    @Nullable
    static DownloadRequest findRequest(String downloadUrl) {
        for (Downloader downloader : ENTRIES) {
            if (downloader.request.getSource().equals(downloadUrl) && !downloader.cancelled) {
                return downloader.request;
            }
        }
        return null;
    }

    int size() {
        return ENTRIES.size();
    }

    boolean isShutdown() {
        return downloadHandleExecutor.isShutdown();
    }

    void remove(Downloader downloader) {
        ENTRIES.remove(downloader);
    }

    void shutdownNow() {
        downloadHandleExecutor.shutdownNow();
    }

    void clear() {
        ENTRIES.clear();
    }

    void submit(@NonNull Downloader downloader, @NonNull Runnable task) {
        ENTRIES.add(downloader);
        try {
            downloadHandleExecutor.submit(task);
        } catch (RejectedExecutionException e) {
            // executor shut down between the isShutdown() check and here; an entry left in the
            // static list would keep the next service instance from ever stopping
            ENTRIES.remove(downloader);
            Log.w(TAG, "download executor rejected " + downloader.getDownloadRequest().getSource());
        }
    }

    void cancelAll() {
        Log.d(TAG, "cancel all downloads");
        for (Downloader d : ENTRIES) {
            d.cancel();
        }
    }

    synchronized void cancel(Context applicationContext, String url) {
        Log.d(TAG, "cancel download url " + url);
        for (Downloader downloader : ENTRIES) {
            if (downloader.cancelled || !downloader.getDownloadRequest().getSource().equals(url)) {
                continue;
            }
            downloader.cancel();
            DownloadRequest request = downloader.getDownloadRequest();
            FeedItem item = DownloadService.getFeedItemFromId(request.getFeedfileId());
            if (item != null) {
                EventBus.getDefault().post(FeedItemEvent.updated(item));
                // undo enqueue upon cancel
                if (request.isMediaEnqueued()) {
                    Log.v(TAG, "Undoing enqueue upon cancelling download");
                    DBWriter.removeQueueItem(applicationContext, false, item);
                }
            }
        }
    }
}
