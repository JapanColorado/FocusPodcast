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
import androidx.preference.Preference
import code.name.monkey.appthemehelper.R
import code.name.monkey.appthemehelper.ThemeStore

class ATEPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    init {
        val attributes =
            context.obtainStyledAttributes(attrs, R.styleable.ATEPreference, 0, 0)

        val donotAccentIcon = attributes.getBoolean(R.styleable.ATEPreference_do_not_accent_icon,false)

        if(!donotAccentIcon){
            icon?.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                ThemeStore.accentColor(context), BlendModeCompat.SRC_IN
            )
        }

    }
}