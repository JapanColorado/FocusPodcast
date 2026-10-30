package dev.russell.yagi.actionbuttons

import dev.russell.yagi.core.feed.util.VideoModeUtils
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.russell.yagi.model.feed.FeedItem
import dev.russell.yagi.R
import dev.russell.yagi.model.feed.FeedMedia
import dev.russell.yagi.core.pref.UsageStatistics
import dev.russell.yagi.core.storage.DBTasks
import dev.russell.yagi.core.util.NetworkUtils
import dev.russell.yagi.core.util.playback.PlaybackServiceStarter
import dev.russell.yagi.core.service.playback.PlaybackService
import dev.russell.yagi.dialog.UseStreamConfirmDialog
import android.app.Activity

class PlayActionButton(val item: FeedItem) : ItemActionButton {
    @get:StringRes
    override val label: Int
        get() = R.string.play_label

    @get:DrawableRes
    override val drawable: Int
        get() = R.drawable.ic_play_48dp

    override fun onClick(context: Activity?) {
        val activity = context ?: return
        val media: FeedMedia = item.getMedia() ?: return
        // Only repair media that claims to be downloaded; local folder feeds use content://
        // URIs that cannot be checked here.
        val isLocalFeed = item.feed?.isLocalFeed == true
        if (media.isDownloaded && !isLocalFeed && !media.fileExists()) {
            DBTasks.notifyMissingFeedMediaFile(activity, media)
            // fall through and stream instead, subject to the streaming preference
            UsageStatistics.logAction(UsageStatistics.ACTION_STREAM)
            if (!NetworkUtils.isStreamingAllowed()) {
                UseStreamConfirmDialog(activity, media).show()
                return
            }
        }
        PlaybackServiceStarter(activity, media)
            .callEvenIfRunning(true)
            .start()
        if (VideoModeUtils.shouldShowVideo(media)) {
            activity.startActivity(PlaybackService.getPlayerActivityIntent(activity, media))
        }
    }
}
