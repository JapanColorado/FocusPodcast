package allen.town.podcast.core.service.download;

import android.content.Context;
import android.util.Log;
import android.webkit.URLUtil;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class DefaultDownloaderFactory implements DownloaderFactory {
    private static final String TAG = "DefaultDwnldrFactory";

    @Nullable
    @Override
    public Downloader create(@NonNull Context context, @NonNull DownloadRequest request) {
        if (!URLUtil.isHttpUrl(request.getSource()) && !URLUtil.isHttpsUrl(request.getSource())) {
            Log.e(TAG, "Could not find appropriate downloader for " + request.getSource());
            return null;
        }
        return new HttpDownloader(context, request);
    }
}