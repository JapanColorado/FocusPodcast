package dev.russell.yagi.fragment.storage

import dev.russell.yagi.core.pref.PlaybackPreferences
import dev.russell.yagi.core.pref.Prefs
import dev.russell.yagi.core.storage.DBReader
import dev.russell.yagi.core.storage.StorageUsage
import dev.russell.yagi.core.util.StorageUtils
import android.util.Log

/**
 * What the storage overview shows: the downloads grouped by podcast, and the free and total space
 * of the volume holding the data folder (0 when it cannot be read), and the title of the episode
 * loaded in the player when its download is listed (it is never deleted from here).
 */
class StorageSnapshot(
    val overview: StorageUsage.Overview,
    val freeBytes: Long,
    val totalBytes: Long,
    val playerEpisodeTitle: String? = null,
) {
    companion object {
        private const val TAG = "StorageSnapshot"

        /** Reads the database, measures every downloaded file and asks the volume. Off the main thread. */
        @JvmStatic
        fun load(): StorageSnapshot {
            val entries = StorageUsage.measure(DBReader.getDownloadedMediaWithFeed())
            val playerMediaId = PlaybackPreferences.getCurrentlyPlayingFeedMediaId()
            val overview = StorageUsage.aggregate(
                entries, Prefs.shouldFavoriteKeepEpisode(), playerMediaId
            )
            val playerEpisodeTitle = if (overview.feeds.any { it.holdsPlayerEpisode }) {
                DBReader.getFeedMedia(playerMediaId)?.item?.title
            } else {
                null
            }
            var free = 0L
            var total = 0L
            val folder = Prefs.getDataFolder(null)
            if (folder != null) {
                try {
                    free = StorageUtils.getFreeSpaceAvailable(folder.absolutePath)
                    total = StorageUtils.getTotalSpaceAvailable(folder.absolutePath)
                } catch (e: IllegalArgumentException) {
                    // StatFs throws for a path it cannot stat (e.g. an unmounted SD card)
                    Log.w(TAG, "Could not stat " + folder.absolutePath, e)
                }
            }
            return StorageSnapshot(overview, free, total, playerEpisodeTitle)
        }
    }
}
