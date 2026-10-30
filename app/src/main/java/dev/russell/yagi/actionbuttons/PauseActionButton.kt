package dev.russell.yagi.actionbuttons

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.russell.yagi.core.util.IntentUtils.sendLocalBroadcast
import dev.russell.yagi.model.feed.FeedItem
import dev.russell.yagi.R
import dev.russell.yagi.model.feed.FeedMedia
import dev.russell.yagi.core.util.FeedItemUtil
import dev.russell.yagi.core.service.playback.PlaybackService
import android.app.Activity

class PauseActionButton(val item: FeedItem) : ItemActionButton {
    @get:StringRes
    override val label: Int
        get() = R.string.pause_label

    @get:DrawableRes
    override val drawable: Int
        get() = R.drawable.ic_pause

    override fun onClick(context: Activity?) {
        val activity = context ?: return
        val media: FeedMedia = item.getMedia() ?: return
        if (FeedItemUtil.isCurrentlyPlaying(media)) {
            sendLocalBroadcast(activity, PlaybackService.ACTION_PAUSE_PLAY_CURRENT_EPISODE)
        }
    }
}