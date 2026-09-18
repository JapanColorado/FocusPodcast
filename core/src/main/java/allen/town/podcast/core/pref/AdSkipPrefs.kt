package allen.town.podcast.core.pref

import allen.town.podcast.core.pref.PrefsStore.getString
import allen.town.podcast.core.pref.PrefsStore.prefs

/**
 * Owns the global ad auto-skip settings: the master switch, how sure the detector has to be
 * before a segment is skipped (the "sensitivity" list preference and the confidence threshold
 * derived from it), whether a finished download queues an analysis run, and whether the player
 * announces a skip with a snackbar. It reads and writes through [PrefsStore]; [Prefs] exposes
 * every member of it unchanged.
 *
 * The feature is opt-in ([isAdSkipEnabled] defaults to false) because detection is heuristic.
 * The threshold only ever gates [allen.town.podcast.model.feed.AdSegment.Source.DETECTED]
 * segments: chapter-derived and hand-marked ones are exact and always skip.
 */
internal object AdSkipPrefs {

    /** Require a very sure detection: only segments at 0.75 confidence or above are skipped. */
    const val SENSITIVITY_LOW = "low"

    /** The default: skip from 0.60 confidence upwards. */
    const val SENSITIVITY_MEDIUM = "medium"

    /** Skip eagerly, from 0.45 confidence upwards, accepting more false positives. */
    const val SENSITIVITY_HIGH = "high"

    private const val MIN_CONFIDENCE_LOW = 0.75f
    private const val MIN_CONFIDENCE_MEDIUM = 0.60f
    private const val MIN_CONFIDENCE_HIGH = 0.45f

    var isAdSkipEnabled: Boolean
        get() = prefs.getBoolean(Prefs.PREF_AD_SKIP_ENABLED, false)
        set(enabled) {
            prefs.edit().putBoolean(Prefs.PREF_AD_SKIP_ENABLED, enabled).apply()
        }

    /** One of [SENSITIVITY_LOW], [SENSITIVITY_MEDIUM], [SENSITIVITY_HIGH]. */
    var adSkipSensitivity: String
        get() = getString(Prefs.PREF_AD_SKIP_SENSITIVITY, SENSITIVITY_MEDIUM)
        set(sensitivity) {
            prefs.edit().putString(Prefs.PREF_AD_SKIP_SENSITIVITY, sensitivity).apply()
        }

    /**
     * The lowest confidence a detected segment may have and still be skipped. An unknown stored
     * value reads as the medium threshold rather than throwing, so a hand-edited or migrated
     * preference cannot break playback.
     */
    val adSkipMinConfidence: Float
        get() = when (adSkipSensitivity) {
            SENSITIVITY_LOW -> MIN_CONFIDENCE_LOW
            SENSITIVITY_HIGH -> MIN_CONFIDENCE_HIGH
            else -> MIN_CONFIDENCE_MEDIUM
        }

    var isAdSkipAnalyzeOnDownload: Boolean
        get() = prefs.getBoolean(Prefs.PREF_AD_SKIP_ANALYZE_ON_DOWNLOAD, true)
        set(analyze) {
            prefs.edit().putBoolean(Prefs.PREF_AD_SKIP_ANALYZE_ON_DOWNLOAD, analyze).apply()
        }

    var isAdSkipShowSnackbar: Boolean
        get() = prefs.getBoolean(Prefs.PREF_AD_SKIP_SHOW_SNACKBAR, true)
        set(show) {
            prefs.edit().putBoolean(Prefs.PREF_AD_SKIP_SHOW_SNACKBAR, show).apply()
        }
}
