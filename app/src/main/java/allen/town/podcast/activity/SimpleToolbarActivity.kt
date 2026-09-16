package allen.town.podcast.activity

import allen.town.focus_common.extensions.*
import allen.town.focus_common.util.BasePreferenceUtil
import allen.town.focus_common.util.LanguageContextWrapper
import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat
import code.name.monkey.appthemehelper.util.VersionUtils
import code.name.monkey.retromusic.util.theme.ThemeManager
import allen.town.podcast.R
import allen.town.podcast.core.pref.Prefs
import allen.town.focus_common.activity.ToolbarBaseActivity
import java.util.*

abstract class SimpleToolbarActivity : ToolbarBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        updateTheme()
        super.onCreate(savedInstanceState)
        setEdgeToEdgeOrImmersive(R.id.status_bar,false)
        toggleScreenOn()
        setLightNavigationBarAuto()
        setLightStatusBarAuto(surfaceColor())
        if (VersionUtils.hasQ()) {
            window.decorView.isForceDarkAllowed = false
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        exitFullscreen()
    }

    private fun updateTheme() {
        setTheme(Prefs.theme)
        AppCompatDelegate.setDefaultNightMode(ThemeManager.getNightMode(application))

        if (BasePreferenceUtil.circlePlayButton) {
            //enabling this breaks the context menu background and icons, so it stays off
            setTheme(R.style.CircleFABOverlay)
        }
    }

    override fun    attachBaseContext(newBase: Context?) {
        val code = BasePreferenceUtil.languageCode
        val locale = if (code == "auto") {
            // Get the device default locale
            ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0]
        } else {
            Locale.forLanguageTag(code)
        }
        super.attachBaseContext(LanguageContextWrapper.wrap(newBase, locale))
        //related to Android App Bundles; used for loading resources. Unclear whether it affects plain APK builds.
    }
}