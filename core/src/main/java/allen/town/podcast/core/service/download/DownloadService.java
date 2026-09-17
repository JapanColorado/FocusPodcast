package allen.town.podcast.core.service.download;

import android.annotation.SuppressLint;
import android.app.Notification;
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


import allen.town.podcast.common.util.Timber;
import allen.town.podcast.core.BuildConfig;
import allen.town.podcast.core.R;
import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import allen.town.podcast.core.event.DownloadEvent;
import allen.town.podcast.core.util.download.ConnectionStateMonitor;
import allen.town.podcast.model.feed.FeedItem;
import allen.town.podcast.model.feed.FeedMedia;
import allen.town.podcast.core.pref.Prefs;
import allen.town.podcast.core.storage.DBReader;
import allen.town.podcast.core.storage.DBTasks;
import allen.town.podcast.model.download.DownloadError;

/**
 * Manages the download of feedfiles in the app. Downloads can be enqueued via the startService intent.
 * The argument of the intent is an instance of DownloadRequest in the EXTRA_REQUESTS field of
 * the intent.
 * After the downloads have finished, the downloaded object will be passed on to a specific handler, depending on the
 * type of the feedfile.
 *
 * <p>This class owns the Android side of that: the static entry points the rest of the app calls,
 * the service lifecycle, the foreground state and the cancel broadcast receiver. The work itself is
 * split across four collaborators it creates and drives: {@link DownloadQueue} (what is in flight
 * and the pool that runs it), {@link DownloadPipeline} (the enqueue thread, the download bodies and
 * the retry), {@link DownloadCompletionHandler} (what a finished download means) and
 * {@link DownloadNotifier} (the ongoing notification and the report).</p>
 */
public class DownloadService extends Service {
    private static final String TAG = "DownloadService";
    public static final String ACTION_CANCEL_DOWNLOAD = "action.allen.town.podcast.core.service.cancelDownload";
    public static final String ACTION_CANCEL_ALL_DOWNLOADS = "action.allen.town.podcast.core.service.cancelAll";
    public static final String EXTRA_DOWNLOAD_URL = "downloadUrl";
    public static final String EXTRA_REQUESTS = "downloadRequests";
    public static final String EXTRA_REFRESH_ALL = "refreshAll";
    public static final String EXTRA_INITIATED_BY_USER = "initiatedByUser";
    public static final String EXTRA_CLEANUP_MEDIA = "cleanupMedia";

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    /**
     * The downloaders currently enqueued or running, owned by {@link DownloadQueue}. Kept here
     * under the name the rest of the package already reads it by. All reads are guarded by
     * {@link #isRunning()} so that an empty list observed while no service is alive is reported as
     * "not downloading" rather than as a stale answer.
     */
    static final List<Downloader> downloads = DownloadQueue.ENTRIES;

    private final DownloadQueue queue;
    private final DownloadNotifier notifier;
    private final DownloadCompletionHandler completionHandler;
    private final DownloadPipeline pipeline;
    private final NewEpisodesNotification newEpisodesNotification;
    static DownloaderFactory downloaderFactory = new DefaultDownloaderFactory();
    private ConnectionStateMonitor connectionMonitor;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public DownloadService() {
        newEpisodesNotification = new NewEpisodesNotification();

        Log.d(TAG, "init download service " + Prefs.getParallelDownloads());
        queue = new DownloadQueue();
        notifier = new DownloadNotifier(this);
        completionHandler = new DownloadCompletionHandler(this, newEpisodesNotification, notifier);
        pipeline = new DownloadPipeline(this, queue, notifier, completionHandler,
                this::stopServiceIfEverythingDone);
        // Must be the first runnable in syncExecutor
        pipeline.submitBeforeAnyRequest(newEpisodesNotification::loadCountersBeforeRefresh);
    }

    @Override
    // Lint's UnspecifiedRegisterReceiverFlag fires on the pre-Android-13 branch below,
    // where the two-argument registerReceiver is the only overload that exists. The
    // exported flags are passed on Android 13+, which is where they are enforced.
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    public void onCreate() {
        Log.d(TAG, "onCreate");
        RUNNING.set(true);
        notifier.onServiceCreated();

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

    /** @see DownloadQueue#MAX_REQUESTS_PER_INTENT */
    @VisibleForTesting
    static final int MAX_REQUESTS_PER_INTENT = DownloadQueue.MAX_REQUESTS_PER_INTENT;

    /** Tells whether a download for the given URL is already in flight. */
    @VisibleForTesting
    interface InFlightCheck extends DownloadQueue.InFlightCheck {
    }

    /** @see DownloadQueue#filterInFlight */
    @VisibleForTesting
    static ArrayList<DownloadRequest> filterInFlight(DownloadRequest[] requests, InFlightCheck inFlight) {
        return DownloadQueue.filterInFlight(requests, inFlight);
    }

    /** @see DownloadQueue#capToIntentLimit */
    @VisibleForTesting
    static ArrayList<DownloadRequest> capToIntentLimit(List<DownloadRequest> requests) {
        return DownloadQueue.capToIntentLimit(requests);
    }

    /** @see DownloadCompletionHandler#isTransientError */
    @VisibleForTesting
    static boolean isTransientError(@Nullable DownloadError reason) {
        return DownloadCompletionHandler.isTransientError(reason);
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
        return DownloadQueue.isDownloadingFeeds();
    }

    public static boolean isDownloadingFile(String downloadUrl) {
        if (!isRunning()) {
            return false;
        }
        return DownloadQueue.isDownloadingFile(downloadUrl);
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
        return DownloadQueue.findRequest(downloadUrl);
    }

    /**
     * Replaces the factory that turns a {@link DownloadRequest} into a {@link Downloader}.
     * Tests only; production code always uses {@link DefaultDownloaderFactory}.
     */
    @VisibleForTesting
    public static void setDownloaderFactory(@NonNull DownloaderFactory downloaderFactory) {
        DownloadService.downloaderFactory = downloaderFactory;
    }

    /** The item owning the media with the given id, or null if there is none. */
    @Nullable
    static FeedItem getFeedItemFromId(long id) {
        FeedMedia media = DBReader.getFeedMedia(id);
        if (media != null) {
            return media.getItem();
        } else {
            return null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // The service was started with startForegroundService(), so startForeground() must be
        // called on every path (including the immediate shutdown below) to honour the 5s contract.
        Notification notification = notifier.buildSummaryNotification();
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
            notifier.setupNotificationUpdaterIfNecessary();
            pipeline.enqueue(intent);
        } else if (intent != null && intent.getBooleanExtra(EXTRA_REFRESH_ALL, false)) {
            notifier.setupNotificationUpdaterIfNecessary();
            pipeline.enqueueAllFeeds(intent);
        } else if (queue.size() == 0) {
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
        queue.cancelAll();
        shutdown();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        RUNNING.set(false);
        // Stop accepting work before clearing the list: a queued enqueue task or a retry could
        // otherwise re-add a downloader after the clear, and the static list would carry it
        // into the next service instance, which would then never stop.
        pipeline.shutdownNow();
        queue.shutdownNow();
        queue.clear();
        EventBus.getDefault().postSticky(DownloadEvent.refresh(Collections.emptyList()));

        boolean showAutoDownloadReport = Prefs.showAutoDownloadReport();
        if (Prefs.showDownloadReport() || showAutoDownloadReport) {
            notifier.updateReport(completionHandler.getReportQueue(), showAutoDownloadReport);
            completionHandler.getReportQueue().clear();
        }

        unregisterReceiver(cancelDownloadReceiver);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            connectionMonitor.disable(getApplicationContext());
        }

        notifier.shutdown();

        // start auto download in case anything new has shown up
        DBTasks.autodownloadUndownloadedItems(getApplicationContext());
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
                pipeline.cancel(url);
            } else if (TextUtils.equals(intent.getAction(), ACTION_CANCEL_ALL_DOWNLOADS)) {
                pipeline.cancelAll();
            }
        }
    };

    /**
     * Check if there's something else to download, otherwise stop.
     */
    private void stopServiceIfEverythingDone() {
        Log.d(TAG, queue.size() + " remain download");
        if (queue.size() <= 0) {
            Log.d(TAG, "try to shutdown");
            shutdown();
        }
    }

    private void shutdown() {
        // If the service was run for a very short time, the system may delay closing
        // the notification. Set the notification text now so that a misleading message
        // is not left on the notification.
        notifier.runFinalUpdateAndStop();
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
}
