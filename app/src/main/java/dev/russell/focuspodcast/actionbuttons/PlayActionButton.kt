package dev.russell.focuspodcast.actionbuttons

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.russell.focuspodcast.model.feed.FeedItem
import dev.russell.focuspodcast.R
import dev.russell.focuspodcast.model.feed.FeedMedia
import dev.russell.focuspodcast.core.pref.UsageStatistics
import dev.russell.focuspodcast.core.storage.DBTasks
import dev.russell.focuspodcast.core.util.NetworkUtils
import dev.russell.focuspodcast.core.util.playback.PlaybackServiceStarter
import dev.russell.focuspodcast.core.service.playback.PlaybackService
import dev.russell.focuspodcast.dialog.UseStreamConfirmDialog
import dev.russell.focuspodcast.model.playback.MediaType
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
        if (media.mediaType == MediaType.VIDEO) {
            activity.startActivity(PlaybackService.getPlayerActivityIntent(activity, media))
        }
    }
}
