/*
 * Copyright (c) 2020 Hemanth Savarla.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
@file:JvmName("FragmentExtensionsUtils")
package code.name.monkey.retromusic.extensions

import allen.town.focus_common.extensions.applyToolbar
import allen.town.focus_common.util.BasePreferenceUtil
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.annotation.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.appbar.MaterialToolbar

fun Fragment.getIntRes(@IntegerRes int: Int): Int {
    return resources.getInteger(int)
}

fun Context.getIntRes(@IntegerRes int: Int): Int {
    return resources.getInteger(int)
}

val Context.generalThemeValue
    get() = BasePreferenceUtil.getGeneralThemeValue(isSystemDarkModeEnabled())

fun Context.isSystemDarkModeEnabled(): Boolean {
    val isDarkModeEnabled =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    return isDarkModeEnabled
}

inline fun <reified T : Any> Fragment.extra(key: String, default: T? = null) = lazy {
    val value = arguments?.get(key)
    if (value is T) value else default
}

inline fun <reified T : Any> Fragment.extraNotNull(key: String, default: T? = null) = lazy {
    val value = arguments?.get(key)
    requireNotNull(if (value is T) value else default) { key }
}


val FragmentManager.currentNavigationFragment: Fragment?
    get() = primaryNavigationFragment?.childFragmentManager?.fragments?.first()


@Suppress("UNCHECKED_CAST")
fun <T> AppCompatActivity.whichFragment(@IdRes id: Int): T {
    return supportFragmentManager.findFragmentById(id) as T
}

@Suppress("UNCHECKED_CAST")
fun <T> Fragment.whichFragment(@IdRes id: Int): T {
    return childFragmentManager.findFragmentById(id) as T
}

fun Fragment.showToast(@StringRes stringRes: Int) {
    showToast(getString(stringRes))
}

fun Fragment.showToast(message: String) {
    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
}

fun Context.getDrawableCompat(@DrawableRes drawableRes: Int): Drawable {
    return AppCompatResources.getDrawable(this, drawableRes)!!
}

fun Fragment.getDrawableCompat(@DrawableRes drawableRes: Int): Drawable {
    return AppCompatResources.getDrawable(requireContext(), drawableRes)!!
}

fun Fragment.applyToolbar(toolbar: MaterialToolbar) {
    (requireActivity() as AppCompatActivity).applyToolbar(toolbar)
}

fun Fragment.dip(@DimenRes id: Int): Int {
    return resources.getDimensionPixelSize(id)
}