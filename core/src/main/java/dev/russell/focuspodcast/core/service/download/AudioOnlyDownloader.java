package dev.russell.focuspodcast.core.service.download;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import dev.russell.focuspodcast.core.R;
import dev.russell.focuspodcast.core.service.download.mp4.AudioOnlyMp4Plan;
import dev.russell.focuspodcast.core.service.download.mp4.Mp4TopLevelBox;
import dev.russell.focuspodcast.core.service.download.mp4.UnsupportedMp4Exception;
import dev.russell.focuspodcast.core.util.StorageUtils;
import dev.russell.focuspodcast.core.util.URIUtil;
import dev.russell.focuspodcast.model.download.DownloadError;
import dev.russell.focuspodcast.model.download.DownloadStatus;
import okhttp3.CacheControl;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Downloads only the audio track of a video episode: reads the MP4's sample tables with a few
 * small range requests, then fetches just the audio chunks and writes them as an audio-only MP4
 * ({@link AudioOnlyMp4Plan}). A 24 GB video podcast episode becomes a file of about 130 MB.
 *
 * <p>When the file cannot be cut (not MP4, fragmented, no range support, ...) the download fails
 * with {@link DownloadError#ERROR_AUDIO_ONLY_UNSUPPORTED} and the request's size set to the whole
 * file's size (or {@link DownloadStatus#SIZE_UNKNOWN}); {@link DownloadCompletionHandler} then
 * decides between downloading the whole file and asking the listener first.
 *
 * <p>Resuming: the header goes first and the fetches are written in order, so a file left by an
 * interrupted run is continued after its last complete fetch, provided its header still matches.
 */
public class AudioOnlyDownloader extends HttpDownloader {
    private static final String TAG = "AudioOnlyDownloader";
    /** Enough for the ftyp and the box headers that follow, and often the whole moov. */
    private static final int FIRST_REQUEST_LENGTH = 64 * 1024;
    /** A moov box larger than this is not a podcast episode's; refuse rather than run out of memory. */
    private static final long MAX_MOOV_SIZE = 64L * 1024 * 1024;
    /** Top-level boxes walked before giving up on finding the moov. */
    private static final int MAX_TOP_LEVEL_BOXES = 32;
    /**
     * Range requests in flight at once. Each one waits about a quarter second on a CDN, so the
     * speed comes from parallelism: 16 fetch the 128 MB of audio of a 24 GB episode in under a
     * minute (6 took over five).
     */
    private static final int PARALLEL_REQUESTS = 16;
    /** Fetches requested ahead of the one being written, at most. */
    private static final int WINDOW = 64;
    /** Bytes requested ahead of the one being written, at most; bounds the memory held by results. */
    private static final long WINDOW_BYTES = 16L * 1024 * 1024;
    /** Attempts per range request before the download fails. */
    private static final int ATTEMPTS = 3;
    /** Pause before the first retry of a range request; doubles for the next. */
    private static final long RETRY_DELAY_MS = 1000;

    /** The source URL after redirects; range requests go here directly. */
    private volatile String resolvedUrl;
    /** Set when the download has ended, so that range requests still in the pool give up. */
    private volatile boolean stopped;

    public AudioOnlyDownloader(@NonNull Context context, @NonNull DownloadRequest request) {
        super(context, request);
    }

    @Override
    protected void download() {
        resolvedUrl = request.getSource();
        request.setSoFar(0);
        request.setProgressPercent(0);
        RandomAccessFile out = null;
        ExecutorService pool = null;
        try {
            AudioOnlyMp4Plan plan = readPlan();
            if (plan == null) {
                return; // failed or cancelled, already reported
            }
            if (cancelled) {
                onCancelled();
                return;
            }
            if (!StorageUtils.storageAvailable()) {
                onFail(DownloadError.ERROR_DEVICE_NOT_FOUND, null);
                return;
            }
            request.setSize(plan.getOutputSize());
            if (plan.getOutputSize() > StorageUtils.getFreeSpaceAvailable()) {
                onFail(DownloadError.ERROR_NOT_ENOUGH_SPACE, null);
                return;
            }

            File destination = new File(request.getDestination());
            int first = resumeIndex(plan, destination);
            out = new RandomAccessFile(destination, "rw");
            long position = plan.outputPositionBefore(first);
            if (first == 0) {
                out.setLength(0);
                out.write(plan.getHeader());
            } else {
                Log.d(TAG, "resuming at fetch " + first + " of " + plan.getFetches().size());
            }
            out.setLength(position);
            out.seek(position);
            request.setSoFar(position);
            request.setStatusMsg(R.string.download_running);

            pool = Executors.newFixedThreadPool(PARALLEL_REQUESTS);
            List<AudioOnlyMp4Plan.Fetch> fetches = plan.getFetches();
            ArrayDeque<Future<byte[]>> pending = new ArrayDeque<>();
            int next = first;
            long pendingBytes = 0;
            for (int i = first; i < fetches.size() && !cancelled; i++) {
                while (next < fetches.size() && next < i + WINDOW
                        && (next == i || pendingBytes + fetches.get(next).length <= WINDOW_BYTES)) {
                    AudioOnlyMp4Plan.Fetch fetch = fetches.get(next++);
                    pendingBytes += fetch.length;
                    pending.add(pool.submit(() -> fetchRange(fetch.offset, fetch.length)));
                }
                byte[] fetched = await(pending.remove());
                pendingBytes -= fetched.length;
                byte[] kept = fetches.get(i).extract(fetched);
                out.write(kept);
                request.setSoFar(request.getSoFar() + kept.length);
                request.setProgressPercent((int) (100.0 * request.getSoFar() / request.getSize()));
            }
            if (cancelled) {
                onCancelled();
            } else if (out.length() != plan.getOutputSize()) {
                onFail(DownloadError.ERROR_IO_WRONG_SIZE, "Audio-only file is " + out.length()
                        + " bytes, expected " + plan.getOutputSize());
            } else {
                Log.d(TAG, "audio-only download complete: " + plan.getOutputSize() + " bytes in "
                        + fetches.size() + " range requests");
                onSuccess();
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Malformed download url " + request.getSource(), e);
            onFail(DownloadError.ERROR_MALFORMED_URL, e.getMessage());
        } catch (HttpStatusException e) {
            if (cancelled) {
                onCancelled();
            } else if (e.isTransient()) {
                // Throttled, a server error, or Range ignored by one edge: worth the automatic retry,
                // which resumes after the last complete fetch.
                onFail(DownloadError.ERROR_CONNECTION_ERROR, e.getMessage());
            } else {
                callOnFailByResponseCode(e.response);
            }
        } catch (IOException e) {
            if (cancelled) {
                onCancelled();
            } else {
                onIOException(e);
            }
        } finally {
            stopped = true;
            if (pool != null) {
                pool.shutdownNow();
            }
            IOUtils.closeQuietly(out);
        }
    }

    /**
     * Reads the source's ftyp and moov and plans the audio-only file. Returns null after
     * reporting the failure if the file cannot be cut or the server refused.
     */
    @Nullable
    private AudioOnlyMp4Plan readPlan() throws IOException {
        long fileSize = DownloadStatus.SIZE_UNKNOWN;
        Response response = execute(request.getSource(), 0, FIRST_REQUEST_LENGTH);
        if (response.code() == HttpURLConnection.HTTP_OK) {
            // Range ignored. Ask once more before concluding that the server never honours it:
            // behind a CDN one edge may answer differently from the next.
            response.close();
            response = execute(request.getSource(), 0, FIRST_REQUEST_LENGTH);
        }
        try {
            if (!response.isSuccessful()) {
                callOnFailByResponseCode(response);
                return null;
            }
            checkIfRedirect(response);
            resolvedUrl = response.request().url().toString();
            ResponseBody body = response.body();
            if (response.code() != HttpURLConnection.HTTP_PARTIAL) {
                // The server ignores Range: the audio cannot be fetched on its own.
                failUnsupported("No range support (HTTP " + response.code() + ")", body.contentLength());
                return null;
            }
            fileSize = totalSize(response.header("Content-Range"));
            if (fileSize <= 0) {
                failUnsupported("Unknown file size", DownloadStatus.SIZE_UNKNOWN);
                return null;
            }
            byte[] head = body.bytes();

            byte[] ftyp = null;
            byte[] moov = null;
            long offset = 0;
            for (int i = 0; moov == null && offset < fileSize; i++) {
                if (cancelled) {
                    onCancelled();
                    return null;
                }
                if (i == MAX_TOP_LEVEL_BOXES) {
                    failUnsupported("No moov box found", fileSize);
                    return null;
                }
                byte[] header = offset + Mp4TopLevelBox.MAX_HEADER_LENGTH <= head.length
                        ? Arrays.copyOfRange(head, (int) offset, (int) offset + Mp4TopLevelBox.MAX_HEADER_LENGTH)
                        : fetchRange(offset, (int) Math.min(Mp4TopLevelBox.MAX_HEADER_LENGTH, fileSize - offset));
                Mp4TopLevelBox box = Mp4TopLevelBox.parse(header, header.length, offset, fileSize);
                if (box.type.equals("moof")) {
                    throw new UnsupportedMp4Exception("Fragmented MP4");
                } else if (box.type.equals("ftyp") && offset == 0) {
                    ftyp = readBox(head, box);
                } else if (box.type.equals("moov")) {
                    if (box.size > MAX_MOOV_SIZE) {
                        throw new UnsupportedMp4Exception("moov box of " + box.size + " bytes");
                    }
                    moov = readBox(head, box);
                }
                offset = box.end();
            }
            if (moov == null) {
                failUnsupported("No moov box found", fileSize);
                return null;
            }
            AudioOnlyMp4Plan plan = AudioOnlyMp4Plan.create(ftyp, moov);
            if (plan.getRequiredSourceSize() > fileSize) {
                throw new UnsupportedMp4Exception("Audio chunks run past the end of the file");
            }
            return plan;
        } catch (UnsupportedMp4Exception e) {
            failUnsupported(e.getMessage(), fileSize);
            return null;
        } finally {
            response.close();
        }
    }

    /** The box's bytes, from the first response if it holds them, else with one more request. */
    private byte[] readBox(byte[] head, Mp4TopLevelBox box) throws IOException {
        if (box.end() <= head.length) {
            return Arrays.copyOfRange(head, (int) box.offset, (int) box.end());
        }
        return fetchRange(box.offset, (int) box.size);
    }

    private void failUnsupported(String reason, long fileSize) {
        Log.w(TAG, "Cannot download only the audio of " + request.getSource() + ": " + reason);
        request.setSize(fileSize > 0 ? fileSize : DownloadStatus.SIZE_UNKNOWN);
        onFail(DownloadError.ERROR_AUDIO_ONLY_UNSUPPORTED, reason);
    }

    /**
     * Where to continue writing {@code destination}: after its last complete fetch if it holds
     * the beginning of this same output, else from the start.
     */
    private static int resumeIndex(AudioOnlyMp4Plan plan, File destination) throws IOException {
        if (!destination.exists() || destination.length() < plan.getHeaderLength()) {
            return 0;
        }
        byte[] existing = new byte[plan.getHeaderLength()];
        try (RandomAccessFile file = new RandomAccessFile(destination, "r")) {
            file.readFully(existing);
        }
        return Arrays.equals(existing, plan.getHeader()) ? plan.resumeIndex(destination.length()) : 0;
    }

    /**
     * Fetches {@code length} bytes from {@code offset}, retrying a failed request. A retry goes
     * through the original URL again, because the redirect target may be a signed URL that has
     * expired since.
     */
    private byte[] fetchRange(long offset, int length) throws IOException {
        IOException failure = null;
        for (int attempt = 0; attempt < ATTEMPTS && !cancelled && !stopped; attempt++) {
            if (attempt > 0) {
                backOff(attempt);
            }
            String url = attempt == 0 ? resolvedUrl : request.getSource();
            try (Response response = execute(url, offset, length)) {
                if (response.code() != HttpURLConnection.HTTP_PARTIAL) {
                    failure = new HttpStatusException(response);
                    continue;
                }
                byte[] bytes = response.body().bytes();
                if (bytes.length != length) {
                    failure = new IOException("Range request at " + offset + " returned " + bytes.length
                            + " of " + length + " bytes");
                    continue;
                }
                resolvedUrl = response.request().url().toString();
                return bytes;
            } catch (IOException e) {
                failure = e;
            }
        }
        throw failure != null ? failure : new IOException("Download cancelled");
    }

    /** Waits a little longer before each retry, so a throttling server gets some air. */
    private void backOff(int attempt) throws IOException {
        try {
            Thread.sleep(RETRY_DELAY_MS * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private Response execute(String url, long offset, int length) throws IOException {
        URI uri = URIUtil.getURIFromRequestUrl(url);
        Request.Builder httpReq = new Request.Builder().url(uri.toURL())
                .tag(request)
                // no transparent gzip: the byte offsets are into the file itself
                .addHeader("Accept-Encoding", "identity")
                .addHeader("Range", "bytes=" + offset + "-" + (offset + length - 1))
                .cacheControl(new CacheControl.Builder().noCache().build());
        return newCall(httpReq);
    }

    /** Reads the total from a {@code Content-Range: bytes a-b/total} header; -1 if unknown. */
    static long totalSize(@Nullable String contentRange) {
        if (contentRange == null) {
            return -1;
        }
        int slash = contentRange.lastIndexOf('/');
        if (slash < 0) {
            return -1;
        }
        try {
            return Long.parseLong(contentRange.substring(slash + 1).trim());
        } catch (NumberFormatException e) {
            return -1; // "*": the server does not know the size
        }
    }

    private static byte[] await(Future<byte[]> future) throws IOException {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException(cause);
        }
    }

    /** A range request answered with something other than 206 Partial Content. */
    private static final class HttpStatusException extends IOException {
        final transient Response response;

        HttpStatusException(Response response) {
            super("HTTP " + response.code());
            this.response = response;
        }

        /** 200 (Range ignored this once), 408, 429 and server errors may pass on a later attempt. */
        boolean isTransient() {
            int code = response.code();
            return code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_CLIENT_TIMEOUT
                    || code == 429 || code >= HttpURLConnection.HTTP_INTERNAL_ERROR;
        }
    }
}
