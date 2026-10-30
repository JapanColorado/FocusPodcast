package dev.russell.yagi.core.service.download;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface DownloaderFactory {
    @Nullable
    Downloader create(@NonNull Context context, @NonNull DownloadRequest request);
}