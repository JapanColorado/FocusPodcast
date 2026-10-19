package dev.russell.focuspodcast.core.util;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import dev.russell.focuspodcast.core.service.download.PodcastHttpClient;
import dev.russell.focuspodcast.core.storage.DBReader;
import dev.russell.focuspodcast.core.storage.DBWriter;
import dev.russell.focuspodcast.model.feed.FeedItem;
import dev.russell.focuspodcast.model.transcript.CachedTranscript;
import dev.russell.focuspodcast.model.transcript.Transcript;
import dev.russell.focuspodcast.parser.feed.transcript.TranscriptParser;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Downloads, caches and reads episode transcripts ({@code <podcast:transcript>}). The download is
 * kept as received in the database, together with its plain text for the episode search, and
 * parsed again whenever it is shown. A copy downloaded from an address the feed no longer gives
 * is stale: it is replaced on the next load, but still shown if the new address cannot be reached.
 */
public final class TranscriptUtils {

    private static final String TAG = "TranscriptUtils";
    /** Transcripts larger than this are refused; even a three-hour VTT is a few hundred kB. */
    private static final long MAX_TRANSCRIPT_BYTES = 8L * 1024 * 1024;

    private TranscriptUtils() {
    }

    /**
     * The transcript of {@code item}: the cached copy when it is current, otherwise a fresh
     * download, which is then cached. Blocks on the network; call it off the main thread.
     *
     * @throws IOException when nothing is cached and the download fails
     */
    @WorkerThread
    @NonNull
    public static Transcript load(@NonNull FeedItem item) throws IOException {
        String url = item.getTranscriptUrl();
        CachedTranscript cached = DBReader.loadCachedTranscript(item.getId());
        if (cached != null && (url == null || cached.isFrom(url))) {
            return TranscriptParser.parse(cached.getRaw(), cached.getType());
        }
        if (url == null || url.isEmpty()) {
            return Transcript.empty();
        }
        try {
            return download(item, url);
        } catch (IOException e) {
            if (cached != null) {
                Log.w(TAG, "could not refresh the transcript, showing the cached one: " + e.getMessage());
                return TranscriptParser.parse(cached.getRaw(), cached.getType());
            }
            throw e;
        }
    }

    /**
     * Downloads and caches the transcript of {@code item} unless a current copy is cached. Never
     * throws: called after an episode download, which must not fail because of its transcript.
     */
    @WorkerThread
    public static void cacheQuietly(@Nullable FeedItem item) {
        if (item == null || !item.hasTranscript() || item.getId() == 0) {
            return;
        }
        String url = item.getTranscriptUrl();
        try {
            CachedTranscript cached = DBReader.loadCachedTranscript(item.getId());
            if (cached != null && cached.isFrom(url)) {
                return;
            }
            download(item, url);
        } catch (IOException | RuntimeException e) {
            // Log and continue: the transcript is fetched again when it is opened.
            Log.w(TAG, "could not cache the transcript of " + item.getTitle() + ": " + e.getMessage());
        }
    }

    @NonNull
    private static Transcript download(@NonNull FeedItem item, @NonNull String url) throws IOException {
        String raw = fetch(url);
        Transcript transcript = TranscriptParser.parse(raw, item.getTranscriptType());
        DBWriter.setTranscript(item.getId(), url, item.getTranscriptType(), raw, transcript.toPlainText());
        return transcript;
    }

    @NonNull
    private static String fetch(@NonNull String url) throws IOException {
        Request request;
        try {
            request = new Request.Builder().url(url).build();
        } catch (IllegalArgumentException e) {
            throw new IOException("invalid transcript URL: " + url, e);
        }
        try (Response response = PodcastHttpClient.getHttpClient().newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("transcript download failed: HTTP " + response.code());
            }
            if (body.contentLength() > MAX_TRANSCRIPT_BYTES) {
                throw new IOException("transcript too large: " + body.contentLength() + " bytes");
            }
            String text = body.string();
            if (text.length() > MAX_TRANSCRIPT_BYTES) {
                throw new IOException("transcript too large: " + text.length() + " characters");
            }
            return text;
        }
    }
}
