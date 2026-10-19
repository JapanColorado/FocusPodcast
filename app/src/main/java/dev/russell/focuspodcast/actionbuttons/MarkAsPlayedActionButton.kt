package dev.russell.focuspodcast.actionbuttons

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.russell.focuspodcast.model.feed.FeedItem
import dev.russell.focuspodcast.R
import dev.russell.focuspodcast.core.storage.DBWriter
import android.app.Activity

class MarkAsPlayedActionButton(val item: FeedItem) : ItemActionButton {
    @get:StringRes
    override val label: Int
        get() = if (item.hasMedia()) R.string.mark_read_label else R.string.mark_read_no_media_label

    @get:DrawableRes
    override val drawable: Int
        get() = R.drawable.ic_round_check_24

    override fun onClick(context: Activity?) {
        if (!item.isPlayed()) {
            DBWriter.markItemPlayed(item, FeedItem.PLAYED, true)
        }
    }

    override val isVisibility: Int
        get() = if (item.isPlayed()) View.INVISIBLE else View.VISIBLE
}