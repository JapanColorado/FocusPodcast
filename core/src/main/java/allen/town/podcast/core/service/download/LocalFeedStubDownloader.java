package allen.town.podcast.core.service.download;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * This does not actually download, but it keeps track of a local feed's refresh state.
 */
public class LocalFeedStubDownloader extends Downloader {

    public LocalFeedStubDownloader(@NonNull Context context, @NonNull DownloadRequest request) {
        super(context, request);
    }

    @Override
    protected void download() {
    }
}