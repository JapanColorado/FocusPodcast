package dev.russell.focuspodcast.core.storage;

import dev.russell.focuspodcast.core.pref.Prefs;

public abstract class EpisodeCleanupAlgorithmFactory {
    public static EpisodeCleanupAlgorithm build() {
        if (!Prefs.isEnableAutodownload()) {
            return new APNullCleanupAlgorithm();
        }
        int cleanupValue = Prefs.getEpisodeCleanupValue();
        switch (cleanupValue) {
            case Prefs.EPISODE_CLEANUP_EXCEPT_FAVORITE:
                return new ExceptFavoriteCleanupAlgorithm();
            case Prefs.EPISODE_CLEANUP_QUEUE:
                return new APQueueCleanupAlgorithm();
            case Prefs.EPISODE_CLEANUP_NULL:
                return new APNullCleanupAlgorithm();
            default:
                return new APCleanupAlgorithm(cleanupValue);
        }
    }
}
