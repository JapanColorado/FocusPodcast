package allen.town.focus_common.util

import allen.town.focus_common.extensions.getStringOrDefault
import allen.town.focus_common.model.CategoryInfo
import android.content.Context
import android.content.SharedPreferences
import android.text.format.DateUtils
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import code.name.monkey.appthemehelper.ThemeStoreHack
import code.name.monkey.appthemehelper.constants.ThemeConstants.ADMOB_BANNER_KEY
import code.name.monkey.appthemehelper.constants.ThemeConstants.ADMOB_INTERSTITIAL_KEY
import code.name.monkey.appthemehelper.constants.ThemeConstants.ADMOB_NATIVE_KEY
import code.name.monkey.appthemehelper.constants.ThemeConstants.ADMOB_OPEN_KEY
import code.name.monkey.appthemehelper.constants.ThemeConstants.ADMOB_REWARD_KEY
import code.name.monkey.appthemehelper.constants.ThemeConstants.APP_OPEN_COUNT
import code.name.monkey.appthemehelper.constants.ThemeConstants.BLACK_THEME
import code.name.monkey.appthemehelper.constants.ThemeConstants.CIRCLE_PLAY_BUTTON
import code.name.monkey.appthemehelper.constants.ThemeConstants.COLORED_APP_SHORTCUTS
import code.name.monkey.appthemehelper.constants.ThemeConstants.DESATURATED_COLOR
import code.name.monkey.appthemehelper.constants.ThemeConstants.DISABLE_FIREBASE
import code.name.monkey.appthemehelper.constants.ThemeConstants.FIRST_INSTALL_AND_LAUNCH
import code.name.monkey.appthemehelper.constants.ThemeConstants.FIRST_TO_VIEW_VIDEO_AD
import code.name.monkey.appthemehelper.constants.ThemeConstants.GENERAL_THEME
import code.name.monkey.appthemehelper.constants.ThemeConstants.INTERSTITIAL_AD_TIME
import code.name.monkey.appthemehelper.constants.ThemeConstants.KEEP_SCREEN_ON
import code.name.monkey.appthemehelper.constants.ThemeConstants.LANGUAGE_NAME
import code.name.monkey.appthemehelper.constants.ThemeConstants.LAST_ADMOB_CHECK
import code.name.monkey.appthemehelper.constants.ThemeConstants.LIBRARY_CATEGORIES
import code.name.monkey.appthemehelper.constants.ThemeConstants.MATERIAL_YOU
import code.name.monkey.appthemehelper.constants.ThemeConstants.REWARD_PRO_VALID_TIME
import code.name.monkey.appthemehelper.constants.ThemeConstants.TAB_TEXT_MODE
import code.name.monkey.appthemehelper.constants.ThemeConstants.THEME_AUTO_VALUE
import code.name.monkey.appthemehelper.constants.ThemeConstants.THEME_DARK_VALUE
import code.name.monkey.appthemehelper.constants.ThemeConstants.THEME_LIGHT_VALUE
import code.name.monkey.appthemehelper.constants.ThemeConstants.TOGGLE_FULL_SCREEN
import code.name.monkey.appthemehelper.constants.ThemeConstants.VIEW_VIDEO_AD_TIME
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

    var defaultCategories: List<CategoryInfo>? = null

    @JvmStatic
    val languageCode: String get() = sharedPreferences!!.getString(LANGUAGE_NAME, "auto") ?: "auto"

    @JvmStatic
    var libraryCategory: List<CategoryInfo>
        get() {
            val gson = Gson()
            val collectionType = object : TypeToken<List<CategoryInfo>>() {}.type

            val data = sharedPreferences!!.getStringOrDefault(
                LIBRARY_CATEGORIES,
                gson.toJson(defaultCategories, collectionType)
            )
            return try {
                Gson().fromJson(data, collectionType)
            } catch (e: JsonSyntaxException) {
                e.printStackTrace()
                return defaultCategories!!
            }
        }
        set(value) {
            val collectionType = object : TypeToken<List<CategoryInfo?>?>() {}.type
            sharedPreferences!!.edit {
                putString(LIBRARY_CATEGORIES, Gson().toJson(value, collectionType))
            }
        }

    @JvmStatic
    fun instance(context: Context): SharedPreferences {
        if (sharedPreferences == null) {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        }
        return sharedPreferences!!
    }

    private val isBlackMode
        get() = sharedPreferences!!.getBoolean(
            BLACK_THEME, false
        )

    @JvmStatic
    val isScreenOnEnabled
        get() = sharedPreferences!!.getBoolean(KEEP_SCREEN_ON, false)

    @JvmStatic
    val isFullScreenMode
        get() = sharedPreferences!!.getBoolean(
            TOGGLE_FULL_SCREEN, false
        )

    @JvmStatic
    val materialYou
        get() = ThemeStoreHack.isMaterialYou && sharedPreferences!!.getBoolean(MATERIAL_YOU, VersionUtils.hasS())

    @JvmStatic
    var wallpaperAccent
        get() = sharedPreferences!!.getBoolean(
            WALLPAPER_ACCENT,
            VersionUtils.hasOreoMR1() && !VersionUtils.hasS()
        )
        set(value) = sharedPreferences!!.edit {
            putBoolean(WALLPAPER_ACCENT, value)
        }

    @JvmStatic
    var appOpenCount
        get() = sharedPreferences!!.getInt(
            APP_OPEN_COUNT,
            0
        )
        set(value) = sharedPreferences!!.edit {
            putInt(APP_OPEN_COUNT, value)
        }

    var isColoredAppShortcuts
        get() = sharedPreferences!!.getBoolean(
            COLORED_APP_SHORTCUTS, true
        )
        set(value) = sharedPreferences!!.edit {
            putBoolean(COLORED_APP_SHORTCUTS, value)
        }

    @JvmStatic
    var isDesaturatedColor
        get() = sharedPreferences!!.getBoolean(
            DESATURATED_COLOR, false
        )
        set(value) = sharedPreferences!!.edit {
            putBoolean(DESATURATED_COLOR, value)
        }

    @JvmStatic
    val circlePlayButton
        get() = sharedPreferences!!.getBoolean(CIRCLE_PLAY_BUTTON, false)

    @JvmStatic
    fun getGeneralThemeValue(isSystemDark: Boolean): ThemeMode {
        val themeMode: String =
            sharedPreferences!!.getStringOrDefault(GENERAL_THEME, THEME_AUTO_VALUE)
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
        return  sharedPreferences!!.getStringOrDefault(GENERAL_THEME, THEME_AUTO_VALUE)
    }

    @JvmStatic
    var lastAdmobCheckTime: Long
        get() {
            val time = sharedPreferences!!.getLong(LAST_ADMOB_CHECK, 0)
            Timber.d("return last admob check time $time")
            return time
        }
        set(time) {
            sharedPreferences!!.edit()
                .putLong(LAST_ADMOB_CHECK, time)
                .commit()
            Timber.d("last admob check time $time")
        }

    /**
     * 是否需要检查admob 信息，间隔时间是48小时，不能太频繁，否则占用api调用数
     */
    @JvmStatic
    fun needCheckAdmobInfo(): Boolean {
        val currentTime = System.currentTimeMillis()
        return currentTime - lastAdmobCheckTime > 48 * 60 * 60 * 1000
    }

    //奖励pro会员到期时间
    @JvmStatic
    var rewardAdValidTime: Long
        get() {
            val time = sharedPreferences!!.getLong(REWARD_PRO_VALID_TIME, 0)
            Timber.d("return ad valid time $time")
            return time
        }
        set(value) {
            //激励时间比当前时间大说明没过期，直接累加10分钟，否则从当前传入开始时间计算（每次多加30秒，是因为广告时间最多30s）
            var time =
                (if (rewardAdValidTime >= value) rewardAdValidTime else value) + 10 * 60 * 1000 + 30000
            sharedPreferences!!.edit()
                .putLong(REWARD_PRO_VALID_TIME, time)
                .commit()
            Timber.d("ad valid time $time")
            setRewardAdViewLog(value)
        }

    /**
     * 记录激励广告观看日志
     */
    private fun setRewardAdViewLog(time: Long) {
        val logSet = sharedPreferences!!.getStringSet(VIEW_VIDEO_AD_TIME, null) as? HashSet
        if (logSet == null || !DateUtils.isToday(logSet.toList()[0].toLong())) {
            //如果为空或者里面的第一条记录不是今天，那么是新的一天
            Timber.d("setRewardAdViewLog for new today")
            sharedPreferences!!.edit()
                .putStringSet(VIEW_VIDEO_AD_TIME, HashSet<String>().apply {
                    add("$time")
                })
                .commit()
        } else {
            val list = logSet.toList()
            if (list.size >= 2) {
                Timber.i("up to 2 toady")
            } else {
                Timber.d("setRewardAdViewLog for today")
                sharedPreferences!!.edit()
                    .putStringSet(VIEW_VIDEO_AD_TIME, logSet.apply {
                        add("$time")
                    })
                    .commit()
            }
        }

    }

    /**
     * 今天还是否显示激励按钮，今天激励过2次就不显示了
     */
    @JvmStatic
    fun isRewardCanShowToday(): Boolean {
        var flag = true
        val logSet = sharedPreferences!!.getStringSet(VIEW_VIDEO_AD_TIME, null) as? HashSet
        if (logSet != null && logSet.toList().size >= 2 && DateUtils.isToday(logSet.toList()[0].toLong())) {
            flag = false
        }
        Timber.i("show reward ad? $flag")
        return flag
    }


    @JvmStatic
    val viewVideoAdTimeInToday
        get() = DateUtils.isToday(rewardAdValidTime)

    @JvmStatic
    fun isRewardAdProValid(): Boolean {
        //激励广告会员有效期
        val time = rewardAdValidTime
        val currentTime = System.currentTimeMillis()
        try {
            if (time > currentTime && time - currentTime < 22 * 60 * 1000) {
                //最多20分钟会员，如果有效期比当前时间大22分钟说明被篡改了
                Timber.i("this is temporary suppoter or ad blocker")
                return true
            }
        } catch (e: Exception) {
            Timber.e("parse view video ad time failed", e)
            return false
        }
        return false
    }

    @JvmStatic
    var firstToViewVideoAd: Boolean
        get() {
            return sharedPreferences!!.getBoolean(FIRST_TO_VIEW_VIDEO_AD, true)
        }
        set(value) {
            sharedPreferences!!.edit()
                .putBoolean(FIRST_TO_VIEW_VIDEO_AD, value)
                .commit()
        }

    @JvmStatic
    var interstitialAdTimeValid: Boolean = false
        get() {
            //超过20分钟有效，即插屏广告20分钟内只会显示1次
            return System.currentTimeMillis() - sharedPreferences!!.getLong(
                INTERSTITIAL_AD_TIME,
                0
            ) > 20 * 60 * 1000
        }

    fun setInterstitialAdTime(time: Long) {
        sharedPreferences!!.edit()
            .putLong(INTERSTITIAL_AD_TIME, time)
            .commit()
    }

    @JvmStatic
    var firstInstallAndLaunch: Boolean
        get() {
            return sharedPreferences!!.getBoolean(FIRST_INSTALL_AND_LAUNCH, true)
        }
        set(value) {
            sharedPreferences!!.edit()
                .putBoolean(FIRST_INSTALL_AND_LAUNCH, value)
                .commit()
        }

    @JvmStatic
    var webDevUrl: String?
        get() {
            return sharedPreferences!!.getString(WEBDEV_SERVER_URL, "")
        }
        set(value) {
            sharedPreferences!!.edit()
                .putString(WEBDEV_SERVER_URL, value)
                .commit()
        }

    @JvmStatic
    var webDevUser: String?
        get() {
            return sharedPreferences!!.getString(WEBDEV_SERVER_USER, "")
        }
        set(value) {
            sharedPreferences!!.edit()
                .putString(WEBDEV_SERVER_USER, value)
                .commit()
        }

    @JvmStatic
    var webDevPass: String?
        get() {
            return sharedPreferences!!.getString(WEBDEV_SERVER_PASS, "")
        }
        set(value) {
            sharedPreferences!!.edit()
                .putString(WEBDEV_SERVER_PASS, value)
                .commit()
        }

    @JvmStatic
    var admobOpenAdId: String?
        get() {
            return sharedPreferences!!.getString(ADMOB_OPEN_KEY, null)
        }
        set(value) {
            sharedPreferences!!.edit()
                .putString(ADMOB_OPEN_KEY, value)
                .commit()
        }

    @JvmStatic
    var admobRewardAdId: String?
        get() {
            return sharedPreferences!!.getString(ADMOB_REWARD_KEY, null)
        }
        set(value) {
            sharedPreferences!!.edit()
                .putString(ADMOB_REWARD_KEY, value)
                .commit()
        }

    @JvmStatic
    var admobBannerAdId: String?
        get() {
            return sharedPreferences!!.getString(ADMOB_BANNER_KEY, null)
        }
        set(value) {
            sharedPreferences!!.edit()
                .putString(ADMOB_BANNER_KEY, value)
                .commit()
        }

    @JvmStatic
    var admobInterstitialAdId: String?
        get() {
            return sharedPreferences!!.getString(ADMOB_INTERSTITIAL_KEY, null)
        }
        set(value) {
            sharedPreferences!!.edit()
                .putString(ADMOB_INTERSTITIAL_KEY, value)
                .commit()
        }

    @JvmStatic
    var admobNativeAdId: String?
        get() {
            return sharedPreferences!!.getString(ADMOB_NATIVE_KEY, null)
        }
        set(value) {
            sharedPreferences!!.edit()
                .putString(ADMOB_NATIVE_KEY, value)
                .commit()
        }

    @JvmStatic
    fun setStringValue(key: String, value: String) {
        sharedPreferences!!.edit()
            .putString(key, value)
            .commit()
    }

    @JvmStatic
    val tabTitleMode: Int
        get() {
            return when (sharedPreferences!!.getStringOrDefault(
                TAB_TEXT_MODE, "0"
            ).toInt()) {
                0 -> BottomNavigationView.LABEL_VISIBILITY_AUTO
                1 -> BottomNavigationView.LABEL_VISIBILITY_LABELED
                2 -> BottomNavigationView.LABEL_VISIBILITY_SELECTED
                3 -> BottomNavigationView.LABEL_VISIBILITY_UNLABELED
                else -> BottomNavigationView.LABEL_VISIBILITY_LABELED
            }
        }

    @JvmStatic
    var isDisableFirebase: Boolean = false
        get() {
            return sharedPreferences!!.getBoolean(
                DISABLE_FIREBASE,
                false
            )
        }
}
