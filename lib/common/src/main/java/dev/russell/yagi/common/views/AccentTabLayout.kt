package dev.russell.yagi.common.views

import dev.russell.yagi.common.R
import dev.russell.yagi.common.util.BasePreferenceUtil
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import dev.russell.yagi.theme.ThemeStore
import dev.russell.yagi.common.extensions.addAlpha
import com.google.android.material.tabs.TabLayout

class AccentTabLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : TabLayout(context, attrs) {
    init {

        setTabIndicatorFullWidth(true)
        setSelectedTabIndicatorGravity(INDICATOR_GRAVITY_STRETCH)
        // Changing the tint color has no effect
        val selectedDrawable: Drawable? = context.getDrawable(R.drawable.cat_tabs_pill_indicator)
        if (!BasePreferenceUtil.materialYou) {
            setTabTextColors(
                ThemeStore.textColorSecondary(context),
                ThemeStore.accentColor(context).addAlpha(0.6F)
            )
            setSelectedTabIndicator(
                selectedDrawable
            )
            setSelectedTabIndicatorColor(ThemeStore.accentColor(context))
            tabRippleColor = ColorStateList.valueOf(ThemeStore.accentColor(context).addAlpha(0.12F))
        } else {
            setSelectedTabIndicator(selectedDrawable)
        }
    }
}