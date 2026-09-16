package allen.town.podcast.core.service.download;

import android.content.Context;
import android.net.wifi.WifiManager;
import androidx.annotation.NonNull;

import java.util.Date;
import java.util.concurrent.Callable;

import allen.town.podcast.core.R;
import allen.town.podcast.model.download.DownloadStatus;

/**
 * Downloads files
 */
public abstract class Downloader implements Callable<Downloader> {
    private static final String TAG = "Downloader";

    private volatile boolean finished;
    public volatile boolean cancelled;
    public String permanentRedirectUrl = null;

    @NonNull
    final DownloadRequest request;
    @NonNull
    final DownloadStatus result;
    /** Application context; downloads outlive the Service instance that enqueued them. */
    @NonNull
    final Context appContext;

    Downloader(@NonNull Context context, @NonNull DownloadRequest request) {
        super();
        this.appContext = context.getApplicationContext();
        this.request = request;
        this.request.setStatusMsg(R.string.download_pending);
        this.cancelled = false;
        this.result = new DownloadStatus(0, request.getTitle(), request.getFeedfileId(), request.getFeedfileType(),
                false, cancelled, false, null, new Date(), null, request.isInitiatedByUser());
    }

    protected abstract void download();

    public final Downloader call() {
        WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
        WifiManager.WifiLock wifiLock = null;
        if (wifiManager != null) {
            wifiLock = wifiManager.createWifiLock(TAG);
            wifiLock.acquire();
        }

        try {
            download();
        } finally {
            // release even if download() throws, otherwise the lock is held until process death
            if (wifiLock != null && wifiLock.isHeld()) {
                wifiLock.release();
            }
            finished = true;
        }
        return this;
    }

    @NonNull
    public DownloadRequest getDownloadRequest() {
        return request;
    }

    @NonNull
    public DownloadStatus getResult() {
        return result;
    }

    public boolean isFinished() {
        return finished;
    }

    public void cancel() {
        cancelled = true;
    }

}