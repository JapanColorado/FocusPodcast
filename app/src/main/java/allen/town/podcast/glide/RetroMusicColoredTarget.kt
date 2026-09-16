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
package allen.town.podcast.glide

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.ImageView
import allen.town.focus_common.extensions.colorControlNormal
import com.bumptech.glide.request.transition.Transition
import allen.town.podcast.MyApp
import allen.town.podcast.core.glide.palette.BitmapPaletteTarget
import allen.town.podcast.core.glide.palette.BitmapPaletteWrapper
import allen.town.podcast.util.MediaNotificationProcessor
import io.reactivex.disposables.Disposable

abstract class RetroMusicColoredTarget(view: ImageView) : BitmapPaletteTarget(view) {

    private var paletteDisposable: Disposable? = null

    protected val defaultFooterColor: Int
        get() = getView().context.colorControlNormal()

    abstract fun onColorReady(colors: MediaNotificationProcessor,bitmap:Bitmap?)

    override fun onLoadFailed(errorDrawable: Drawable?) {
        super.onLoadFailed(errorDrawable)
        onColorReady(MediaNotificationProcessor.errorColor(MyApp.instance),null)
    }

    override fun onResourceReady(
        resource: BitmapPaletteWrapper,
        transition: Transition<in BitmapPaletteWrapper>?
    ) {
        super.onResourceReady(resource, transition)
        paletteDisposable?.dispose()
        paletteDisposable = MediaNotificationProcessor(MyApp.instance).getPaletteAsync({
            onColorReady(it, resource.bitmap)
        }, resource.bitmap)
    }

    override fun onLoadCleared(placeholder: Drawable?) {
        // the target is recycled with the row; a palette still in flight would call back into a
        // view that now shows a different episode
        paletteDisposable?.dispose()
        paletteDisposable = null
        super.onLoadCleared(placeholder)
    }
}
