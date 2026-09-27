package allen.town.podcast.activity

import allen.town.podcast.common.extensions.*
import allen.town.podcast.common.util.BasePreferenceUtil
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import allen.town.podcast.theme.util.VersionUtils
import allen.town.podcast.common.theme.ThemeManager
import allen.town.podcast.R
import allen.town.podcast.core.pref.Prefs
import allen.town.podcast.common.activity.ToolbarBaseActivity

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
}