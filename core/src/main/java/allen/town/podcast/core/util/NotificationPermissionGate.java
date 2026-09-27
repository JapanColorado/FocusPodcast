package allen.town.podcast.core.util;

/**
 * Decides when to explain and then request the notification permission (Android 13+). The app
 * never asks at startup: it waits until an episode download is actually running, the first moment
 * a notification (download progress, then the new-episode and download-result alerts) would be
 * useful, and it explains that once. Media playback notifications are exempt from the permission,
 * so playback is deliberately not a trigger.
 */
public final class NotificationPermissionGate {

    /** Android 13 (TIRAMISU), the first release with a runtime POST_NOTIFICATIONS permission. */
    public static final int FIRST_RUNTIME_PERMISSION_SDK = 33;

    private NotificationPermissionGate() {
    }

    /**
     * @param sdkInt                 {@code Build.VERSION.SDK_INT}
     * @param notificationsEnabled   whether the app may already post notifications
     * @param rationaleShown         whether the explanation was shown before (answered either way)
     * @param episodeDownloadRunning whether at least one episode (not just a feed) is downloading
     */
    public static boolean shouldExplain(int sdkInt, boolean notificationsEnabled,
                                        boolean rationaleShown, boolean episodeDownloadRunning) {
        return sdkInt >= FIRST_RUNTIME_PERMISSION_SDK
                && !notificationsEnabled
                && !rationaleShown
                && episodeDownloadRunning;
    }
}
