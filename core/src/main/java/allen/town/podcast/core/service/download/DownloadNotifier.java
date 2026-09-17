package allen.town.podcast.core.service.download;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import allen.town.podcast.core.R;
import allen.town.podcast.core.service.download.handler.PostDownloaderTask;
import allen.town.podcast.model.download.DownloadStatus;

/**
 * Owns everything the download service shows while it runs: the {@link DownloadServiceNotification}
 * builder, the single-threaded executor that refreshes the ongoing notification and re-posts the
 * download list once a second, and the end-of-run report. The service keeps the foreground
 * lifecycle itself (it is the only thing that can call startForeground/stopForeground) and asks
 * this class for the notification to show and for the periodic updates to start, run one last
 * time, or stop.
 */
class DownloadNotifier {
    private static final String TAG = "DownloadService";
    private static final int SCHED_EX_POOL_SIZE = 1;

    private final Service service;
    private final ScheduledThreadPoolExecutor notificationUpdateExecutor;
    private DownloadServiceNotification notificationManager;
    private NotificationUpdater notificationUpdater;
    private ScheduledFuture<?> notificationUpdaterFuture;
    private volatile ScheduledFuture<?> downloadPostFuture;

    DownloadNotifier(@NonNull Service service) {
        this.service = service;
        notificationUpdateExecutor = new ScheduledThreadPoolExecutor(SCHED_EX_POOL_SIZE,
                r -> {
                    Thread t = new Thread(r, "NotificationUpdateExecutor");
                    t.setPriority(Thread.MIN_PRIORITY);
                    return t;
                }, (r, executor) -> Log.w(TAG, "SchedEx rejected submission of new task")
        );
    }

    /** Builds the notification templates. Called from the service's onCreate. */
    void onServiceCreated() {
        notificationManager = new DownloadServiceNotification(service);
    }

    /** The notification describing the current queue, for startForeground. */
    @Nullable
    Notification buildSummaryNotification() {
        return notificationManager.updateNotifications(DownloadQueue.ENTRIES);
    }

    void postAuthenticationNotification(DownloadRequest request) {
        notificationManager.postAuthenticationNotification(request);
    }

    void updateReport(List<DownloadStatus> reportQueue, boolean showAutoDownloadReport) {
        notificationManager.updateReport(reportQueue, showAutoDownloadReport);
    }

    /**
     * Schedules the notification updater task if it hasn't been scheduled yet.
     */
    void setupNotificationUpdaterIfNecessary() {
        if (notificationUpdater == null) {
            notificationUpdater = new NotificationUpdater();
            notificationUpdaterFuture = notificationUpdateExecutor
                    .scheduleAtFixedRate(notificationUpdater, 1, 1, TimeUnit.SECONDS);
        }
    }

    /**
     * Refreshes the notification one last time before the service stops. If the service was run
     * for a very short time, the system may delay closing the notification, so the text is set
     * now to avoid leaving a misleading message behind.
     */
    void runFinalUpdateAndStop() {
        if (notificationUpdater != null) {
            notificationUpdater.run();
        }
        cancelNotificationUpdater();
    }

    /** Stops the periodic updates and the executor that runs them. */
    void shutdown() {
        cancelNotificationUpdater();
        notificationUpdateExecutor.shutdownNow();
        if (downloadPostFuture != null) {
            downloadPostFuture.cancel(true);
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
            Notification n = notificationManager.updateNotifications(DownloadQueue.ENTRIES);
            if (n != null) {
                NotificationManager nm = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
                nm.notify(R.id.notification_downloading, n);
            }
        }
    }

    /** Publishes the current download list to the UI, now and once a second from now on. */
    void postDownloaders() {
        new PostDownloaderTask(DownloadQueue.ENTRIES).run();

        if (downloadPostFuture == null) {
            synchronized (this) {
                if (downloadPostFuture == null && !notificationUpdateExecutor.isShutdown()) {
                    downloadPostFuture = notificationUpdateExecutor.scheduleAtFixedRate(
                            new PostDownloaderTask(DownloadQueue.ENTRIES), 1, 1, TimeUnit.SECONDS);
                }
            }
        }
    }
}
