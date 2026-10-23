package dev.russell.focuspodcast.actionbuttons

import dev.russell.focuspodcast.core.feed.util.VideoModeUtils
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.russell.focuspodcast.model.feed.FeedItem
import dev.russell.focuspodcast.R
import dev.russell.focuspodcast.model.feed.FeedMedia
import dev.russell.focuspodcast.core.util.playback.PlaybackServiceStarter
import dev.russell.focuspodcast.core.service.playback.PlaybackService
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