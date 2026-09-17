package allen.town.podcast.core.pref

import allen.town.podcast.core.pref.PrefsStore.TAG
import allen.town.podcast.core.pref.PrefsStore.getString
import allen.town.podcast.core.pref.PrefsStore.prefs
import allen.town.podcast.model.feed.SortOrder
import allen.town.podcast.model.playback.MediaType
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.VisibleForTesting
import org.json.JSONArray
import org.json.JSONException
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Arrays
import java.util.Locale

/**
 * Owns how an episode plays: the audio and video speeds (and the speed chooser list), skip
 * silence, fast-forward / rewind step sizes, the headset, bluetooth, phone-call and audio-focus
 * behaviour, the hardware media button mapping, the "keep episode" rules, and the queue's
 * locked / keep-sorted state. It reads and writes through [PrefsStore]; [Prefs] exposes every
 * member of it unchanged.
 */
internal object PlaybackPrefs {

    private const val PREF_UNPAUSE_ON_BLUETOOTH_RECONNECT = "pref_play_when_bluetooth_reconnect"
    private const val PREF_FAVORITE_KEEPS_EPISODE = "pref_keeps_favorite_episodes"
    private const val PREF_PLAYBACK_SPEED_ARRAY = "pref_playback_speed_list"
    private const val PREF_RESUME_AFTER_CALL = "pref_replay_after_call"
    private const val PREF_TIME_RESPECTS_SPEED = "pref_respects_playbacktime_for_speed"
    private const val PREF_AUDIO_LOUDNESS = "pref_audio_loudness"
    private const val PREF_PLAYBACK_SPEED = "pref_globa_playback_speed"
    private const val PREF_VIDEO_PLAYBACK_SPEED = "pref_global_video_playback_speed"
    private const val PREF_FAST_FORWARD_SECS = "pref_global_fast_forward_secs"
    private const val PREF_REWIND_SECS = "pref_global_rewind_secs"
    private const val PREF_QUEUE_LOCKED = "pref_queue_Locked"
    private const val PREF_STEREO_TO_MONO = "pref_stereo_to_mono"

    val isPauseOnHeadsetDisconnect: Boolean
        get() = prefs.getBoolean(Prefs.PREF_PAUSE_ON_HEADSET_DISCONNECT, true)

    val isUnpauseOnHeadsetReconnect: Boolean
        get() = prefs.getBoolean(Prefs.PREF_UNPAUSE_ON_HEADSET_RECONNECT, true)

    val isUnpauseOnBluetoothReconnect: Boolean
        get() = prefs.getBoolean(PREF_UNPAUSE_ON_BLUETOOTH_RECONNECT, false)

    val hardwareForwardButton: Int
        get() = getString(
            Prefs.PREF_HARDWARE_FORWARD_BUTTON,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD.toString()
        ).toInt()

    val hardwarePreviousButton: Int
        get() = getString(
            Prefs.PREF_HARDWARE_PREVIOUS_BUTTON,
            KeyEvent.KEYCODE_MEDIA_REWIND.toString()
        ).toInt()

    /**
     * Set to true to enable Continuous Playback
     */
    @set:VisibleForTesting
    var isFollowQueue: Boolean
        get() = prefs.getBoolean(Prefs.PREF_FOLLOW_QUEUE, true)
        set(value) {
            prefs.edit().putBoolean(Prefs.PREF_FOLLOW_QUEUE, value).apply()
        }

    fun shouldSkipKeepEpisode(): Boolean {
        return prefs.getBoolean(Prefs.PREF_SKIP_KEEPS_EPISODE, true)
    }

    fun shouldFavoriteKeepEpisode(): Boolean {
        return prefs.getBoolean(PREF_FAVORITE_KEEPS_EPISODE, true)
    }

    val smartMarkAsPlayedSecs: Int
        get() = getString(Prefs.PREF_SMART_MARK_AS_PLAYED_SECS, "30").toInt()

    fun getPlaybackSpeed(mediaType: MediaType?): Float {
        return if (mediaType == MediaType.VIDEO) {
            videoPlaybackSpeed
        } else {
            audioPlaybackSpeed
        }
    }

    /**
     * Returns the global audio playback speed.
     * @return
     */
    private val audioPlaybackSpeed: Float
        get() = try {
            getString(PREF_PLAYBACK_SPEED, "1.00").toFloat()
        } catch (e: NumberFormatException) {
            Log.e(TAG, Log.getStackTraceString(e))
            setPlaybackSpeed(1.0f)
            1.0f
        }

    /**
     * Returns the global video playback speed.
     * @return
     */
    var videoPlaybackSpeed: Float
        get() = try {
            getString(PREF_VIDEO_PLAYBACK_SPEED, "1.00").toFloat()
        } catch (e: NumberFormatException) {
            Log.e(TAG, Log.getStackTraceString(e))
            videoPlaybackSpeed = 1.0f
            1.0f
        }
        set(speed) {
            prefs.edit()
                .putString(PREF_VIDEO_PLAYBACK_SPEED, speed.toString())
                .apply()
        }

    fun setPlaybackSpeed(speed: Float) {
        prefs.edit()
            .putString(PREF_PLAYBACK_SPEED, speed.toString())
            .apply()
    }

    var isSkipSilence: Boolean
        get() = prefs.getBoolean(Prefs.PREF_PLAYBACK_SKIP_SILENCE, false)
        set(skipSilence) {
            prefs.edit()
                .putBoolean(Prefs.PREF_PLAYBACK_SKIP_SILENCE, skipSilence)
                .apply()
        }

    var playbackSpeedArray: List<Float>
        get() = readPlaybackSpeedArray(prefs.getString(PREF_PLAYBACK_SPEED_ARRAY, null))
        set(speeds) {
            val format = DecimalFormatSymbols(Locale.US)
            format.decimalSeparator = '.'
            val speedFormat = DecimalFormat("0.0", format)
            val jsonArray = JSONArray()
            for (speed in speeds) {
                jsonArray.put(speedFormat.format(speed.toDouble()))
            }
            prefs.edit()
                .putString(PREF_PLAYBACK_SPEED_ARRAY, jsonArray.toString())
                .apply()
        }

    private fun readPlaybackSpeedArray(valueFromPrefs: String?): List<Float> {
        if (valueFromPrefs != null) {
            try {
                val jsonArray = JSONArray(valueFromPrefs)
                val selectedSpeeds: MutableList<Float> = ArrayList()
                for (i in 0 until jsonArray.length()) {
                    selectedSpeeds.add(jsonArray.getDouble(i).toFloat())
                }
                return selectedSpeeds
            } catch (e: JSONException) {
                // Falls through to the default speed list below; a corrupt preference value
                // should not stop the player from starting.
                Log.e(TAG, "Could not read playback speeds from the stored JSON array", e)
            }
        }
        // If this preference hasn't been set yet, return the default options
        return Arrays.asList(0.8f, 1.0f, 1.2f, 1.5f, 2.0f)
    }

    fun shouldPauseForFocusLoss(): Boolean {
        return prefs.getBoolean(Prefs.PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS, true)
    }

    fun shouldResumeAfterCall(): Boolean {
        return prefs.getBoolean(PREF_RESUME_AFTER_CALL, true)
    }

    fun timeRespectsSpeed(): Boolean {
        return prefs.getBoolean(PREF_TIME_RESPECTS_SPEED, false)
    }

    var fastForwardSecs: Int
        get() = prefs.getInt(PREF_FAST_FORWARD_SECS, 30)
        set(secs) {
            prefs.edit()
                .putInt(PREF_FAST_FORWARD_SECS, secs)
                .apply()
        }

    var rewindSecs: Int
        get() = prefs.getInt(PREF_REWIND_SECS, 10)
        set(secs) {
            prefs.edit()
                .putInt(PREF_REWIND_SECS, secs)
                .apply()
        }

    /**
     * Whether the playlist is locked.
     * @return
     */
    var isPlaylistLocked: Boolean
        get() = prefs.getBoolean(PREF_QUEUE_LOCKED, false)
        set(locked) {
            prefs.edit()
                .putBoolean(PREF_QUEUE_LOCKED, locked)
                .apply()
        }

    /**
     * Returns if the queue is in keep sorted mode.
     *
     * @see .getQueueKeepSortedOrder
     */
    /**
     * Enables/disables the keep sorted mode of the queue.
     *
     * @see .setQueueKeepSortedOrder
     */
    var isPlaylistKeepSorted: Boolean
        get() = prefs.getBoolean(Prefs.PREF_QUEUE_KEEP_SORTED, false)
        set(keepSorted) {
            prefs.edit()
                .putBoolean(Prefs.PREF_QUEUE_KEEP_SORTED, keepSorted)
                .apply()
        }

    /**
     * Returns the sort order for the queue keep sorted mode.
     * Note: This value is stored independently from the keep sorted state.
     *
     * @see .isQueueKeepSorted
     */
    /**
     * Sets the sort order for the queue keep sorted mode.
     *
     * @see .setQueueKeepSorted
     */
    var queueKeepSortedOrder: SortOrder?
        get() {
            val sortOrderStr = prefs.getString(Prefs.PREF_QUEUE_KEEP_SORTED_ORDER, "use-default")
            return SortOrder.parseWithDefault(sortOrderStr, SortOrder.DATE_NEW_OLD)
        }
        set(sortOrder) {
            if (sortOrder == null) {
                return
            }
            prefs.edit()
                .putString(Prefs.PREF_QUEUE_KEEP_SORTED_ORDER, sortOrder.name)
                .apply()
        }

    fun useExoplayer(): Boolean {
        return true
    }

    fun stereoToMono(): Boolean {
        return prefs.getBoolean(PREF_STEREO_TO_MONO, false)
    }

    fun stereoToMono(enable: Boolean) {
        prefs.edit()
            .putBoolean(PREF_STEREO_TO_MONO, enable)
            .apply()
    }

    fun audioLoudness(): Boolean {
        return prefs.getBoolean(PREF_AUDIO_LOUDNESS, false)
    }

    fun setAudioLoudness(enable: Boolean) {
        prefs.edit()
            .putBoolean(PREF_AUDIO_LOUDNESS, enable)
            .apply()
    }
}
