package allen.town.podcast.core.pref

import allen.town.podcast.core.R
import allen.town.podcast.core.pref.PrefsStore.TAG
import allen.town.podcast.core.pref.PrefsStore.context
import allen.town.podcast.core.pref.PrefsStore.getString
import allen.town.podcast.core.pref.PrefsStore.prefs
import allen.town.podcast.core.storage.APCleanupAlgorithm
import allen.town.podcast.core.storage.APNullCleanupAlgorithm
import allen.town.podcast.core.storage.APQueueCleanupAlgorithm
import allen.town.podcast.core.storage.EpisodeCleanupAlgorithm
import allen.town.podcast.core.storage.ExceptFavoriteCleanupAlgorithm
import android.util.Log
import androidx.annotation.VisibleForTesting
import java.io.File

/**
 * Owns downloading and on-disk storage: whether downloads are queued and where, how many run in
 * parallel, the episode cache size and cleanup algorithm, the auto-download switches, the
 * stream-over-download choice, and the data folder the app writes episodes into. It reads and
 * writes through [PrefsStore]; [Prefs] exposes every member of it unchanged.
 */
internal object DownloadPrefs {

    private const val PREF_ENQUEUE_DOWNLOADED = "pref_add_to_playlist_when_download"
    private const val PREF_AUTO_DELETE = "pref_auto_delete"
    private const val PREF_DATA_FOLDER = "prefDataFolder"
    private const val EPISODE_CACHE_SIZE_UNLIMITED = -1

    fun enqueueDownloadedEpisodes(): Boolean {
        return prefs.getBoolean(PREF_ENQUEUE_DOWNLOADED, true)
    }

    @VisibleForTesting
    fun setEnqueueDownloadedEpisodes(enqueueDownloadedEpisodes: Boolean) {
        prefs.edit()
            .putBoolean(PREF_ENQUEUE_DOWNLOADED, enqueueDownloadedEpisodes)
            .apply()
    }

    // should never happen but just in case
    var enqueueLocation: Prefs.EnqueueLocation
        get() {
            val valStr = getString(Prefs.PREF_ENQUEUE_LOCATION, Prefs.EnqueueLocation.BACK.name)
            return try {
                Prefs.EnqueueLocation.valueOf(valStr)
            } catch (t: IllegalArgumentException) {
                // should never happen but just in case
                Log.e(TAG, "getEnqueueLocation: invalid value '$valStr' Use default.", t)
                Prefs.EnqueueLocation.BACK
            }
        }
        set(location) {
            prefs.edit()
                .putString(Prefs.PREF_ENQUEUE_LOCATION, location.name)
                .apply()
        }

    val isAutoDelete: Boolean
        get() = prefs.getBoolean(PREF_AUTO_DELETE, false)

    fun shouldDeleteRemoveFromQueue(): Boolean {
        return prefs.getBoolean(Prefs.PREF_DELETE_REMOVES_FROM_QUEUE, false)
    }

    val parallelDownloads: Int
        get() = getString(Prefs.PREF_PARALLEL_DOWNLOADS, "4").toInt()

    val episodeCacheSizeUnlimited: Int
        get() = context.resources.getInteger(R.integer.episode_cache_size_unlimited)

    /**
     * Returns the capacity of the episode cache. This method will return the
     * negative integer EPISODE_CACHE_SIZE_UNLIMITED if the cache size is set to
     * 'unlimited'.
     */
    val episodeCacheSize: Int
        get() = readEpisodeCacheSizeInternal(getString(Prefs.PREF_EPISODE_CACHE_SIZE, "20"))

    private fun readEpisodeCacheSizeInternal(valueFromPrefs: String): Int {
        return if (valueFromPrefs == context.getString(R.string.pref_episode_cache_unlimited)) {
            EPISODE_CACHE_SIZE_UNLIMITED
        } else {
            valueFromPrefs.toInt()
        }
    }

    @set:VisibleForTesting
    var isEnableAutodownload: Boolean
        get() = prefs.getBoolean(Prefs.PREF_ENABLE_AUTODL, false)
        set(enabled) {
            prefs.edit().putBoolean(Prefs.PREF_ENABLE_AUTODL, enabled).apply()
        }

    val isEnableAutodownloadOnBattery: Boolean
        get() = prefs.getBoolean(Prefs.PREF_ENABLE_AUTODL_ON_BATTERY, true)

    var isStreamOverDownload: Boolean
        get() = true
        set(stream) {
            prefs.edit().putBoolean(Prefs.PREF_STREAM_OVER_DOWNLOAD, stream).apply()
        }

    val episodeCleanupAlgorithm: EpisodeCleanupAlgorithm
        get() {
            if (!isEnableAutodownload) {
                return APNullCleanupAlgorithm()
            }
            val cleanupValue = episodeCleanupValue
            return if (cleanupValue == Prefs.EPISODE_CLEANUP_EXCEPT_FAVORITE) {
                ExceptFavoriteCleanupAlgorithm()
            } else if (cleanupValue == Prefs.EPISODE_CLEANUP_QUEUE) {
                APQueueCleanupAlgorithm()
            } else if (cleanupValue == Prefs.EPISODE_CLEANUP_NULL) {
                APNullCleanupAlgorithm()
            } else {
                APCleanupAlgorithm(cleanupValue)
            }
        }

    var episodeCleanupValue: Int
        get() = getString(Prefs.PREF_EPISODE_CLEANUP, "" + Prefs.EPISODE_CLEANUP_NULL)
            .toInt()
        set(episodeCleanupValue) {
            prefs.edit()
                .putString(Prefs.PREF_EPISODE_CLEANUP, Integer.toString(episodeCleanupValue))
                .apply()
        }

    /**
     * Returns the folder where the app stores all of its data. This method returns the standard
     * data folder if the user has not set one.
     * @param type The name of the folder inside the data folder. May be null when accessing the root of the data folder.
     * @return The requested data folder, or null if the folder could not be created.
     */
    fun getDataFolder(type: String?): File? {
        var dataFolder = getTypeDir(prefs.getString(PREF_DATA_FOLDER, null), type)
        if (dataFolder == null || !dataFolder.canWrite()) {
            dataFolder = context.getExternalFilesDir(type)
        }
        if (dataFolder == null || !dataFolder.canWrite()) {
            dataFolder = getTypeDir(context.filesDir.absolutePath, type)
        }
        return dataFolder
    }

    private fun getTypeDir(baseDirPath: String?, type: String?): File? {
        if (baseDirPath == null) {
            return null
        }
        val baseDir = File(baseDirPath)
        val typeDir = if (type == null) baseDir else File(baseDir, type)
        if (!typeDir.exists()) {
            if (!baseDir.canWrite()) {
                Log.e(TAG, "Base dir is not writable " + baseDir.absolutePath)
                return null
            }
            if (!typeDir.mkdirs()) {
                Log.e(TAG, "Could not create type dir " + typeDir.absolutePath)
                return null
            }
        }
        return typeDir
    }

    fun setDataFolder(dir: String) {
        Log.d(TAG, "set storage folder $dir")
        prefs.edit()
            .putString(PREF_DATA_FOLDER, dir)
            .apply()
    }
}
