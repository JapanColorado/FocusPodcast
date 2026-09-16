/*
 * Copyright (c) 2019 Hemanth Savarala.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by
 *  the Free Software Foundation either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */
package allen.town.focus_common.common.prefs.supportv7

import android.content.Context
import android.util.AttributeSet
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.preference.MultiSelectListPreference
import code.name.monkey.appthemehelper.ThemeStore

class ATEMultiListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = -1,
    defStyleRes: Int = -1
) : MultiSelectListPreference(context, attrs, defStyleAttr, defStyleRes) {

    open fun getSelectedItemsList(): BooleanArray {
        return super.getSelectedItems()
    }

    init {
        if (summary == null || summary.toString().trim { it <= ' ' }.isEmpty()) {
        }
        icon?.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
            ThemeStore.accentColor(context), BlendModeCompat.SRC_IN
        )
    }
}