package allen.town.podcast.core.pref

import allen.town.podcast.common.util.BasePreferenceUtil.instance
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import java.io.File
import java.io.IOException

/**
 * Owns the process-wide state that every preference group in this package reads through: the
 * application context, the default [SharedPreferences] instance and the theme preference store,
 * together with the `init` / `resetForTests` lifecycle and the shared `getString` reader that
 * never leaks a null default. Keeping it in one object means the "not initialised" error is
 * raised in exactly one place no matter which preference a caller touches first. Callers outside
 * this package go through [Prefs], which delegates here.
 */
internal object PrefsStore {

    const val TAG = "Prefs"

    private const val NOT_INITIALIZED =
        "Prefs.init() must be called (via ClientConfig.initialize) before any preference is used"

    /**
     * Typed as [Application] rather than [Context] on purpose: this field lives for the whole
     * process, so only the application instance may be stored here. Anything shorter-lived (an
     * Activity, a Service) would be leaked.
     */
    private var applicationContext: Application? = null
    private var sharedPrefs: SharedPreferences? = null
    private var themePrefs: SharedPreferences? = null

    val context: Application
        get() = applicationContext ?: error(NOT_INITIALIZED)

    val prefs: SharedPreferences
        get() = sharedPrefs ?: error(NOT_INITIALIZED)

    /**
     * Sets up the preference store.
     */
    fun init(context: Context) {
        Log.d(TAG, "init")
        applicationContext = context.applicationContext as Application
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        themePrefs = instance(context)
        createNoMediaFile()
    }

    /**
     * Drops the process-wide state so that a unit test can assert the uninitialised behaviour.
     * Robolectric reuses one class loader for every test class with the same configuration, so
     * without this hook the "not initialised" branch would only ever be reachable in whichever
     * test happened to run first.
     */
    fun resetForTests() {
        applicationContext = null
        sharedPrefs = null
        themePrefs = null
    }

    /** Reads a string preference that always has a non-null default. */
    fun getString(key: String, defaultValue: String): String =
        prefs.getString(key, defaultValue) ?: defaultValue

    /**
     * Create a .nomedia file to prevent scanning by the media scanner.
     */
    private fun createNoMediaFile() {
        val f = File(context.getExternalFilesDir(null), ".nomedia")
        if (!f.exists()) {
            try {
                f.createNewFile()
            } catch (e: IOException) {
                // Safe to ignore: without .nomedia the media scanner may index downloaded
                // episodes, which is cosmetic and not worth failing the data folder setup for.
                Log.e(TAG, "Could not create .nomedia file in " + f.parent, e)
            }
            Log.d(TAG, ".nomedia file created")
        }
    }
}
