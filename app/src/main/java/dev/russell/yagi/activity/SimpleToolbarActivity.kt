package dev.russell.yagi.activity

import dev.russell.yagi.common.extensions.*
import dev.russell.yagi.common.util.BasePreferenceUtil
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import dev.russell.yagi.theme.util.VersionUtils
import dev.russell.yagi.common.theme.ThemeManager
import dev.russell.yagi.R
import dev.russell.yagi.core.pref.Prefs
import dev.russell.yagi.common.activity.ToolbarBaseActivity

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