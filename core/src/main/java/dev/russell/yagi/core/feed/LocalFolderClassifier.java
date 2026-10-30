package dev.russell.yagi.core.feed;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Guesses whether a newly added local folder holds an audiobook or a podcast, so that one "Add
 * from device" entry serves both. The guess only sets the feed's initial audiobook flag; the user
 * changes it with the Audiobook switch in the feed settings.
 *
 * <p>In order: any .m4b file means an audiobook; a genre tag naming podcasts or audiobooks decides;
 * a folder with a single file is an audiobook; files that all carry the same album tag and track
 * numbers are an audiobook. Everything else is a podcast. Tags are read from a sample of
 * {@link #MAX_SAMPLED_FILES} files spread across the folder, since reading every file of a large
 * folder would delay the import noticeably.</p>
 */
public final class LocalFolderClassifier {
    private static final String TAG = "LocalFolderClassifier";
    static final int MAX_SAMPLED_FILES = 8;

    private LocalFolderClassifier() {
    }

    /** The tags of one file that the guess looks at. */
    static final class Tags {
        @Nullable
        final String album;
        @Nullable
        final String genre;
        final int trackNumber;

        Tags(@Nullable String album, @Nullable String genre, int trackNumber) {
            this.album = album;
            this.genre = genre;
            this.trackNumber = trackNumber;
        }
    }

    @WorkerThread
    public static boolean looksLikeAudiobook(@NonNull Context context,
                                             @NonNull List<LocalFolderScanner.LocalFile> mediaFiles) {
        List<String> names = new ArrayList<>();
        for (LocalFolderScanner.LocalFile file : mediaFiles) {
            names.add(file.name);
        }
        List<Tags> tags = new ArrayList<>();
        for (LocalFolderScanner.LocalFile file : sample(mediaFiles)) {
            tags.add(readTags(context, file));
        }
        return looksLikeAudiobook(names, tags);
    }

    /**
     * @param names the names of every media file in the folder
     * @param tags  the tags of a sample of those files
     */
    static boolean looksLikeAudiobook(@NonNull List<String> names, @NonNull List<Tags> tags) {
        if (names.isEmpty()) {
            return false;
        }
        for (String name : names) {
            if (name.toLowerCase(Locale.ROOT).endsWith(".m4b")) {
                return true;
            }
        }
        for (Tags tag : tags) {
            String genre = tag.genre == null ? "" : tag.genre.toLowerCase(Locale.ROOT);
            if (genre.contains("podcast")) {
                return false;
            }
            if (genre.contains("audiobook") || genre.contains("audio book") || genre.contains("hörbuch")
                    || genre.contains("spoken")) {
                return true;
            }
        }
        if (names.size() == 1) {
            return true;
        }
        return hasOneAlbumWithTrackNumbers(tags);
    }

    private static boolean hasOneAlbumWithTrackNumbers(List<Tags> tags) {
        if (tags.size() < 2) {
            return false;
        }
        String album = null;
        for (Tags tag : tags) {
            if (tag.album == null || tag.album.trim().isEmpty() || tag.trackNumber <= 0) {
                return false;
            }
            if (album == null) {
                album = tag.album.trim();
            } else if (!album.equalsIgnoreCase(tag.album.trim())) {
                return false;
            }
        }
        return true;
    }

    /** Up to {@link #MAX_SAMPLED_FILES} files, evenly spaced from first to last. */
    static <T> List<T> sample(List<T> files) {
        if (files.size() <= MAX_SAMPLED_FILES) {
            return files;
        }
        List<T> sample = new ArrayList<>(MAX_SAMPLED_FILES);
        for (int i = 0; i < MAX_SAMPLED_FILES; i++) {
            sample.add(files.get((int) ((long) i * (files.size() - 1) / (MAX_SAMPLED_FILES - 1))));
        }
        return sample;
    }

    private static Tags readTags(Context context, LocalFolderScanner.LocalFile file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, file.uri);
            return new Tags(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE),
                    LocalFeedUpdater.parseLeadingNumber(
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)));
        } catch (RuntimeException e) {
            // unreadable file: it simply does not vote
            Log.w(TAG, "unable to read tags of " + file.uri + ": " + e.getMessage());
            return new Tags(null, null, 0);
        } finally {
            try {
                retriever.release();
            } catch (IOException e) {
                Log.w(TAG, "unable to release MediaMetadataRetriever: " + e.getMessage());
            }
        }
    }
}
