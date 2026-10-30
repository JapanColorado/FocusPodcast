package dev.russell.yagi.actionbuttons

import dev.russell.yagi.common.util.TopSnackbarUtil.showSnack
import dev.russell.yagi.model.feed.FeedItem
import dev.russell.yagi.R
import dev.russell.yagi.model.feed.FeedMedia
import dev.russell.yagi.core.pref.UsageStatistics
import dev.russell.yagi.core.util.NetworkUtils
import dev.russell.yagi.core.service.download.DownloadRequestCreator
import dev.russell.yagi.core.storage.DBWriter
import android.view.View
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.russell.yagi.core.service.download.DownloadService
import android.app.Activity

class DownloadActionButton(val item: FeedItem) : ItemActionButton {
    @get:StringRes
    override val label: Int
        get() = R.string.download_label

    @get:DrawableRes
    override val drawable: Int
        get() = R.drawable.ic_round_arrow_circle_down_24
    override val isVisibility: Int
        get() = if (item.getFeed().isLocalFeed()) View.GONE else View.VISIBLE

    override fun onClick(context: Activity?) {
        val media: FeedMedia? = item.getMedia()
        if (media == null || shouldNotDownload(media)) {
            return
        }
        UsageStatistics.logAction(UsageStatistics.ACTION_DOWNLOAD)
        if (NetworkUtils.isEpisodeDownloadAllowed() || MobileDownloadHelper.userAllowedMobileDownloads()) {
            DownloadService.download(
                context,
                false,
                DownloadRequestCreator.create(item.getMedia()).build()
            )
        } else if (MobileDownloadHelper.userChoseAddToQueue() && !item.isTagged(FeedItem.TAG_QUEUE)) {
            DBWriter.addQueueItem(context, item)
            showSnack(context, R.string.added_to_queue_label, Toast.LENGTH_SHORT)
        } else {
            MobileDownloadHelper.confirmMobileDownload(context, item)
        }
    }

    private fun shouldNotDownload(media: FeedMedia): Boolean {
        val isDownloading = DownloadService.isDownloadingFile(media.download_url)
        return isDownloading || media.isDownloaded
    }
}