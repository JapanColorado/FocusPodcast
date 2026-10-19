package dev.russell.focuspodcast.activity

import dev.russell.focuspodcast.common.extensions.*
import dev.russell.focuspodcast.common.util.BasePreferenceUtil
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import dev.russell.focuspodcast.theme.util.VersionUtils
import dev.russell.focuspodcast.common.theme.ThemeManager
import dev.russell.focuspodcast.R
import dev.russell.focuspodcast.core.pref.Prefs
import dev.russell.focuspodcast.common.activity.ToolbarBaseActivity

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