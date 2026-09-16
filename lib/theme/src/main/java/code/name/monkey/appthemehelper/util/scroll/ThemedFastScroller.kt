/*
 * Copyright (c) 2020 Hemanth Savarala.
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
package code.name.monkey.appthemehelper.util.scroll

import android.view.ViewGroup
import code.name.monkey.appthemehelper.ThemeStore.Companion.accentColor
import code.name.monkey.appthemehelper.util.ColorUtil
import code.name.monkey.appthemehelper.util.ColorUtil.isColorLight
import code.name.monkey.appthemehelper.util.MaterialValueHelper.getPrimaryTextColor
import code.name.monkey.appthemehelper.util.TintHelper
import code.name.monkey.appthemehelper.view.PopupBackground
import me.zhanghai.android.fastscroll.*

object ThemedFastScroller {
    @JvmStatic
    fun create(view: ViewGroup): FastScroller {
        val context = view.context
        val color = ColorUtil.withAlpha(accentColor(context), 0.8F)
        val textColor = getPrimaryTextColor(context, isColorLight(color))
        val fastScrollerBuilder = FastScrollerBuilder(view)

        fastScrollerBuilder.useMd2Style()
        fastScrollerBuilder.setPopupStyle { popupText ->
            PopupStyles.MD2.accept(popupText)
            popupText.background = PopupBackground(context, color)
            popupText.setTextColor(textColor)
        }

        fastScrollerBuilder.setThumbDrawable(
            TintHelper.createTintedDrawable(
                context,
                R.drawable.afs_md2_thumb,
                color
            )
        )

        fastScrollerBuilder.setAnimationHelper(PodcastScrollerAnimationHelper(view))
        val fastScroller = fastScrollerBuilder.build()

        return fastScroller
    }
}