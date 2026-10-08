package allen.town.podcast.core.feed;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.documentfile.provider.DocumentFile;

import org.apache.commons.io.FilenameUtils;

import java.util.Collections;
import java.util.concurrent.ExecutionException;

import allen.town.podcast.core.R;
import allen.town.podcast.core.storage.DBTasks;
import allen.town.podcast.core.storage.DBWriter;
import allen.town.podcast.model.feed.Feed;
import allen.town.podcast.model.feed.FeedPreferences;
import allen.town.podcast.model.feed.SortOrder;

/**
 * Creates feeds from the device's own storage: a folder ({@link Feed#PREFIX_LOCAL_FOLDER}) or one
 * single file ({@link Feed#PREFIX_LOCAL_FILE}), both picked with the storage access framework.
 * Each call keeps read access to the picked document across reboots, stores the feed (subscribed,
 * sorted in {@link SortOrder#TRACK_ORDER}, optionally marked as an audiobook) and starts the first
 * scan, which {@link LocalFeedUpdater} runs in the download service. Adding the same folder or
 * file again returns the existing feed and rescans it.
 *
 * <p>Both methods block on database writes, so they must not run on the main thread.</p>
 */
public final class LocalFeedCreator {

    private LocalFeedCreator() {
    }

    /**
     * Adds a folder picked with {@code ActivityResultContracts.OpenDocumentTree}.
     *
     * @param treeUri   the tree URI the picker returned
     * @param audiobook true to mark the new feed as an audiobook
     * @return the feed as stored in the database
     * @throws IllegalArgumentException if the folder cannot be resolved
     */
    @WorkerThread
    @NonNull
    public static Feed addLocalFolder(@NonNull Context context, @NonNull Uri treeUri, boolean audiobook) {
        context.getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        DocumentFile documentFile = DocumentFile.fromTreeUri(context, treeUri);
        if (documentFile == null) {
            throw new IllegalArgumentException("Unable to retrieve document tree");
        }
        String title = documentFile.getName();
        if (title == null) {
            title = context.getString(R.string.local_folder);
        }
        return addFeed(context, Feed.PREFIX_LOCAL_FOLDER + treeUri, title, audiobook);
    }

    /**
     * Adds one file picked with {@code ActivityResultContracts.OpenDocument}, typically a whole
     * audiobook (.m4b, .m4a, .mp3).
     *
     * @param documentUri the document URI the picker returned
     * @param audiobook   true to mark the new feed as an audiobook
     * @return the feed as stored in the database
     * @throws IllegalArgumentException if the file cannot be resolved
     */
    @WorkerThread
    @NonNull
    public static Feed addLocalFile(@NonNull Context context, @NonNull Uri documentUri, boolean audiobook) {
        context.getContentResolver().takePersistableUriPermission(documentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        DocumentFile documentFile = DocumentFile.fromSingleUri(context, documentUri);
        if (documentFile == null || !documentFile.exists()) {
            throw new IllegalArgumentException("Unable to open the file");
        }
        String name = documentFile.getName();
        String title = name != null ? FilenameUtils.getBaseName(name) : context.getString(R.string.local_file);
        return addFeed(context, Feed.PREFIX_LOCAL_FILE + documentUri, title, audiobook);
    }

    @NonNull
    private static Feed addFeed(@NonNull Context context, @NonNull String downloadUrl, @NonNull String title,
                                boolean audiobook) {
        Feed feed = new Feed(downloadUrl, null, title);
        feed.setItems(Collections.emptyList());
        feed.setSortOrder(SortOrder.TRACK_ORDER);
        feed.setSubscribed(true);

        Feed fromDatabase = DBTasks.updateFeed(context, feed, false);
        FeedPreferences preferences = fromDatabase.getPreferences();
        if (audiobook && preferences != null && !preferences.isAudiobook()) {
            try {
                // before the first scan, so that its tracks are stored as UNPLAYED, not NEW
                DBWriter.setFeedAudiobook(fromDatabase.getId(), true).get();
                preferences.setAudiobook(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while adding " + downloadUrl, e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("Unable to mark " + downloadUrl + " as an audiobook", e);
            }
        }
        DBTasks.forceRefreshFeed(context, fromDatabase, true);
        return fromDatabase;
    }
}
