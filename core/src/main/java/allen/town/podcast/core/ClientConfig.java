package allen.town.podcast.core;

import android.content.Context;

import java.io.File;

import allen.town.podcast.core.pref.PlaybackPreferences;
import allen.town.podcast.core.pref.Prefs;
import allen.town.podcast.core.pref.SleepTimerPreferences;
import allen.town.podcast.core.pref.UsageStatistics;
import allen.town.podcast.core.service.download.PodcastHttpClient;
import allen.town.podcast.core.sync.SynchronizationCredentials;
import allen.town.podcast.core.sync.SynchronizationSettings;
import allen.town.podcast.core.util.NetworkUtils;
import allen.town.podcast.core.util.ui.NotificationUtils;
import allen.town.podcast.net.ssl.SslProviderInstaller;
import allen.town.podcast.storage.db.Db;

/**
 * One-time, process-wide initialisation of the core module plus the one callback interface
 * that core needs the app module to supply.
 *
 * <p>{@link #initialize(Context, DownloadServiceCallbacks)} must be called exactly once, from
 * {@code Application.onCreate}. Every other entry point in the process (services, broadcast
 * receivers, {@code WorkManager} workers) is created after {@code Application.onCreate} has
 * returned, so those only assert that initialisation happened, via
 * {@link #ensureInitialized(Context)}.</p>
 */
public class ClientConfig {

    /**
     * Should be used when setting the User-Agent header for HTTP requests. Built from core's own
     * {@code VERSION_NAME} build config field so that core does not have to ask app for it.
     */
    public static final String USER_AGENT = "FocusPodcast_" + BuildConfig.VERSION_NAME;

    private static DownloadServiceCallbacks downloadServiceCallbacks;

    private static boolean initialized = false;

    private ClientConfig() {
    }

    /**
     * Initialises every core singleton and records the app-supplied callbacks.
     * Must be called from {@code Application.onCreate}. Repeat calls are ignored.
     */
    public static synchronized void initialize(Context context, DownloadServiceCallbacks callbacks) {
        if (callbacks == null) {
            throw new IllegalArgumentException("downloadServiceCallbacks must not be null");
        }
        if (initialized) {
            return;
        }
        Context appContext = context.getApplicationContext();
        downloadServiceCallbacks = callbacks;
        Db.init(appContext);
        Prefs.init(appContext);
        UsageStatistics.init(appContext);
        PlaybackPreferences.init(appContext);
        SslProviderInstaller.install(appContext);
        NetworkUtils.init(appContext);
        PodcastHttpClient.setCacheDirectory(new File(appContext.getCacheDir(), "okhttp"));
        PodcastHttpClient.setProxyConfig(Prefs.getProxyConfig());
        SleepTimerPreferences.init(appContext);
        SynchronizationCredentials.init(appContext);
        SynchronizationSettings.init(appContext);
        NotificationUtils.createChannels(appContext);
        initialized = true;
    }

    /**
     * Cheap guard for entry points (receivers, workers, services) that can only run after
     * {@code Application.onCreate}. It never initialises anything itself: if initialisation is
     * missing that is a wiring bug in the app module, and failing loudly here is better than
     * half-initialising core from a background thread.
     *
     * @param context the caller's context, kept for symmetry with {@link #initialize} and for
     *                future diagnostics; not stored.
     * @throws IllegalStateException if {@link #initialize(Context, DownloadServiceCallbacks)}
     *                               has not run yet.
     */
    public static synchronized void ensureInitialized(Context context) {
        if (!initialized) {
            throw new IllegalStateException("ClientConfig.initialize(Context, DownloadServiceCallbacks)"
                    + " must be called from Application.onCreate before any core component is used");
        }
    }


    /**
     * @throws IllegalStateException if the app module has not initialised core yet.
     */
    public static DownloadServiceCallbacks getDownloadServiceCallbacks() {
        DownloadServiceCallbacks callbacks = downloadServiceCallbacks;
        if (callbacks == null) {
            throw new IllegalStateException("ClientConfig.initialize(Context, DownloadServiceCallbacks)"
                    + " must be called from Application.onCreate before any core component is used");
        }
        return callbacks;
    }
}
