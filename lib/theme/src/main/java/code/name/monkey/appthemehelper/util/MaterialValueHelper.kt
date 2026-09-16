package code.name.monkey.appthemehelper.util

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat

import code.name.monkey.appthemehelper.R

object MaterialValueHelper {

    @SuppressLint("PrivateResource")
    @JvmStatic
    @ColorInt
    fun getPrimaryTextColor(context: Context?, dark: Boolean): Int {
        val ctx = checkNotNull(context) { "getPrimaryTextColor needs a context to resolve a color" }
        return if (dark) {
            ContextCompat.getColor(ctx, androidx.appcompat.R.color.primary_text_default_material_light)
        } else ContextCompat.getColor(ctx, androidx.appcompat.R.color.primary_text_default_material_dark)
    }

    @SuppressLint("PrivateResource")
    @JvmStatic
    @ColorInt
    fun getSecondaryTextColor(context: Context?, dark: Boolean): Int {
        val ctx = checkNotNull(context) { "getSecondaryTextColor needs a context to resolve a color" }
        return if (dark) {
            ContextCompat.getColor(ctx, androidx.appcompat.R.color.secondary_text_default_material_light)
        } else ContextCompat.getColor(ctx, androidx.appcompat.R.color.secondary_text_default_material_dark)
    }

    @SuppressLint("PrivateResource")
    @JvmStatic
    @ColorInt
    fun getPrimaryDisabledTextColor(context: Context?, dark: Boolean): Int {
        val ctx =
            checkNotNull(context) { "getPrimaryDisabledTextColor needs a context to resolve a color" }
        return if (dark) {
            ContextCompat.getColor(ctx, androidx.appcompat.R.color.primary_text_disabled_material_light)
        } else ContextCompat.getColor(ctx, androidx.appcompat.R.color.primary_text_disabled_material_dark)
    }

    @SuppressLint("PrivateResource")
    @JvmStatic
    @ColorInt
    fun getSecondaryDisabledTextColor(context: Context?, dark: Boolean): Int {
        val ctx =
            checkNotNull(context) { "getSecondaryDisabledTextColor needs a context to resolve a color" }
        return if (dark) {
            ContextCompat.getColor(ctx, androidx.appcompat.R.color.secondary_text_disabled_material_light)
        } else ContextCompat.getColor(ctx, androidx.appcompat.R.color.secondary_text_disabled_material_dark)
    }
}
