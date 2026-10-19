package dev.russell.focuspodcast.actionbuttons

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.russell.focuspodcast.core.util.IntentUtils.openInBrowser
import dev.russell.focuspodcast.model.feed.FeedItem
import dev.russell.focuspodcast.R
import android.app.Activity

class VisitWebsiteActionButton(val item: FeedItem) : ItemActionButton {
    @get:StringRes
    override val label: Int
        get() = R.string.visit_website_label

    @get:DrawableRes
    override val drawable: Int
        get() = R.drawable.ic_web

    override fun onClick(context: Activity?) {
        val activity = context ?: return
        openInBrowser(activity, item.getLink())
    }

    override val isVisibility: Int
        get() = View.GONE
}