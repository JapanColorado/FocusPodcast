package allen.town.podcast.pref

import allen.town.podcast.theme.constants.ThemeConstants.LANGUAGE_NAME
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceManager
import java.util.Locale

/**
 * The app language is the per-app locale that AppCompat keeps (the system's LocaleManager on
 * Android 13+, AppCompat's own storage via AppLocalesMetadataHolderService below that), so the
 * choice made in the app and the one made in system Settings are the same value. The
 * `language_name` list preference is only a view of it and is not persisted.
 */
object AppLanguage {
    /** Entry value of the "System default" item in `pref_language_codes`. */
    const val AUTO = "auto"

    /** Apply a code from `pref_language_codes`; [AUTO] clears the per-app locale. */
    fun apply(code: String) {
        AppCompatDelegate.setApplicationLocales(
            if (code == AUTO) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(code)
        )
    }

    /**
     * The entry of [codes] that best matches the current per-app locale: same language and
     * region, else same language (the region-less entry first), else [AUTO].
     */
    fun currentCode(codes: Array<out CharSequence>): String {
        val locale = AppCompatDelegate.getApplicationLocales()[0] ?: return AUTO
        return closestCode(locale, codes.map { it.toString() })
    }

    internal fun closestCode(locale: Locale, codes: List<String>): String {
        val language = normalizeLanguage(locale.language)
        var region = locale.country
        if (language == "zh" && region.isEmpty()) {
            // zh-Hant without a region is Traditional Chinese; everything else is Simplified
            region = if (locale.script == "Hant") "TW" else "CN"
        } else if (language == "zh" && region in setOf("HK", "MO")) {
            region = "TW"
        }
        val candidates = codes.filter { it != AUTO }.map { it to Locale.forLanguageTag(it) }
            .filter { (_, candidate) -> normalizeLanguage(candidate.language) == language }
        return candidates.firstOrNull { (_, candidate) -> candidate.country == region }?.first
            ?: candidates.firstOrNull { (_, candidate) -> candidate.country.isEmpty() }?.first
            ?: candidates.firstOrNull()?.first
            ?: AUTO
    }

    /** Java and older Android report Hebrew as the legacy code "iw". */
    private fun normalizeLanguage(language: String): String = if (language == "iw") "he" else language

    /**
     * One-time move from the old in-app language preference (which wrapped every activity's
     * context) to the per-app locale. Runs from Application.onCreate, where AppCompat has no
     * activity to reach the LocaleManager through, so Android 13+ talks to it directly.
     */
    fun migrateLegacyPreference(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val code = prefs.getString(LANGUAGE_NAME, null) ?: return
        prefs.edit().remove(LANGUAGE_NAME).apply()
        if (code.isEmpty() || code == AUTO) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java) ?: return
            if (localeManager.applicationLocales.isEmpty) {
                localeManager.applicationLocales = LocaleList.forLanguageTags(code)
            }
        } else {
            // Stored by AppCompat (autoStoreLocales) once the first activity is created
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
        }
    }
}
