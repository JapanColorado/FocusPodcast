package allen.town.podcast.actionbuttons

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import allen.town.podcast.model.feed.FeedItem
import allen.town.podcast.R
import allen.town.podcast.model.feed.FeedMedia
import allen.town.podcast.core.pref.UsageStatistics
import allen.town.podcast.core.storage.DBTasks
import allen.town.podcast.core.util.NetworkUtils
import allen.town.podcast.core.util.playback.PlaybackServiceStarter
import allen.town.podcast.core.service.playback.PlaybackService
import allen.town.podcast.dialog.UseStreamConfirmDialog
import allen.town.podcast.model.playback.MediaType
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
