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
package code.name.monkey.appthemehelper.shortcut

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Bundle

@TargetApi(Build.VERSION_CODES.N_MR1)
abstract class BaseShortcutType(var context: Context) {

    abstract val shortcutInfo: ShortcutInfo

    /**
     * Creates an Intent that will launch MainActivtiy and immediately play {@param songs} in either shuffle or normal mode
     *
     * @param shortcutType Describes the type of shortcut to create (ShuffleAll, TopTracks, custom playlist, etc.)
     * @return
     */
    fun <T> getPlaySongsIntent(clazz: Class<T>?, name: String?, value: String?): Intent {
        val intent = Intent(context, clazz)
        intent.action = Intent.ACTION_VIEW
        val b = Bundle()
        name?.run {
            b.putString(name, value)
        }
        intent.putExtras(b)
        return intent
    }


    companion object {
        const val ID_PREFIX = "allen.town.focus.podcast.appshortcuts.id."
        val id: String
            get() = ID_PREFIX + "invalid"
    }
}
