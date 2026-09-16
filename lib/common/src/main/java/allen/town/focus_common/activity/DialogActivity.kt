package allen.town.focus_common.activity

import allen.town.focus_common.util.BasePreferenceUtil
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import code.name.monkey.retromusic.util.theme.ThemeManager
import com.google.android.material.color.DynamicColors

open class DialogActivity : AppCompatActivity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        updateTheme()
        BasePreferenceUtil.instance(this)
        super.onCreate(savedInstanceState)
    }

    private fun updateTheme() {
        AppCompatDelegate.setDefaultNightMode(ThemeManager.getNightMode(application))

        // Apply dynamic colors to activity if enabled
        if (BasePreferenceUtil.materialYou) {
            DynamicColors.applyIfAvailable(
                this,
                com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_DayNight
            )
        }
    }
}