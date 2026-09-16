package allen.town.podcast.core.service.download;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.ServiceCompat;

import androidx.core.content.ContextCompat;


import allen.town.focus_common.util.Timber;
import allen.town.podcast.core.BuildConfig;
import allen.town.podcast.core.R;
import allen.town.podcast.core.feed.LocalFeedUpdater;
import allen.town.podcast.core.storage.EpisodeCleanupAlgorithmFactory;
import allen.town.podcast.model.download.DownloadStatus;
import org.apache.commons.io.FileUtils;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import allen.town.podcast.core.event.DownloadEvent;
import allen.town.podcast.core.util.NetworkUtils;
import allen.town.podcast.core.util.download.ConnectionStateMonitor;
import allen.town.podcast.event.FeedItemEvent;
import allen.town.podcast.model.feed.Feed;
import allen.town.podcast.model.feed.FeedItem;
import allen.town.podcast.model.feed.FeedMedia;
import allen.town.podcast.core.pref.Prefs;
import allen.town.podcast.core.service.download.handler.FailedDownloadHandler;
import allen.town.podcast.core.service.download.handler.FeedSyncTask;
import allen.town.podcast.core.service.download.handler.MediaDownloadedHandler;
import allen.town.podcast.core.service.download.handler.PostDownloaderTask;
import allen.town.podcast.core.storage.DBReader;
import allen.town.podcast.core.storage.DBTasks;
import allen.town.podcast.core.storage.DBWriter;
import allen.town.podcast.model.download.DownloadError;

/**
 * Manages the download of feedfiles in the app. Downloads can be enqueued via the startService intent.
 * The argument of the intent is an instance of DownloadRequest in the EXTRA_REQUESTS field of
 * the intent.
 * After the downloads have finished, the downloaded object will be passed on to a specific handler, depending on the
 * type of the feedfile.
 */
public class DownloadService extends Service {
    private static final String TAG = "DownloadService";
    private static final int SCHED_EX_POOL_SIZE = 1;
    /** Maximum number of automatic re-submissions of a media download after a transient error. */
    private static final int MAX_RETRIES = 1;
    /** Delay before an automatic retry is started. */
    private static final long RETRY_DELAY_MS = 3000;
    public static final String ACTION_CANCEL_DOWNLOAD = "action.allen.town.podcast.core.service.cancelDownload";
    public static final String ACTION_CANCEL_ALL_DOWNLOADS = "action.allen.town.podcast.core.service.cancelAll";
    public static final String EXTRA_DOWNLOAD_URL = "downloadUrl";
    public static final String EXTRA_REQUESTS = "downloadRequests";
    public static final String EXTRA_REFRESH_ALL = "refreshAll";
    public static final String EXTRA_INITIATED_BY_USER = "initiatedByUser";
    public static final String EXTRA_CLEANUP_MEDIA = "cleanupMedia";

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    /**
     * The downloaders currently enqueued or running.
     *
     * <p>This list is static because the static query methods below ({@link #isDownloadingFile},
     * {@link #isDownloadingFeeds}, {@link #findRequest}) are called from all over the app without a
     * service binding. It is owned by the running service instance: entries are added in
     * {@link #submitDownloader} and removed when a downloader finishes, is cancelled, or is rejected
     * by the executor, and {@link #onDestroy} clears whatever is left after shutting the executors
     * down. That clear is what keeps the list from carrying stale downloaders into the next service
     * instance (which would make the new instance believe work is still pending and never stop).
     * The entries are {@link Downloader} objects, not Contexts, so the static reference does not
     * leak the Service. All reads are guarded by {@link #isRunning()} so that an empty list observed
     * while no service is alive is reported as "not downloading" rather than as a stale answer.</p>
     *
     * <p>It can be modified from another thread while iterating. Both possible race conditions are
     * not critical: remove while iterating means we think it is still downloading and don't start a
     * new download with the same file; add while iterating means we think it is not downloading and
     * might start a second download with the same file.</p>
     */
    static final List<Downloader> downloads = new CopyOnWriteArrayList<>();
    private final ExecutorService downloadHandleExecutor;
    private final ExecutorService downloadEnqueueExecutor;

    private final List<DownloadStatus> reportQueue = new ArrayList<>();
    private DownloadServiceNotification notificationManager;
    private final NewEpisodesNotification newEpisodesNotification;
    private NotificationUpdater notificationUpdater;
    private ScheduledFuture<?> notificationUpdaterFuture;
    private volatile ScheduledFuture<?> downloadPostFuture;
    private final ScheduledThreadPoolExecutor notificationUpdateExecutor;
    private static DownloaderFactory downloaderFactory = new DefaultDownloaderFactory();
    private ConnectionStateMonitor connectionMonitor;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public DownloadService() {
        newEpisodesNotification = new NewEpisodesNotification();

        downloadEnqueueExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "EnqueueThread");
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        // Must be the first runnable in syncExecutor
        downloadEnqueueExecutor.execute(newEpisodesNotification::loadCountersBeforeRefresh);

        Log.d(TAG, "init download service " + Prefs.getParallelDownloads());
        downloadHandleExecutor = Executors.newFixedThreadPool(Prefs.getParallelDownloads(),
                r -> {
                    Thread t = new Thread(r, "DownloadThread");
                    t.setPriority(Thread.MIN_PRIORITY);
                    return t;
                });
        notificationUpdateExecutor = new ScheduledThreadPoolExecutor(SCHED_EX_POOL_SIZE,
                r -> {
                    Thread t = new Thread(r, "NotificationUpdateExecutor");
                    t.setPriority(Thread.MIN_PRIORITY);
                    return t;
                }, (r, executor) -> Log.w(TAG, "SchedEx rejected submission of new task")
        );
    }

    @Override
    // Lint's UnspecifiedRegisterReceiverFlag fires on the pre-Android-13 branch below,
    // where the two-argument registerReceiver is the only overload that exists. The
    // exported flags are passed on Android 13+, which is where they are enforced.
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    public void onCreate() {
        Log.d(TAG, "onCreate");
        RUNNING.set(true);
        notificationManager = new DownloadServiceNotification(this);

        IntentFilter cancelDownloadReceiverFilter = new IntentFilter();
        cancelDownloadReceiverFilter.addAction(ACTION_CANCEL_ALL_DOWNLOADS);
        cancelDownloadReceiverFilter.addAction(ACTION_CANCEL_DOWNLOAD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // senders always setPackage(), so no other app needs to reach this receiver
            registerReceiver(cancelDownloadReceiver, cancelDownloadReceiverFilter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(cancelDownloadReceiver, cancelDownloadReceiverFilter);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            connectionMonitor = new ConnectionStateMonitor();
            connectionMonitor.enable(getApplicationContext());
        }
    }

    /**
     * Starts the service in the foreground. On Android 12+ the system may refuse foreground
     * service starts from the background; in that case the request is logged and dropped
     * instead of crashing the caller (e.g. a WorkManager worker or a network callback).
     */
    private static boolean startServiceSafely(Context context, Intent intent) {
        try {
            ContextCompat.startForegroundService(context, intent);
            return true;
        } catch (IllegalStateException e) {
            // ForegroundServiceStartNotAllowedException extends IllegalStateException
            Timber.e(e, "Unable to start DownloadService");
            return false;
        }
    }

    /**
     * Android silently drops an intent whose payload exceeds the binder transaction limit, so a
     * batch bigger than this is trimmed rather than risking losing every request in it.
     */
    @VisibleForTesting
    static final int MAX_REQUESTS_PER_INTENT = 100;

    /** Tells whether a download for the given URL is already in flight. */
    @VisibleForTesting
    interface InFlightCheck {
        boolean isDownloading(String downloadUrl);
    }

    /**
     * Drops the requests whose URL is already being downloaded, keeping the caller's order and
     * the first of any duplicates within the batch itself.
     */
    @VisibleForTesting
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
    @VisibleForTesting
    static ArrayList<DownloadRequest> capToIntentLimit(List<DownloadRequest> requests) {
        if (requests.size() <= MAX_REQUESTS_PER_INTENT) {
            return new ArrayList<>(requests);
        }
        return new ArrayList<>(requests.subList(0, MAX_REQUESTS_PER_INTENT));
    }

    public static void download(Context context, boolean cleanupMedia, DownloadRequest... requests) {
        ArrayList<DownloadRequest> requestsToSend =
                filterInFlight(requests, DownloadService::isDownloadingFile);
        for (DownloadRequest request : requestsToSend) {
            Timber.i("got request " + request.getTitle());
        }
        if (requestsToSend.isEmpty()) {
            return;
        } else if (requestsToSend.size() > MAX_REQUESTS_PER_INTENT) {
            if (BuildConfig.DEBUG) {
                throw new IllegalArgumentException("Android silently drops intent payloads that are too large");
            } else {
                Log.i(TAG, "Too many download requests. Dropping some to avoid Android dropping all.");
                requestsToSend = capToIntentLimit(requestsToSend);
            }
        }

        Intent launchIntent = new Intent(context, DownloadService.class);
        launchIntent.putParcelableArrayListExtra(DownloadService.EXTRA_REQUESTS, requestsToSend);
        if (cleanupMedia) {
            launchIntent.putExtra(DownloadService.EXTRA_CLEANUP_MEDIA, true);
        }
        startServiceSafely(context, launchIntent);
    }

    /** @return false if the system refused to start the service (background FGS restriction). */
    public static boolean refreshAllFeeds(Context context, boolean initiatedByUser) {
        Intent launchIntent = new Intent(context, DownloadService.class);
        launchIntent.putExtra(DownloadService.EXTRA_REFRESH_ALL, true);
        launchIntent.putExtra(DownloadService.EXTRA_INITIATED_BY_USER, initiatedByUser);
        return startServiceSafely(context, launchIntent);
    }

    public static void cancel(Context context, String url) {
        if (!isRunning()) {
            return;
        }
        Intent cancelIntent = new Intent(DownloadService.ACTION_CANCEL_DOWNLOAD);
        cancelIntent.putExtra(DownloadService.EXTRA_DOWNLOAD_URL, url);
        cancelIntent.setPackage(context.getPackageName());
        context.sendBroadcast(cancelIntent);
    }

    public static void cancelAll(Context context) {
        if (!isRunning()) {
            return;
        }
        Intent cancelIntent = new Intent(DownloadService.ACTION_CANCEL_ALL_DOWNLOADS);
        cancelIntent.setPackage(context.getPackageName());
        context.sendBroadcast(cancelIntent);
    }

    /** @return true while a DownloadService instance is alive (between onCreate and onDestroy). */
    public static boolean isRunning() {
        return RUNNING.get();
    }

    public static boolean isDownloadingFeeds() {
        if (!isRunning()) {
            return false;
        }
        for (Downloader downloader : downloads) {
            if (downloader.request.getFeedfileType() == Feed.FEEDFILETYPE_FEED && !downloader.cancelled) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDownloadingFile(String downloadUrl) {
        if (!isRunning()) {
            return false;
        }
        for (Downloader downloader : downloads) {
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
    public static DownloadRequest findRequest(String downloadUrl) {
        if (!isRunning()) {
            return null;
        }
        for (Downloader downloader : downloads) {
            if (downloader.request.getSource().equals(downloadUrl) && !downloader.cancelled) {
                return downloader.request;
            }
        }
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // The service was started with startForegroundService(), so startForeground() must be
        // called on every path (including the immediate shutdown below) to honour the 5s contract.
        Notification notification = notificationManager.updateNotifications(downloads);
        try {
            startForeground(R.id.notification_downloading, notification);
        } catch (Exception e) {
            // ForegroundServiceStartNotAllowedException (12+) / SecurityException (14+) when the
            // background-start exemption expired between startForegroundService() and here.
            Timber.e(e, "startForeground failed, giving up on this start");
            stopSelf();
            return Service.START_NOT_STICKY;
        }

        if (intent != null && intent.hasExtra(EXTRA_REQUESTS)) {
            setupNotificationUpdaterIfNecessary();
            downloadEnqueueExecutor.execute(() -> onDownloadQueued(intent));
        } else if (intent != null && intent.getBooleanExtra(EXTRA_REFRESH_ALL, false)) {
            setupNotificationUpdaterIfNecessary();
            downloadEnqueueExecutor.execute(() -> enqueueAll(intent));
        } else if (downloads.size() == 0) {
            shutdown();
        } else {
            Log.d(TAG, "unknown intent");
        }
        return Service.START_NOT_STICKY;
    }

    /**
     * Android 15 limits dataSync foreground services to 6h per day and calls this when the
     * quota is used up; the service must stop promptly or the app is killed.
     */
    @Override
    public void onTimeout(int startId, int fgsType) {
        Log.w(TAG, "foreground service timeout, cancelling all downloads");
        cancelAllDownloads();
        shutdown();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        RUNNING.set(false);
        // Stop accepting work before clearing the list: a queued enqueue task or a retry could
        // otherwise re-add a downloader after the clear, and the static list would carry it
        // into the next service instance, which would then never stop.
        downloadEnqueueExecutor.shutdownNow();
        downloadHandleExecutor.shutdownNow();
        downloads.clear();
        EventBus.getDefault().postSticky(DownloadEvent.refresh(Collections.emptyList()));

        boolean showAutoDownloadReport = Prefs.showAutoDownloadReport();
        if (Prefs.showDownloadReport() || showAutoDownloadReport) {
            notificationManager.updateReport(reportQueue, showAutoDownloadReport);
            reportQueue.clear();
        }

        unregisterReceiver(cancelDownloadReceiver);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            connectionMonitor.disable(getApplicationContext());
        }

        cancelNotificationUpdater();
        notificationUpdateExecutor.shutdownNow();
        if (downloadPostFuture != null) {
            downloadPostFuture.cancel(true);
        }

        // start auto download in case anything new has shown up
        DBTasks.autodownloadUndownloadedItems(getApplicationContext());
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
            if (downloader.cancelled || downloadHandleExecutor.isShutdown()) {
                return;
            }
            try {
                downloader.call();
            } catch (Exception e) {
                Log.e(TAG, "download threw", e);
            }
            try {
                if (downloader.getResult().isSuccessful()) {
                    retry = !handleSuccessfulDownload(downloader)
                            && handleFailedDownload(downloader);
                } else {
                    retry = handleFailedDownload(downloader);
                }
            } catch (Exception e) {
                Log.e(TAG, "download result handling threw", e);
            }
        } finally {
            // Always remove the downloader on this thread. Routing the removal through the
            // enqueue executor made it depend on that thread being alive and unblocked, which
            // is how entries ended up stuck in the "downloading" list forever.
            if (retry && !downloader.cancelled && !downloadHandleExecutor.isShutdown()) {
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
            downloads.remove(downloader);
            postDownloaders();
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
            LocalFeedUpdater.updateFeed(feed, DownloadService.this, (scanned, totalFiles) -> {
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
            downloads.remove(downloader);
            postDownloaders();
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
            FeedItem item = getFeedItemFromId(request.getFeedfileId());
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
            stopServiceIfEverythingDone();
            return;
        }
        try {
            downloadEnqueueExecutor.execute(this::stopServiceIfEverythingDone);
        } catch (RejectedExecutionException e) {
            stopServiceIfEverythingDone();
        }
    }

    /**
     * Returns true for errors that are typically transient (connection dropped, timeout, DNS
     * hiccup) and therefore worth one automatic retry.
     */
    @VisibleForTesting
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
    private boolean handleSuccessfulDownload(Downloader downloader) {
        DownloadRequest request = downloader.getDownloadRequest();
        DownloadStatus status = downloader.getResult();
        final int type = status.getFeedfileType();

        if (type == Feed.FEEDFILETYPE_FEED) {
            Log.d(TAG, "feed completed download");
            FeedSyncTask feedSyncTask = new FeedSyncTask(DownloadService.this, request);
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
                    newEpisodesNotification.showIfNeeded(DownloadService.this, feedSyncTask.getSavedFeed());
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
            MediaDownloadedHandler handler = new MediaDownloadedHandler(DownloadService.this, status, request);
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
     * Handles a download that did not succeed.
     *
     * @return true if the request should be re-submitted automatically (the caller takes care
     *         of that once the current downloader has been removed from the list), false if
     *         the failure is final. A FeedItemEvent for the media is posted by the caller in
     *         every case, so this method does not need to.
     */
    private boolean handleFailedDownload(Downloader downloader) {
        DownloadStatus status = downloader.getResult();
        DownloadRequest request = downloader.getDownloadRequest();
        final int type = status.getFeedfileType();

        if (status.isCancelled()) {
            return false;
        }

        if (status.getReason() == DownloadError.ERROR_UNAUTHORIZED) {
            notificationManager.postAuthenticationNotification(request);
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

        if (type == FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
            FeedItem item = getFeedItemFromId(status.getFeedfileId());
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

    private final BroadcastReceiver cancelDownloadReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "receiver cancel download intent " + intent.getAction());
            if (!isRunning()) {
                return;
            }
            if (TextUtils.equals(intent.getAction(), ACTION_CANCEL_DOWNLOAD)) {
                String url = intent.getStringExtra(EXTRA_DOWNLOAD_URL);
                if (url == null) {
                    throw new IllegalArgumentException("ACTION_CANCEL_DOWNLOAD intent needs download url extra");
                }
                downloadEnqueueExecutor.execute(() -> {
                    doCancel(url);
                    postDownloaders();
                    stopServiceIfEverythingDone();
                });
            } else if (TextUtils.equals(intent.getAction(), ACTION_CANCEL_ALL_DOWNLOADS)) {
                downloadEnqueueExecutor.execute(() -> {
                    cancelAllDownloads();
                    postDownloaders();
                    stopServiceIfEverythingDone();
                });
            }
        }
    };

    private void cancelAllDownloads() {
        Log.d(TAG, "cancel all downloads");
        for (Downloader d : downloads) {
            d.cancel();
        }
    }

    private synchronized void doCancel(String url) {
        Log.d(TAG, "cancel download url " + url);
        for (Downloader downloader : downloads) {
            if (downloader.cancelled || !downloader.getDownloadRequest().getSource().equals(url)) {
                continue;
            }
            downloader.cancel();
            DownloadRequest request = downloader.getDownloadRequest();
            FeedItem item = getFeedItemFromId(request.getFeedfileId());
            if (item != null) {
                EventBus.getDefault().post(FeedItemEvent.updated(item));
                // undo enqueue upon cancel
                if (request.isMediaEnqueued()) {
                    Log.v(TAG, "Undoing enqueue upon cancelling download");
                    DBWriter.removeQueueItem(getApplicationContext(), false, item);
                }
            }
        }
    }

    private void onDownloadQueued(Intent intent) {
        List<DownloadRequest> requests = intent.getParcelableArrayListExtra(EXTRA_REQUESTS);
        if (requests == null) {
            throw new IllegalArgumentException("ACTION_ENQUEUE_DOWNLOAD intent needs request extra");
        }
        Log.d(TAG, "receive download enqueue request " + requests.size());

        if (intent.getBooleanExtra(EXTRA_CLEANUP_MEDIA, false)) {
            EpisodeCleanupAlgorithmFactory.build().makeRoomForEpisodes(getApplicationContext(), requests.size());
        }

        for (DownloadRequest request : requests) {
            addNewRequest(request);
        }
        postDownloaders();
        stopServiceIfEverythingDone();

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
            actuallyEnqueued = DBTasks.enqueueFeedItemsToDownload(getApplicationContext(), feedItems);
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
        boolean initiatedByUser = intent.getBooleanExtra(EXTRA_INITIATED_BY_USER, false);
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
        postDownloaders();
        stopServiceIfEverythingDone();
    }


    synchronized private void addNewRequest(@NonNull DownloadRequest request) {
        if (isDownloadingFile(request.getSource())) {
            Log.d(TAG, "already in queue");
            return;
        } else if (downloadHandleExecutor.isShutdown()) {
            Log.d(TAG, "service is already shutting down.");
            return;
        }
        Log.d(TAG, "add new request -> " + request.getSource());
        if (request.getSource().startsWith(Feed.PREFIX_LOCAL_FOLDER)) {
            Downloader downloader = new LocalFeedStubDownloader(getApplicationContext(), request);
            submitDownloader(downloader, () -> performLocalFeedRefresh(downloader, request));
        } else {
            writeFileUrl(request);
            Downloader downloader = downloaderFactory.create(getApplicationContext(), request);
            if (downloader != null) {
                submitDownloader(downloader, () -> performDownload(downloader));
            }
        }
    }

    private void submitDownloader(@NonNull Downloader downloader, @NonNull Runnable task) {
        downloads.add(downloader);
        try {
            downloadHandleExecutor.submit(task);
        } catch (RejectedExecutionException e) {
            // executor shut down between the isShutdown() check and here; an entry left in the
            // static list would keep the next service instance from ever stopping
            downloads.remove(downloader);
            Log.w(TAG, "download executor rejected " + downloader.getDownloadRequest().getSource());
        }
    }

    /**
     * Replaces the factory that turns a {@link DownloadRequest} into a {@link Downloader}.
     * Tests only; production code always uses {@link DefaultDownloaderFactory}.
     */
    @VisibleForTesting
    public static void setDownloaderFactory(@NonNull DownloaderFactory downloaderFactory) {
        DownloadService.downloaderFactory = downloaderFactory;
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

    /**
     * Check if there's something else to download, otherwise stop.
     */
    private void stopServiceIfEverythingDone() {
        Log.d(TAG, downloads.size() + " remain download");
        if (downloads.size() <= 0) {
            Log.d(TAG, "try to shutdown");
            shutdown();
        }
    }

    @Nullable
    private FeedItem getFeedItemFromId(long id) {
        FeedMedia media = DBReader.getFeedMedia(id);
        if (media != null) {
            return media.getItem();
        } else {
            return null;
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

    /**
     * Schedules the notification updater task if it hasn't been scheduled yet.
     */
    private void setupNotificationUpdaterIfNecessary() {
        if (notificationUpdater == null) {
            notificationUpdater = new NotificationUpdater();
            notificationUpdaterFuture = notificationUpdateExecutor
                    .scheduleAtFixedRate(notificationUpdater, 1, 1, TimeUnit.SECONDS);
        }
    }

    private void cancelNotificationUpdater() {
        boolean result = false;
        if (notificationUpdaterFuture != null) {
            result = notificationUpdaterFuture.cancel(true);
        }
        notificationUpdater = null;
        notificationUpdaterFuture = null;
        Log.d(TAG, "NotificationUpdater cancelled. Result: " + result);
    }

    private class NotificationUpdater implements Runnable {
        public void run() {
            Notification n = notificationManager.updateNotifications(downloads);
            if (n != null) {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                nm.notify(R.id.notification_downloading, n);
            }
        }
    }

    private void postDownloaders() {
        new PostDownloaderTask(downloads).run();

        if (downloadPostFuture == null) {
            synchronized (this) {
                if (downloadPostFuture == null && !notificationUpdateExecutor.isShutdown()) {
                    downloadPostFuture = notificationUpdateExecutor.scheduleAtFixedRate(
                            new PostDownloaderTask(downloads), 1, 1, TimeUnit.SECONDS);
                }
            }
        }
    }

    private void shutdown() {
        // If the service was run for a very short time, the system may delay closing
        // the notification. Set the notification text now so that a misleading message
        // is not left on the notification.
        if (notificationUpdater != null) {
            notificationUpdater.run();
        }
        cancelNotificationUpdater();
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
}
