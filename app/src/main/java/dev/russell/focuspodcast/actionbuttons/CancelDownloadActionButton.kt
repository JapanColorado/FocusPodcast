package dev.russell.focuspodcast.actionbuttons

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.russell.focuspodcast.model.feed.FeedItem
import dev.russell.focuspodcast.R
import dev.russell.focuspodcast.core.pref.Prefs
import dev.russell.focuspodcast.core.service.download.DownloadService
import dev.russell.focuspodcast.core.storage.DBWriter
import android.app.Activity

class CancelDownloadActionButton(val item: FeedItem) : ItemActionButton {
    @get:StringRes
    override val label: Int
        get() = R.string.cancel_download_label

    @get:DrawableRes
    override val drawable: Int
        get() = R.drawable.ic_cancel

    override fun onClick(context: Activity?) {
        val media = item.media ?: return
        DownloadService.cancel(context, media.download_url)
        if (Prefs.isEnableAutodownload) {
            item.disableAutoDownload()
            DBWriter.setFeedItem(item)
        }
    }
}