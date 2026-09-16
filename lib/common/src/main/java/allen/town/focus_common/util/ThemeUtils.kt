package allen.town.focus_common.util

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import code.name.monkey.retromusic.extensions.generalThemeValue
import code.name.monkey.retromusic.util.theme.ThemeMode

object ThemeUtils {

    @JvmStatic
    fun generalThemeValue(context: Context): ThemeMode {
        return context.generalThemeValue
    }

    @JvmStatic
    @ColorInt
    fun getColorFromAttr(context: Context, @AttrRes attr: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(context, typedValue.resourceId)
        } else typedValue.data
    }

    @JvmStatic
    @DrawableRes
    fun getDrawableFromAttr(context: Context, @AttrRes attr: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
        return typedValue.resourceId
    }

    @JvmStatic
    fun getIntFromAttr(context: Context, @AttrRes attr: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
        return if (typedValue.resourceId != 0) {
            context.resources.getDimensionPixelSize(typedValue.resourceId)
        } else typedValue.data
    }
}