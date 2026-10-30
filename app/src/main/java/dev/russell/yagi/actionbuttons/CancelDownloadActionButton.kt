package dev.russell.yagi.actionbuttons

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.russell.yagi.model.feed.FeedItem
import dev.russell.yagi.R
import dev.russell.yagi.core.pref.Prefs
import dev.russell.yagi.core.service.download.DownloadService
import dev.russell.yagi.core.storage.DBWriter
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