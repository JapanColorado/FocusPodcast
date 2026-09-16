package allen.town.focus_common.views

import allen.town.focus_common.R
import allen.town.focus_common.util.BasePreferenceUtil
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import code.name.monkey.appthemehelper.ThemeStore
import allen.town.focus_common.extensions.addAlpha
import com.google.android.material.tabs.TabLayout

class AccentTabLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : TabLayout(context, attrs) {
    init {

//        tabLayout.setTabGravity(GRAVITY_CENTER);
        setTabIndicatorFullWidth(true)
        setSelectedTabIndicatorGravity(INDICATOR_GRAVITY_STRETCH)
        //修改tint颜色没用
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