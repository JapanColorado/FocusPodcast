package dev.russell.yagi.actionbuttons

import dev.russell.yagi.core.feed.util.VideoModeUtils
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.russell.yagi.model.feed.FeedItem
import dev.russell.yagi.R
import dev.russell.yagi.model.feed.FeedMedia
import dev.russell.yagi.core.util.playback.PlaybackServiceStarter
import dev.russell.yagi.core.service.playback.PlaybackService
import android.app.Activity

class PlayLocalActionButton(val item: FeedItem) : ItemActionButton {
    @get:StringRes
    override val label: Int
        get() = R.string.play_label

    @get:DrawableRes
    override val drawable: Int
        get() = R.drawable.ic_play_24dp

    override fun onClick(context: Activity?) {
        val activity = context ?: return
        val media: FeedMedia = item.getMedia() ?: return
        PlaybackServiceStarter(activity, media)
            .callEvenIfRunning(true)
            .start()
        if (VideoModeUtils.shouldShowVideo(media)) {
            activity.startActivity(PlaybackService.getPlayerActivityIntent(activity, media))
        }
    }
}