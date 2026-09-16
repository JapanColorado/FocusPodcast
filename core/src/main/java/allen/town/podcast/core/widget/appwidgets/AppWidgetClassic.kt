package allen.town.podcast.core.widget.appwidgets

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.RemoteViews
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import allen.town.podcast.core.R
import allen.town.podcast.core.feed.util.ImageResourceUtils
import allen.town.podcast.core.glide.ApGlideSettings
import allen.town.podcast.core.widget.WidgetUpdater
import allen.town.podcast.core.widget.base.BaseAppWidget
import java.util.concurrent.TimeUnit

class AppWidgetClassic : BaseAppWidget() {
    val TAG = "BaseAppWidget"

    override fun getLayout(): Int {
        return R.layout.app_widget_classic
    }

    // detekt: a widget update runs in the home screen's process and must never throw.
    // Glide's blocking submit() can fail with anything up to OutOfMemoryError, and the
    // fallback path below is exactly what those failures exist for.
    @Suppress("TooGenericExceptionCaught")
    override fun processRemoteViewIfNeeded(
        context: Context,
        remoteViews: RemoteViews,
        widgetState: WidgetUpdater.WidgetState,
        appWidgetIds: IntArray,
        isCreated: Boolean
    ) {
        if (widgetState.media != null) {
            var icon: Bitmap?
            val iconSize = context.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)

            try {
                icon = Glide.with(context)
                    .asBitmap()
                    .load(widgetState.media.imageLocation)
                    .apply(
                        RequestOptions.diskCacheStrategyOf(ApGlideSettings.AP_DISK_CACHE_STRATEGY)
                            .transform(
                                RoundedCorners((8 * context.resources.displayMetrics.density).toInt())
                            )
                    )
                    .submit(iconSize, iconSize)[500, TimeUnit.MILLISECONDS]
                remoteViews.setImageViewBitmap(R.id.imgvCover, icon)
            } catch (ignored: Throwable) {
                // The primary cover could not be loaded; the fallback below reports for both.
                try {
                    icon = Glide.with(context)
                        .asBitmap()
                        .load(ImageResourceUtils.getFallbackImageLocation(widgetState.media))
                        .apply(
                            RequestOptions.diskCacheStrategyOf(ApGlideSettings.AP_DISK_CACHE_STRATEGY)
                                .transform(
                                    RoundedCorners((8 * context.resources.displayMetrics.density).toInt())
                                )
                        )
                        .submit(iconSize, iconSize)[500, TimeUnit.MILLISECONDS]
                    remoteViews.setImageViewBitmap(R.id.imgvCover, icon)
                } catch (tr2: Throwable) {
                    Log.e(TAG, "Error loading the media icon for the widget", tr2)
                    remoteViews.setImageViewResource(R.id.imgvCover, R.mipmap.ic_launcher_round)
                }
            }
        }
        pushUpdate(context, appWidgetIds, remoteViews)
    }

    companion object {

        const val NAME = "app_widget_classic"

        private var mInstance: AppWidgetClassic? = null

        @JvmStatic
        val instance: AppWidgetClassic
            @Synchronized get() = mInstance ?: AppWidgetClassic().also { mInstance = it }
    }
}