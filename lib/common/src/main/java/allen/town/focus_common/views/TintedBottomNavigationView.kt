package allen.town.focus_common.views


import allen.town.focus_common.util.BasePreferenceUtil
import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import code.name.monkey.appthemehelper.ThemeStore
import code.name.monkey.appthemehelper.util.ATHUtil
import allen.town.focus_common.extensions.addAlpha
import com.google.android.material.bottomnavigation.BottomNavigationView
import dev.chrisbanes.insetter.applyInsetter
import allen.town.focus_common.extensions.setItemColors
import allen.town.focus_common.extensions.darkAccentColor

class TintedBottomNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BottomNavigationView(context, attrs, defStyleAttr) {

    override fun getMaxItemCount(): Int {
        return 5
    }

    fun hideBottomNavigationView(){
        clearAnimation()
        animate().translationY(height.toFloat()).duration = 300
    }

    fun showBottomNavigationView(){
        clearAnimation()
        animate().translationY(0f).duration = 300
    }
    init {
        if (!isInEditMode) {
            // If we are in Immersive mode we have to just set empty OnApplyWindowInsetsListener as
            // bottom, start, and end padding is always applied (with the help of OnApplyWindowInsetsListener) to
            // BottomNavigationView to dodge the system navigation bar (so we basically clear that listener).
            if (BasePreferenceUtil.isFullScreenMode) {
                setOnApplyWindowInsetsListener { _, insets ->
                    insets
                }
            } else {
                applyInsetter {
                    type(navigationBars = true) {
                        padding(vertical = true)
                        margin(horizontal = true)
                    }
                }
            }

            labelVisibilityMode = BasePreferenceUtil.tabTitleMode

            if (!BasePreferenceUtil.materialYou) {
                val iconColor = ATHUtil.resolveColor(context, android.R.attr.colorControlNormal)
                val accentColor = ThemeStore.accentColor(context)
                setItemColors(iconColor, accentColor)
                itemRippleColor = ColorStateList.valueOf(accentColor.addAlpha(0.08F))
                itemActiveIndicatorColor = ColorStateList.valueOf(accentColor.addAlpha(0.12F))
                backgroundTintList = ColorStateList.valueOf(context.darkAccentColor())
            }
        }
    }
}