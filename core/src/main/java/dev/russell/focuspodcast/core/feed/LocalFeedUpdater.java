package dev.russell.focuspodcast.core.feed;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.russell.focuspodcast.core.R;
import dev.russell.focuspodcast.model.download.DownloadStatus;
import dev.russell.focuspodcast.core.storage.DBReader;
import dev.russell.focuspodcast.core.storage.DBTasks;
import dev.russell.focuspodcast.core.storage.DBWriter;
import dev.russell.focuspodcast.core.util.FeedItemPermutors;
import dev.russell.focuspodcast.core.util.comparator.NaturalOrderComparator;
import dev.russell.focuspodcast.parser.feed.util.DateUtils;
import dev.russell.focuspodcast.model.download.DownloadError;
import dev.russell.focuspodcast.model.feed.Feed;
import dev.russell.focuspodcast.model.feed.FeedItem;
import dev.russell.focuspodcast.model.feed.FeedMedia;
import dev.russell.focuspodcast.model.feed.FeedPreferences;
import dev.russell.focuspodcast.parser.feed.util.MimeTypeUtils;
import dev.russell.focuspodcast.parser.media.id3.ID3ReaderException;
import dev.russell.focuspodcast.parser.media.id3.Id3MetadataReader;
import dev.russell.focuspodcast.parser.media.vorbis.VorbisCommentMetadataReader;
import dev.russell.focuspodcast.parser.media.vorbis.VorbisCommentReaderException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.input.CountingInputStream;

public class LocalFeedUpdater {
    private static final String TAG = "LocalFeedUpdater";

    /**
     * File names (compared case-insensitively) that are used as the cover of a folder feed, in
     * order of preference.
     */
    static final String[] PREFERRED_FEED_IMAGE_FILENAMES = {
            "folder.jpg", "folder.jpeg", "folder.png", "folder.webp",
            "cover.jpg", "cover.jpeg", "cover.png", "cover.webp",
            "front.jpg", "front.jpeg", "front.png",
            "albumart.jpg", "album.jpg", "album.png",
    };

    public static void updateFeed(Feed feed, Context context,
                                  @Nullable UpdaterProgressListener updaterProgressListener) {
        try {
            if (feed.isLocalFileFeed()) {
                tryUpdateFileFeed(feed, context, updaterProgressListener);
            } else {
                tryUpdateFolderFeed(feed, context, updaterProgressListener);
            }

            if (mustReportDownloadSuccessful(feed)) {
                reportSuccess(feed);
            }
        } catch (Exception e) {
            // Not fatal: the failure is recorded on the feed itself by reportError(), which is what
            // the download report and the "last update failed" flag in the UI are built from.
            Log.e(TAG, "Failed to update local feed " + feed.getDownload_url(), e);
            reportError(feed, e.getMessage());
        }
    }

    private static void tryUpdateFolderFeed(Feed feed, Context context,
                                            @Nullable UpdaterProgressListener updaterProgressListener)
            throws IOException {
        Uri treeUri = Uri.parse(feed.getDownload_url().substring(Feed.PREFIX_LOCAL_FOLDER.length()));
        DocumentFile documentFolder = DocumentFile.fromTreeUri(context, treeUri);
        if (documentFolder == null) {
            throw new IOException("Unable to retrieve document tree. "
                    + "Try re-connecting the folder on the podcast info page.");
        }
        if (!documentFolder.exists() || !documentFolder.canRead()) {
            throw new IOException("Cannot read local directory. "
                    + "Try re-connecting the folder on the podcast info page.");
        }

        if (feed.getItems() == null) {
            feed.setItems(new ArrayList<>());
        }
        //make sure it is the latest 'version' of this feed from the db (all items etc)
        feed = DBTasks.updateFeed(context, feed, false);

        LocalFolderScanner.Result scan = LocalFolderScanner.scan(context, treeUri);
        TagTally tally = new TagTally();
        boolean everyItemRead = mergeFiles(feed, scan.mediaFiles, context, tally, updaterProgressListener);

        List<FeedItem> items = feed.getItems();
        feed.setImageUrl(getImageUrl(scan.imageFiles, items,
                Feed.PREFIX_GENERATIVE_COVER + documentFolder.getUri()));

        feed.getPreferences().setAutoDownload(false);
        feed.getPreferences().setAutoDeleteAction(FeedPreferences.AutoDeleteAction.NO);
        feed.setDescription(context.getString(R.string.local_feed_description));
        if (everyItemRead) {
            // Only when every file's tags were read in this pass (the first scan, or after all
            // files changed): a partial pass would vote on a few files only. A user's rename is
            // a custom title and always wins over this.
            String album = tally.mostCommonAlbum();
            if (album != null) {
                feed.setTitle(album);
            }
            String artist = tally.mostCommonArtist();
            feed.setAuthor(artist != null ? artist : context.getString(R.string.local_folder));
        } else if (feed.getAuthor() == null) {
            feed.setAuthor(context.getString(R.string.local_folder));
        }

        // update items, delete items without existing file;
        // only delete items if the folder contains at least one element to avoid accidentally
        // deleting played state or position in case the folder is temporarily unavailable.
        // A truncated scan is not exhaustive either.
        boolean removeUnlistedItems = !items.isEmpty() && !scan.truncated;
        DBTasks.updateFeed(context, feed, removeUnlistedItems);
    }

    /**
     * A feed made from one file, usually a whole audiobook (.m4b): one item, metadata from the
     * file's tags.
     */
    private static void tryUpdateFileFeed(Feed feed, Context context,
                                          @Nullable UpdaterProgressListener updaterProgressListener)
            throws IOException {
        Uri documentUri = Uri.parse(feed.getDownload_url().substring(Feed.PREFIX_LOCAL_FILE.length()));
        DocumentFile document = DocumentFile.fromSingleUri(context, documentUri);
        if (document == null || !document.exists() || !document.canRead()) {
            throw new IOException("Cannot read the local file. Try adding it again.");
        }
        String name = document.getName() != null ? document.getName() : documentUri.getLastPathSegment();
        if (name == null) {
            name = "";
        }
        LocalFolderScanner.LocalFile file = new LocalFolderScanner.LocalFile(documentUri, name, name,
                MimeTypeUtils.getMimeType(document.getType(), name), document.length(),
                document.lastModified(), 0);

        if (feed.getItems() == null) {
            feed.setItems(new ArrayList<>());
        }
        feed = DBTasks.updateFeed(context, feed, false);

        TagTally tally = new TagTally();
        boolean everyItemRead = mergeFiles(feed, Collections.singletonList(file), context, tally,
                updaterProgressListener);

        List<FeedItem> items = feed.getItems();
        feed.setImageUrl(getImageUrl(Collections.emptyList(), items,
                Feed.PREFIX_GENERATIVE_COVER + documentUri));
        feed.getPreferences().setAutoDownload(false);
        feed.getPreferences().setAutoDeleteAction(FeedPreferences.AutoDeleteAction.NO);
        feed.setDescription(context.getString(R.string.local_file_feed_description));
        if (everyItemRead) {
            String album = tally.mostCommonAlbum();
            if (album != null) {
                feed.setTitle(album);
            } else if (!items.isEmpty() && !TextUtils.isEmpty(items.get(0).getTitle())) {
                feed.setTitle(items.get(0).getTitle());
            }
            String artist = tally.mostCommonArtist();
            feed.setAuthor(artist != null ? artist : context.getString(R.string.local_file));
        } else if (feed.getAuthor() == null) {
            feed.setAuthor(context.getString(R.string.local_file));
        }
        DBTasks.updateFeed(context, feed, !items.isEmpty());
    }

    /**
     * Adds an item for every new file, re-reads the tags of files whose size changed and drops
     * items whose file is gone (from {@code feed.getItems()}; the database is updated by the
     * caller). Items are identified by their relative path, stored as the item's link. Rows
     * written before subfolders were scanned hold the bare file name, which is the relative path
     * of a file at the top of the folder, so they keep their played state and position.
     *
     * @return true if the tags of every remaining item were read in this pass
     */
    private static boolean mergeFiles(Feed feed, List<LocalFolderScanner.LocalFile> mediaFiles,
                                      Context context, TagTally tally,
                                      @Nullable UpdaterProgressListener updaterProgressListener) {
        List<FeedItem> items = feed.getItems();
        Map<String, FeedItem> byPath = new HashMap<>();
        for (FeedItem item : items) {
            if (item.getMedia() != null && item.getLink() != null) {
                byPath.put(item.getLink(), item);
            }
        }
        Set<String> paths = new HashSet<>();
        boolean everyItemRead = true;
        for (int i = 0; i < mediaFiles.size(); i++) {
            LocalFolderScanner.LocalFile file = mediaFiles.get(i);
            paths.add(file.relativePath);
            FeedItem oldItem = byPath.get(file.relativePath);
            if (oldItem == null) {
                FeedItem newItem = createFeedItem(feed, file, context, tally);
                items.add(newItem);
                byPath.put(file.relativePath, newItem);
            } else if (oldItem.getMedia() != null && oldItem.getMedia().getSize() != file.size) {
                // file content changed: re-read metadata; unchanged files are left alone so a
                // refresh does not parse every file in the folder again
                oldItem.updateFromOther(createFeedItem(feed, file, context, tally));
            } else {
                everyItemRead = false;
            }
            if (updaterProgressListener != null) {
                updaterProgressListener.onLocalFileScanned(i, mediaFiles.size());
            }
        }

        // remove feed items without corresponding file
        Iterator<FeedItem> it = items.iterator();
        while (it.hasNext()) {
            if (!paths.contains(it.next().getLink())) {
                it.remove();
            }
        }
        return everyItemRead;
    }

    /**
     * Returns the image URL for a local feed: a cover file (see
     * {@link #PREFERRED_FEED_IMAGE_FILENAMES}) closest to the top of the folder, else the first
     * image file closest to the top, else the embedded picture of the first track in track order,
     * else {@code fallback} (a generated cover).
     */
    @NonNull
    static String getImageUrl(@NonNull List<LocalFolderScanner.LocalFile> images,
                              @NonNull List<FeedItem> items, @NonNull String fallback) {
        LocalFolderScanner.LocalFile best = null;
        int bestRank = Integer.MAX_VALUE;
        for (LocalFolderScanner.LocalFile image : images) {
            int preference = preferredImageIndex(image.name);
            if (preference < 0) {
                continue;
            }
            int rank = image.depth * PREFERRED_FEED_IMAGE_FILENAMES.length + preference;
            if (rank < bestRank) {
                best = image;
                bestRank = rank;
            }
        }
        if (best == null) {
            for (LocalFolderScanner.LocalFile image : images) {
                if (!isCoverFormat(image.mimeType)) {
                    continue;
                }
                if (best == null || image.depth < best.depth || (image.depth == best.depth
                        && NaturalOrderComparator.INSTANCE.compare(image.relativePath, best.relativePath) < 0)) {
                    best = image;
                }
            }
        }
        if (best != null) {
            return best.uri.toString();
        }

        List<FeedItem> sorted = new ArrayList<>(items);
        sorted.sort(FeedItemPermutors.TRACK_ORDER_COMPARATOR);
        if (!sorted.isEmpty()) {
            FeedMedia first = sorted.get(0).getMedia();
            if (first != null && first.getLocalMediaUrl() != null && first.hasEmbeddedPicture()) {
                // MetadataRetrieverLoader (Glide) reads the picture out of the file
                return FeedMedia.FILENAME_PREFIX_EMBEDDED_COVER + first.getLocalMediaUrl();
            }
        }
        return fallback;
    }

    private static int preferredImageIndex(String name) {
        for (int i = 0; i < PREFERRED_FEED_IMAGE_FILENAMES.length; i++) {
            if (PREFERRED_FEED_IMAGE_FILENAMES[i].equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isCoverFormat(@Nullable String mime) {
        return mime != null && (mime.startsWith("image/jpeg") || mime.startsWith("image/png")
                || mime.startsWith("image/webp"));
    }

    private static FeedItem createFeedItem(Feed feed, LocalFolderScanner.LocalFile file, Context context,
                                           TagTally tally) {
        FeedItem item = new FeedItem(0, FilenameUtils.getBaseName(file.name), UUID.randomUUID().toString(),
                file.relativePath, new Date(file.lastModified), FeedItem.UNPLAYED, feed);
        item.disableAutoDownload();

        String uri = file.uri.toString();
        FeedMedia media = new FeedMedia(0, item, 0, 0, file.size, file.mimeType,
                uri, uri, false, null, 0, 0);
        item.setMedia(media);

        try {
            loadMetadata(item, file.uri, context, tally);
        } catch (Exception e) {
            item.setDescriptionIfLonger(e.getMessage());
        }

        return item;
    }

    private static void loadMetadata(FeedItem item, Uri uri, Context context, TagTally tally) {
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        try {
            loadMetadata(item, uri, context, mediaMetadataRetriever, tally);
        } finally {
            // native resource, must be released explicitly
            try {
                mediaMetadataRetriever.release();
            } catch (IOException e) {
                Log.w(TAG, "unable to release MediaMetadataRetriever: " + e.getMessage());
            }
        }
    }

    private static void loadMetadata(FeedItem item, Uri uri, Context context,
                                     MediaMetadataRetriever mediaMetadataRetriever, TagTally tally) {
        mediaMetadataRetriever.setDataSource(context, uri);

        String dateStr = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE);
        if (!TextUtils.isEmpty(dateStr) && !"19040101T000000.000Z".equals(dateStr)) {
            try {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.getDefault());
                item.setPubDate(simpleDateFormat.parse(dateStr));
            } catch (ParseException parseException) {
                Date date = DateUtils.parse(dateStr);
                if (date != null) {
                    item.setPubDate(date);
                }
            }
        }

        String title = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        if (!TextUtils.isEmpty(title)) {
            item.setTitle(title);
        }

        String durationStr = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        if (!TextUtils.isEmpty(durationStr)) {
            item.getMedia().setDuration((int) Long.parseLong(durationStr));
        }

        item.setTrackNumber(parseLeadingNumber(
                mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)));
        item.setDiscNumber(parseLeadingNumber(
                mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)));
        tally.addAlbum(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
        String albumArtist = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
        tally.addArtist(!TextUtils.isEmpty(albumArtist) ? albumArtist
                : mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));

        item.getMedia().setHasEmbeddedPicture(mediaMetadataRetriever.getEmbeddedPicture() != null);

        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            Id3MetadataReader reader = new Id3MetadataReader(new CountingInputStream(inputStream));
            reader.readInputStream();
            item.setDescriptionIfLonger(reader.getComment());
        } catch (IOException | ID3ReaderException e) {
            Log.w(TAG, "unable to parse ID3  " + uri + ": " + e.getMessage());

            try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
                VorbisCommentMetadataReader reader = new VorbisCommentMetadataReader(inputStream);
                reader.readInputStream();
                item.setDescriptionIfLonger(reader.getDescription());
            } catch (IOException | VorbisCommentReaderException e2) {
                Log.w(TAG, "unable to parse vorbis comments  " + uri + ": " + e2.getMessage());
            }
        }
    }

    /**
     * Parses the number at the start of a track or disc tag such as "3", "03" or "3/12";
     * 0 when there is none.
     */
    static int parseLeadingNumber(@Nullable String value) {
        if (value == null) {
            return 0;
        }
        String trimmed = value.trim();
        int end = 0;
        while (end < trimmed.length() && end < 9 && Character.isDigit(trimmed.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(trimmed.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Counts album and artist tags across the files of one scan to pick the feed's metadata. */
    static final class TagTally {
        private final Map<String, Integer> albums = new LinkedHashMap<>();
        private final Map<String, Integer> artists = new LinkedHashMap<>();

        void addAlbum(@Nullable String album) {
            add(albums, album);
        }

        void addArtist(@Nullable String artist) {
            add(artists, artist);
        }

        @Nullable
        String mostCommonAlbum() {
            return mostCommon(albums);
        }

        @Nullable
        String mostCommonArtist() {
            return mostCommon(artists);
        }

        private static void add(Map<String, Integer> counts, @Nullable String value) {
            if (value == null || value.trim().isEmpty()) {
                return;
            }
            String key = value.trim();
            Integer count = counts.get(key);
            counts.put(key, count == null ? 1 : count + 1);
        }

        /** The most frequent value; the first one seen wins a tie. */
        @Nullable
        private static String mostCommon(Map<String, Integer> counts) {
            String best = null;
            int bestCount = 0;
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (entry.getValue() > bestCount) {
                    best = entry.getKey();
                    bestCount = entry.getValue();
                }
            }
            return best;
        }
    }

    private static void reportError(Feed feed, String reasonDetailed) {
        DownloadStatus status = new DownloadStatus(feed, feed.getTitle(),
                DownloadError.ERROR_IO_ERROR, false, reasonDetailed, true);
        DBWriter.addDownloadStatus(status);
        DBWriter.setFeedLastUpdateFailed(feed.getId(), true);
    }

    /**
     * Reports a successful download status.
     */
    private static void reportSuccess(Feed feed) {
        DownloadStatus status = new DownloadStatus(feed, feed.getTitle(),
                DownloadError.SUCCESS, true, null, true);
        DBWriter.addDownloadStatus(status);
        DBWriter.setFeedLastUpdateFailed(feed.getId(), false);
    }

    /**
     * Answers if reporting success is needed for the given feed.
     */
    private static boolean mustReportDownloadSuccessful(Feed feed) {
        List<DownloadStatus> downloadStatuses = DBReader.getFeedDownloadLog(feed.getId());

        if (downloadStatuses.isEmpty()) {
            // report success if never reported before
            return true;
        }

        Collections.sort(downloadStatuses, (downloadStatus1, downloadStatus2) ->
                downloadStatus1.getCompletionDate().compareTo(downloadStatus2.getCompletionDate()));

        DownloadStatus lastDownloadStatus = downloadStatuses.get(downloadStatuses.size() - 1);

        // report success if the last update was not successful
        // (avoid logging success again if the last update was ok)
        return !lastDownloadStatus.isSuccessful();
    }

    @FunctionalInterface
    public interface UpdaterProgressListener {
        void onLocalFileScanned(int scanned, int totalFiles);
    }
}
