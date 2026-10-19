package dev.russell.focuspodcast.core.feed;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.documentfile.provider.DocumentFile;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import dev.russell.focuspodcast.core.R;
import dev.russell.focuspodcast.core.storage.DBTasks;
import dev.russell.focuspodcast.core.storage.DBWriter;
import dev.russell.focuspodcast.model.feed.Feed;
import dev.russell.focuspodcast.model.feed.FeedPreferences;
import dev.russell.focuspodcast.model.feed.SortOrder;

/**
 * Creates feeds from folders on the device's own storage ({@link Feed#PREFIX_LOCAL_FOLDER}), picked
 * with the storage access framework. It keeps read access to the picked folder across reboots,
 * stores the feed (subscribed, sorted in {@link SortOrder#TRACK_ORDER}, marked as an audiobook when
 * asked to or when it looks like one) and starts the first scan, which {@link LocalFeedUpdater}
 * runs in the download service. Adding the same folder again returns the existing feed and
 * rescans it. Single-file feeds ({@link Feed#PREFIX_LOCAL_FILE}) can no longer be created, but
 * existing ones are still updated.
 *
 * <p>It blocks on database writes, so they must not run on the main thread.</p>
 */
public final class LocalFeedCreator {
    private static final String TAG = "LocalFeedCreator";

    private LocalFeedCreator() {
    }

    /**
     * Adds a folder picked with {@code ActivityResultContracts.OpenDocumentTree}.
     *
     * @param treeUri   the tree URI the picker returned
     * @param audiobook true or false to mark the new feed as an audiobook or a podcast, null to
     *                  let {@link LocalFolderClassifier} guess from the folder's files
     * @return the feed as stored in the database
     * @throws IllegalArgumentException if the folder cannot be resolved
     */
    @WorkerThread
    @NonNull
    public static Feed addLocalFolder(@NonNull Context context, @NonNull Uri treeUri,
                                      @Nullable Boolean audiobook) {
        context.getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        DocumentFile documentFile = DocumentFile.fromTreeUri(context, treeUri);
        if (documentFile == null) {
            throw new IllegalArgumentException("Unable to retrieve document tree");
        }
        String title = documentFile.getName();
        if (title == null) {
            title = context.getString(R.string.local_folder);
        }
        boolean isAudiobook = audiobook != null ? audiobook : looksLikeAudiobook(context, treeUri);
        return addFeed(context, Feed.PREFIX_LOCAL_FOLDER + treeUri, title, isAudiobook);
    }

    private static boolean looksLikeAudiobook(@NonNull Context context, @NonNull Uri treeUri) {
        try {
            return LocalFolderClassifier.looksLikeAudiobook(context,
                    LocalFolderScanner.scan(context, treeUri).mediaFiles);
        } catch (IOException e) {
            // the first scan reports the problem on the feed; until then, treat it as a podcast
            Log.w(TAG, "unable to scan " + treeUri + ": " + e.getMessage());
            return false;
        }
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
