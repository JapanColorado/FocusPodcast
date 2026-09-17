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
package allen.town.podcast.core.widget.base

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import allen.town.podcast.theme.util.VersionUtils
import allen.town.podcast.core.R
import allen.town.podcast.core.widget.WidgetUpdater
import allen.town.podcast.core.widget.WidgetUpdaterWorker
import allen.town.podcast.model.playback.MediaType
import allen.town.podcast.playback.base.PlayerStatus
import allen.town.podcast.ui.startintent.MainActivityStarter
import allen.town.podcast.ui.startintent.VideoPlayerActivityStarter
import java.util.*

abstract class BaseAppWidget : AppWidgetProvider() {

    private val TAG = "PlayerWidget"

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "onReceive")
        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "Widget enabled")
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray?
    ) {
        Log.d(
            TAG, "onUpdate() called with: " + "context = [" + context + "], appWidgetManager = ["
                    + appWidgetManager + "], appWidgetIds = [" + Arrays.toString(appWidgetIds) + "]"
        )
        WidgetUpdaterWorker.enqueueWork(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "Widget disabled")
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        Log.d(TAG, "OnDeleted")
        super.onDeleted(context, appWidgetIds)
    }


    protected fun pushUpdate(
        context: Context,
        appWidgetId: IntArray?,
        views: RemoteViews
    ) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        if (appWidgetId != null) {
            appWidgetManager.updateAppWidget(appWidgetId, views)
        } else {
            Log.v(TAG, "pushUpdate appWidgetId is null")
        }
    }

    protected fun buildPendingIntent(
        context: Context,
        action: String,
        serviceName: ComponentName
    ): PendingIntent {
        val intent = Intent(action)
        intent.component = serviceName
        return if (VersionUtils.hasOreo()) {
            PendingIntent.getForegroundService(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getService(
                context, 0, intent, if (VersionUtils.hasMarshmallow())
                    PendingIntent.FLAG_IMMUTABLE
                else 0
            )
        }
    }

    abstract fun getLayout(): Int

    abstract fun processRemoteViewIfNeeded(
        context: Context,
        remoteViews: RemoteViews,
        widgetState: WidgetUpdater.WidgetState,
        appWidgetIds: IntArray,
        isCreated: Boolean
    )

    open fun getPlayBitmap(context: Context): Bitmap? {
        return null
    }

    open fun getPauseBitmap(context: Context): Bitmap? {
        return null
    }

    open fun getForwardBitmap(context: Context): Bitmap? {
        return null
    }

    open fun getRewindBitmap(context: Context): Bitmap? {
        return null
    }

    open fun getNextBitmap(context: Context): Bitmap? {
        return null
    }

    fun start(context: Context, widgetState: WidgetUpdater.WidgetState) {
        val appWidgetIds = getWidgetIds(context)
        if (appWidgetIds.isEmpty()) {
            Log.v(TAG, "${javaClass} appWidgetIds is null")
            return
        }

        val startMediaPlayer =
            if (widgetState.media != null && widgetState.media.mediaType == MediaType.VIDEO) {
                VideoPlayerActivityStarter(context).pendingIntent
            } else {
                MainActivityStarter(context).withOpenPlayer().pendingIntent
            }

        val views = RemoteViews(context.packageName, getLayout())

        if (widgetState.media != null) {
            views.setOnClickPendingIntent(R.id.layout_left, startMediaPlayer)
            views.setOnClickPendingIntent(R.id.imgvCover, startMediaPlayer)
            views.setTextViewText(R.id.txtvTitle, widgetState.media.episodeTitle)
            val progressString = WidgetUpdater.getProgressString(
                widgetState.position,
                widgetState.duration, widgetState.playbackSpeed
            )
            if (progressString != null) {
                views.setViewVisibility(R.id.txtvProgress, View.VISIBLE)
                views.setTextViewText(R.id.txtvProgress, progressString)
            }
            if (widgetState.status == PlayerStatus.PLAYING) {
                if (getPauseBitmap(context) == null) {
                    views.setImageViewResource(R.id.butPlayExtended, R.drawable.ic_widget_pause)
                } else {
                    views.setImageViewBitmap(R.id.butPlayExtended, getPauseBitmap(context))
                }

                views.setContentDescription(
                    R.id.butPlayExtended,
                    context.getString(R.string.pause_label)
                )
            } else {
                if (getPlayBitmap(context) == null) {
                    views.setImageViewResource(R.id.butPlayExtended, R.drawable.ic_widget_play)
                } else {
                    views.setImageViewBitmap(R.id.butPlayExtended, getPlayBitmap(context))
                }
                views.setContentDescription(
                    R.id.butPlayExtended,
                    context.getString(R.string.play_label)
                )
            }

            if (getRewindBitmap(context) != null) {
                views.setImageViewBitmap(R.id.butRew, getRewindBitmap(context))
            }

            if (getForwardBitmap(context) != null) {
                views.setImageViewBitmap(R.id.butFastForward, getForwardBitmap(context))
            }

            if (getNextBitmap(context) != null) {
                views.setImageViewBitmap(R.id.butSkip, getNextBitmap(context))
            }

            views.setOnClickPendingIntent(
                R.id.butPlayExtended,
                WidgetUpdater.createMediaButtonIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            )
            views.setOnClickPendingIntent(
                R.id.butRew,
                WidgetUpdater.createMediaButtonIntent(context, KeyEvent.KEYCODE_MEDIA_REWIND)
            )
            views.setOnClickPendingIntent(
                R.id.butFastForward,
                WidgetUpdater.createMediaButtonIntent(context, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            )
            views.setOnClickPendingIntent(
                R.id.butSkip,
                WidgetUpdater.createMediaButtonIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            )
        } else {
            // start the app if they click anything
            views.setOnClickPendingIntent(R.id.layout_left, startMediaPlayer)
            views.setOnClickPendingIntent(
                R.id.butPlayExtended,
                WidgetUpdater.createMediaButtonIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            )
            views.setViewVisibility(R.id.txtvProgress, View.GONE)
            views.setTextViewText(R.id.txtvTitle, context.getText(R.string.no_media_playing_label))
            /*views.setImageViewResource(R.id.imgvCover, R.drawable.default_audio_art)

            views.setImageViewResource(R.id.butPlayExtended, R.drawable.ic_widget_play)
            views.setImageViewResource(R.id.butRew, R.drawable.ic_widget_fast_rewind)
            views.setImageViewResource(R.id.butFastForward, R.drawable.ic_widget_fast_forward)
            views.setImageViewResource(R.id.butSkip, R.drawable.ic_widget_skip)*/
        }


        processRemoteViewIfNeeded(context, views, widgetState, appWidgetIds, true)



    }

    /**
     * Check against [AppWidgetManager] if there are any instances of this widget.
     */
    private fun getWidgetIds(context: Context): IntArray {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        return appWidgetManager.getAppWidgetIds(
            ComponentName(
                context, javaClass
            )
        )
    }


    fun getAlbumArtDrawable(context: Context, bitmap: Bitmap?): Drawable {
        return if (bitmap == null) {
            checkNotNull(ContextCompat.getDrawable(context, R.drawable.default_audio_art)) {
                "missing drawable R.drawable.default_audio_art"
            }
        } else {
            BitmapDrawable(context.resources, bitmap)
        }
    }


    fun createRoundedBitmap(
        drawable: Drawable?,
        width: Int,
        height: Int,
        tl: Float,
        tr: Float,
        bl: Float,
        br: Float
    ): Bitmap? {
        if (drawable == null) {
            return null
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(c)

        val rounded = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(rounded)
        val paint = Paint()
        paint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        paint.isAntiAlias = true
        canvas.drawPath(
            composeRoundedRectPath(
                RectF(0f, 0f, width.toFloat(), height.toFloat()), tl, tr, bl, br
            ), paint
        )

        return rounded
    }

    fun createBitmap(drawable: Drawable, sizeMultiplier: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(
            (drawable.intrinsicWidth * sizeMultiplier).toInt(),
            (drawable.intrinsicHeight * sizeMultiplier).toInt(),
            Bitmap.Config.ARGB_8888
        )
        val c = Canvas(bitmap)
        drawable.setBounds(0, 0, c.width, c.height)
        drawable.draw(c)
        return bitmap
    }

    fun composeRoundedRectPath(
        rect: RectF,
        tl: Float,
        tr: Float,
        bl: Float,
        br: Float
    ): Path {
        val path = Path()
        path.moveTo(rect.left + tl, rect.top)
        path.lineTo(rect.right - tr, rect.top)
        path.quadTo(rect.right, rect.top, rect.right, rect.top + tr)
        path.lineTo(rect.right, rect.bottom - br)
        path.quadTo(rect.right, rect.bottom, rect.right - br, rect.bottom)
        path.lineTo(rect.left + bl, rect.bottom)
        path.quadTo(rect.left, rect.bottom, rect.left, rect.bottom - bl)
        path.lineTo(rect.left, rect.top + tl)
        path.quadTo(rect.left, rect.top, rect.left + tl, rect.top)
        path.close()

        return path
    }
}
