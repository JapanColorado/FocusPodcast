package dev.russell.yagi.core.service.download;

import android.util.Log;
import android.webkit.URLUtil;
import dev.russell.yagi.core.feed.util.VideoModeUtils;
import dev.russell.yagi.core.pref.Prefs;
import dev.russell.yagi.core.util.FileNameGenerator;
import dev.russell.yagi.model.feed.Feed;
import dev.russell.yagi.model.feed.FeedMedia;
import dev.russell.yagi.model.playback.MediaType;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;

/**
 * Creates download requests that can be sent to the DownloadService.
 */
public class DownloadRequestCreator {
    private static final String TAG = "DownloadRequestCreat";
    private static final String FEED_DOWNLOADPATH = "cache/";
    private static final String MEDIA_DOWNLOADPATH = "media/";

    public static DownloadRequest.Builder create(Feed feed) {
        File dest = new File(getFeedfilePath(), getFeedfileName(feed));
        if (!isFilenameAvailable(dest.toString()) && !feed.isLocalFeed()) {
            dest = findUnusedFile(dest);
        }
        Log.d(TAG, "create download request " + feed.getDownload_url());

        String username = (feed.getPreferences() != null) ? feed.getPreferences().getUsername() : null;
        String password = (feed.getPreferences() != null) ? feed.getPreferences().getPassword() : null;

        return new DownloadRequest.Builder(dest.toString(), feed)
                .withAuthentication(username, password)
                .deleteOnFailure(true)
                .lastModified(feed.getLastUpdate());
    }

    /**
     * A download of this episode. A video episode whose podcast is listened to as audio only
     * (the "Video episodes" setting, {@link VideoModeUtils#isVideoPreferred}) downloads only its
     * audio track ({@link AudioOnlyDownloader}); everything else downloads the whole file.
     */
    public static DownloadRequest.Builder create(FeedMedia media) {
        return create(media, wantsAudioOnly(media));
    }

    /**
     * A download of the whole file, also for a video episode that would otherwise be downloaded
     * as audio only. Used once the listener agreed to download a video whose audio could not be
     * cut out of it.
     */
    public static DownloadRequest.Builder createFullDownload(FeedMedia media) {
        return create(media, false);
    }

    /** Whether {@link #create(FeedMedia)} would download only this episode's audio track. */
    public static boolean wantsAudioOnly(FeedMedia media) {
        return media.getMediaType() == MediaType.VIDEO
                && !media.isContentUri()
                && !VideoModeUtils.isVideoPreferred(media);
    }

    private static DownloadRequest.Builder create(FeedMedia media, boolean audioOnly) {
        boolean partiallyDownloadedFileExists =
                media.getFile_url() != null && new File(media.getFile_url()).exists();
        if (partiallyDownloadedFileExists && audioOnly && !FeedMedia.isAudioOnlyFile(media.getFile_url())) {
            // A whole-file download is under way (the listener agreed to it, or the podcast was
            // switched to audio only since): resume it rather than throw its progress away.
            audioOnly = false;
        } else if (partiallyDownloadedFileExists && !audioOnly && FeedMedia.isAudioOnlyFile(media.getFile_url())) {
            // Left over from an audio-only download, which a whole-file download cannot resume.
            Log.d(TAG, "discarding partial audio-only file " + media.getFile_url());
            FileUtils.deleteQuietly(new File(media.getFile_url()));
            partiallyDownloadedFileExists = false;
        }
        File dest;
        if (partiallyDownloadedFileExists) {
            dest = new File(media.getFile_url());
        } else {
            dest = new File(getMediafilePath(media), getMediafilename(media, audioOnly));
        }

        if (!isFilenameAvailable(dest.toString()) || (!partiallyDownloadedFileExists && dest.exists())) {
            dest = findUnusedFile(dest);
        }
        Log.d(TAG, "create download request url " + media.getDownload_url() + (audioOnly ? " (audio only)" : ""));

        String username = (media.getItem().getFeed().getPreferences() != null)
                ? media.getItem().getFeed().getPreferences().getUsername() : null;
        String password = (media.getItem().getFeed().getPreferences() != null)
                ? media.getItem().getFeed().getPreferences().getPassword() : null;

        return new DownloadRequest.Builder(dest.toString(), media)
                .deleteOnFailure(false)
                .withAuthentication(username, password)
                .audioOnly(audioOnly);
    }

    private static File findUnusedFile(File dest) {
        // find different name
        File newDest = null;
        for (int i = 1; i < Integer.MAX_VALUE; i++) {
            String newName = FilenameUtils.getBaseName(dest
                    .getName())
                    + "-"
                    + i
                    + FilenameUtils.EXTENSION_SEPARATOR
                    + FilenameUtils.getExtension(dest.getName());
            newDest = new File(dest.getParent(), newName);
            if (!newDest.exists() && isFilenameAvailable(newDest.toString())) {
                Log.d(TAG, "use new file name " + newName);
                break;
            }
        }
        return newDest;
    }

    /**
     * Returns true if a filename is available and false if it has already been
     * taken by another requested download.
     */
    private static boolean isFilenameAvailable(String path) {
        for (Downloader downloader : DownloadService.downloads) {
            // same definition of "in flight" as DownloadService.isDownloadingFile()
            if (!downloader.cancelled && downloader.request.getDestination().equals(path)) {
                return false;
            }
        }
        return true;
    }

    private static String getFeedfilePath() {
        return Prefs.getDataFolder(FEED_DOWNLOADPATH).toString() + "/";
    }

    private static String getFeedfileName(Feed feed) {
        String filename = feed.getDownload_url();
        if (feed.getTitle() != null && !feed.getTitle().isEmpty()) {
            filename = feed.getTitle();
        }
        return "feed-" + FileNameGenerator.generateFileName(filename) + feed.getId();
    }

    private static String getMediafilePath(FeedMedia media) {
        String mediaPath = MEDIA_DOWNLOADPATH
                + FileNameGenerator.generateFileName(media.getItem().getFeed().getTitle());
        return Prefs.getDataFolder(mediaPath).toString() + "/";
    }

    private static String getMediafilename(FeedMedia media, boolean audioOnly) {
        String titleBaseFilename = "";

        // Try to generate the filename by the item title
        if (media.getItem() != null && media.getItem().getTitle() != null) {
            String title = media.getItem().getTitle();
            titleBaseFilename = FileNameGenerator.generateFileName(title);
        }

        String urlBaseFilename = URLUtil.guessFileName(media.getDownload_url(), null, media.getMime_type());

        String baseFilename;
        if (!titleBaseFilename.equals("")) {
            baseFilename = titleBaseFilename;
        } else {
            baseFilename = urlBaseFilename;
        }
        final int filenameMaxLength = 220;
        if (baseFilename.length() > filenameMaxLength) {
            baseFilename = baseFilename.substring(0, filenameMaxLength);
        }
        String extension = audioOnly ? FeedMedia.AUDIO_ONLY_FILE_EXTENSION : FilenameUtils.getExtension(urlBaseFilename);
        return baseFilename + FilenameUtils.EXTENSION_SEPARATOR + media.getId()
                + FilenameUtils.EXTENSION_SEPARATOR + extension;
    }
}
