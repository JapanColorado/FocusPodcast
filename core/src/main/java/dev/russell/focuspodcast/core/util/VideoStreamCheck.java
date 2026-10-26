package dev.russell.focuspodcast.core.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import dev.russell.focuspodcast.core.feed.util.PlayableFeedPreferences;
import dev.russell.focuspodcast.core.feed.util.PlaybackSpeedUtils;
import dev.russell.focuspodcast.core.pref.Prefs;
import dev.russell.focuspodcast.core.service.download.PodcastHttpClient;
import dev.russell.focuspodcast.model.feed.FeedMedia;
import dev.russell.focuspodcast.model.playback.MediaType;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Whether watching the video of an audio-only download is likely to work: compares the bitrate
 * the stream needs at the speed it would be watched at with Android's estimate of the
 * connection's download speed. A 4K podcast episode can need 60 Mbit/s, which most mobile and
 * weak Wi-Fi connections cannot deliver, and then the stream never starts.
 */
public final class VideoStreamCheck {
    private static final String TAG = "VideoStreamCheck";

    private VideoStreamCheck() {
    }

    /**
     * The bandwidth in kbit/s needed to play {@code fileSize} bytes lasting {@code durationMs}
     * at {@code speed}, or 0 if either is unknown.
     */
    public static long requiredKbps(long fileSize, long durationMs, float speed) {
        if (fileSize <= 0 || durationMs <= 0) {
            return 0;
        }
        return (long) (fileSize * 8.0 / durationMs * Math.max(1f, speed));
    }

    /** True when both values are known and the stream needs more than the connection offers. */
    public static boolean isLikelyTooSlow(long requiredKbps, long availableKbps) {
        return requiredKbps > 0 && availableKbps > 0 && requiredKbps > availableKbps;
    }

    /** Android's estimate of the active network's download speed in kbit/s, 0 if unknown. */
    public static long estimatedDownstreamKbps(@NonNull Context context) {
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        Network network = connectivity != null ? connectivity.getActiveNetwork() : null;
        NetworkCapabilities capabilities = network != null ? connectivity.getNetworkCapabilities(network) : null;
        return capabilities != null ? Math.max(0, capabilities.getLinkDownstreamBandwidthKbps()) : 0;
    }

    /** The speed the episode would play at in the video player. */
    public static float watchingSpeed(@NonNull FeedMedia media) {
        return PlaybackSpeedUtils.resolveSpeed(PlayableFeedPreferences.of(media),
                Prefs.getPlaybackSpeed(MediaType.VIDEO));
    }

    /**
     * The size of the episode's video file on the server, from a one-byte range request; 0 if
     * it cannot be told. The size stored with the episode is that of the downloaded audio.
     */
    @WorkerThread
    public static long fetchStreamSize(@NonNull FeedMedia media) {
        Request request = new Request.Builder()
                .url(media.getStreamUrl())
                .addHeader("Range", "bytes=0-0")
                .addHeader("Accept-Encoding", "identity")
                .build();
        try (Response response = PodcastHttpClient.getHttpClient().newCall(request).execute()) {
            String contentRange = response.header("Content-Range");
            if (response.code() == 206 && contentRange != null && contentRange.contains("/")) {
                return Long.parseLong(contentRange.substring(contentRange.lastIndexOf('/') + 1).trim());
            }
            return response.code() == 200 ? Math.max(0, response.body().contentLength()) : 0;
        } catch (IOException | IllegalArgumentException e) { // incl. a malformed URL or Content-Range
            Log.w(TAG, "Could not read the size of " + media.getStreamUrl(), e);
            return 0;
        }
    }
}
