package allen.town.focus_common.util

import allen.town.focus_common.extensions.getStringOrDefault
import allen.town.focus_common.model.CategoryInfo
import android.content.Context
import android.content.SharedPreferences
import android.text.format.DateUtils
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import code.name.monkey.appthemehelper.ThemeStoreHack
import code.name.monkey.appthemehelper.constants.ThemeConstants.APP_OPEN_COUNT
import code.name.monkey.appthemehelper.constants.ThemeConstants.BLACK_THEME
import code.name.monkey.appthemehelper.constants.ThemeConstants.CIRCLE_PLAY_BUTTON
import code.name.monkey.appthemehelper.constants.ThemeConstants.COLORED_APP_SHORTCUTS
import code.name.monkey.appthemehelper.constants.ThemeConstants.DESATURATED_COLOR
import code.name.monkey.appthemehelper.constants.ThemeConstants.FIRST_INSTALL_AND_LAUNCH
import code.name.monkey.appthemehelper.constants.ThemeConstants.GENERAL_THEME
import code.name.monkey.appthemehelper.constants.ThemeConstants.INTERSTITIAL_AD_TIME
import code.name.monkey.appthemehelper.constants.ThemeConstants.KEEP_SCREEN_ON
import code.name.monkey.appthemehelper.constants.ThemeConstants.LANGUAGE_NAME
import code.name.monkey.appthemehelper.constants.ThemeConstants.LIBRARY_CATEGORIES
import code.name.monkey.appthemehelper.constants.ThemeConstants.MATERIAL_YOU
import code.name.monkey.appthemehelper.constants.ThemeConstants.TAB_TEXT_MODE
import code.name.monkey.appthemehelper.constants.ThemeConstants.THEME_AUTO_VALUE
import code.name.monkey.appthemehelper.constants.ThemeConstants.THEME_DARK_VALUE
import code.name.monkey.appthemehelper.constants.ThemeConstants.THEME_LIGHT_VALUE
import code.name.monkey.appthemehelper.constants.ThemeConstants.TOGGLE_FULL_SCREEN
import code.name.monkey.appthemehelper.constants.ThemeConstants.WALLPAPER_ACCENT
import code.name.monkey.appthemehelper.constants.ThemeConstants.WEBDEV_SERVER_PASS
import code.name.monkey.appthemehelper.constants.ThemeConstants.WEBDEV_SERVER_URL
import code.name.monkey.appthemehelper.constants.ThemeConstants.WEBDEV_SERVER_USER
import code.name.monkey.appthemehelper.util.VersionUtils
import code.name.monkey.retromusic.util.theme.ThemeMode
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken


object BasePreferenceUtil {
    var sharedPreferences: SharedPreferences? = null

    var defaultCategories: List<CategoryInfo> = emptyList()

    /**
     * The shared preferences, which [instance] installs from the Application's onCreate before any
     * other code in the app can read a preference.
     */
    private val prefs: SharedPreferences
        get() = checkNotNull(sharedPreferences) {
            "BasePreferenceUtil.instance(context) has not been called yet"
        }

    @JvmStatic
    val languageCode: String get() = prefs.getString(LANGUAGE_NAME, "auto") ?: "auto"

    @JvmStatic
    var libraryCategory: List<CategoryInfo>
        get() {
            val gson = Gson()
            val collectionType = object : TypeToken<List<CategoryInfo>>() {}.type

            val data = prefs.getStringOrDefault(
                LIBRARY_CATEGORIES,
                gson.toJson(defaultCategories, collectionType)
            )
            return try {
                Gson().fromJson(data, collectionType)
            } catch (e: JsonSyntaxException) {
                // Stored categories are from an older/incompatible build: fall back to the
                // defaults rather than leaving the library screen empty.
                Timber.e(e, "could not read the stored library categories")
                return defaultCategories
            }
        }
        set(value) {
            val collectionType = object : TypeToken<List<CategoryInfo?>?>() {}.type
            prefs.edit {
                putString(LIBRARY_CATEGORIES, Gson().toJson(value, collectionType))
            }
        }

    @JvmStatic
    fun instance(context: Context): SharedPreferences {
        if (sharedPreferences == null) {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        }
        return prefs
    }

    private val isBlackMode
        get() = prefs.getBoolean(
            BLACK_THEME, false
        )

    @JvmStatic
    val isScreenOnEnabled
        get() = prefs.getBoolean(KEEP_SCREEN_ON, false)

    @JvmStatic
    val isFullScreenMode
        get() = prefs.getBoolean(
            TOGGLE_FULL_SCREEN, false
        )

    @JvmStatic
    val materialYou
        get() = ThemeStoreHack.isMaterialYou && prefs.getBoolean(MATERIAL_YOU, VersionUtils.hasS())

    @JvmStatic
    var wallpaperAccent
        get() = prefs.getBoolean(
            WALLPAPER_ACCENT,
            VersionUtils.hasOreoMR1() && !VersionUtils.hasS()
        )
        set(value) = prefs.edit {
            putBoolean(WALLPAPER_ACCENT, value)
        }

    @JvmStatic
    var appOpenCount
        get() = prefs.getInt(
            APP_OPEN_COUNT,
            0
        )
        set(value) = prefs.edit {
            putInt(APP_OPEN_COUNT, value)
        }

    var isColoredAppShortcuts
        get() = prefs.getBoolean(
            COLORED_APP_SHORTCUTS, true
        )
        set(value) = prefs.edit {
            putBoolean(COLORED_APP_SHORTCUTS, value)
        }

    @JvmStatic
    var isDesaturatedColor
        get() = prefs.getBoolean(
            DESATURATED_COLOR, false
        )
        set(value) = prefs.edit {
            putBoolean(DESATURATED_COLOR, value)
        }

    @JvmStatic
    val circlePlayButton
        get() = prefs.getBoolean(CIRCLE_PLAY_BUTTON, false)

    @JvmStatic
    fun getGeneralThemeValue(isSystemDark: Boolean): ThemeMode {
        val themeMode: String =
            prefs.getStringOrDefault(GENERAL_THEME, THEME_AUTO_VALUE)
        return if (isBlackMode && isSystemDark && themeMode != THEME_LIGHT_VALUE) {
            ThemeMode.BLACK
        } else {
            if (isBlackMode && themeMode == THEME_DARK_VALUE) {
                ThemeMode.BLACK
            } else {
                when (themeMode) {
                    THEME_LIGHT_VALUE -> ThemeMode.LIGHT
                    THEME_DARK_VALUE -> ThemeMode.DARK
                    THEME_AUTO_VALUE -> if (isSystemDark) ThemeMode.DARK else ThemeMode.LIGHT
                    else -> ThemeMode.AUTO
                }
            }
        }
    }

    @JvmStatic
    fun getGeneralThemeValueOriginal(): String {
        return  prefs.getStringOrDefault(GENERAL_THEME, THEME_AUTO_VALUE)
    }

    @JvmStatic
    var interstitialAdTimeValid: Boolean = false
        get() {
            // Valid once more than 20 minutes have passed, i.e. the interstitial shows at most once per 20 minutes
            return System.currentTimeMillis() - prefs.getLong(
                INTERSTITIAL_AD_TIME,
                0
            ) > 20 * 60 * 1000
        }

    fun setInterstitialAdTime(time: Long) {
        prefs.edit()
            .putLong(INTERSTITIAL_AD_TIME, time)
            .commit()
    }

    @JvmStatic
    var firstInstallAndLaunch: Boolean
        get() {
            return prefs.getBoolean(FIRST_INSTALL_AND_LAUNCH, true)
        }
        set(value) {
            prefs.edit()
                .putBoolean(FIRST_INSTALL_AND_LAUNCH, value)
                .commit()
        }

    @JvmStatic
    var webDevUrl: String?
        get() {
            return prefs.getString(WEBDEV_SERVER_URL, "")
        }
        set(value) {
            prefs.edit()
                .putString(WEBDEV_SERVER_URL, value)
                .commit()
        }

    @JvmStatic
    var webDevUser: String?
        get() {
            return prefs.getString(WEBDEV_SERVER_USER, "")
        }
        set(value) {
            prefs.edit()
                .putString(WEBDEV_SERVER_USER, value)
                .commit()
        }

    @JvmStatic
    var webDevPass: String?
        get() {
            return prefs.getString(WEBDEV_SERVER_PASS, "")
        }
        set(value) {
            prefs.edit()
                .putString(WEBDEV_SERVER_PASS, value)
                .commit()
        }

    @JvmStatic
    fun setStringValue(key: String, value: String) {
        prefs.edit()
            .putString(key, value)
            .commit()
    }

    @JvmStatic
    val tabTitleMode: Int
        get() {
            return when (prefs.getStringOrDefault(
                TAB_TEXT_MODE, "0"
            ).toInt()) {
                0 -> BottomNavigationView.LABEL_VISIBILITY_AUTO
                1 -> BottomNavigationView.LABEL_VISIBILITY_LABELED
                2 -> BottomNavigationView.LABEL_VISIBILITY_SELECTED
                3 -> BottomNavigationView.LABEL_VISIBILITY_UNLABELED
                else -> BottomNavigationView.LABEL_VISIBILITY_LABELED
            }
        }

}
