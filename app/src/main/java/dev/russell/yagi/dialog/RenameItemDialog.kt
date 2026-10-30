package dev.russell.yagi.dialog

import dev.russell.yagi.common.views.AccentMaterialDialog
import dev.russell.yagi.R
import dev.russell.yagi.core.storage.DBWriter
import dev.russell.yagi.core.storage.NavDrawerData.*
import dev.russell.yagi.databinding.EditTextDialogBinding
import dev.russell.yagi.model.feed.Feed
import dev.russell.yagi.model.feed.FeedPreferences
import android.app.Activity
import android.content.DialogInterface
import android.view.View
import androidx.appcompat.app.AlertDialog
import java.lang.ref.WeakReference

class RenameItemDialog {
    private val activityRef: WeakReference<Activity>
    private var feed: Feed? = null
    private var drawerItem: DrawerItem? = null

    constructor(activity: Activity, feed: Feed?) {
        activityRef = WeakReference(activity)
        this.feed = feed
    }

    constructor(activity: Activity, drawerItem: DrawerItem?) {
        activityRef = WeakReference(activity)
        this.drawerItem = drawerItem
    }

    fun show() {
        val activity = activityRef.get() ?: return
        val feed = this.feed
        val drawerItem = this.drawerItem
        val content = View.inflate(activity, R.layout.edit_text_dialog, null)
        val alertViewBinding = EditTextDialogBinding.bind(content)
        val title: String? = feed?.title ?: drawerItem?.title
        alertViewBinding.urlEditText.setText(title)
        val dialog = AccentMaterialDialog(
            activity,
            R.style.MaterialAlertDialogTheme
        )
            .setView(content)
            .setTitle(if (feed != null) R.string.rename_feed_label else R.string.rename_tag_label)
            .setPositiveButton(android.R.string.ok) { d: DialogInterface?, input: Int ->
                val newTitle = alertViewBinding.urlEditText.text.toString()
                if (feed != null) {
                    feed.customTitle = newTitle
                    DBWriter.setFeedCustomTitle(feed)
                } else if (drawerItem != null) {
                    renameTag(newTitle, drawerItem)
                }
            }
            .setNeutralButton(dev.russell.yagi.core.R.string.reset, null)
            .setNegativeButton(dev.russell.yagi.core.R.string.cancel_label, null)
            .show()

        // To prevent cancelling the dialog on button click
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            .setOnClickListener { view: View? -> alertViewBinding.urlEditText.setText(title) }
    }

    private fun renameTag(title: String, drawerItem: DrawerItem) {
        if (DrawerItem.Type.TAG == drawerItem.type) {
            val feedPreferences: MutableList<FeedPreferences> = ArrayList()
            for (item in (drawerItem as TagDrawerItem).children) {
                feedPreferences.add((item as FeedDrawerItem).feed.preferences)
            }
            for (preferences in feedPreferences) {
                preferences.tags.remove(drawerItem.title)
                preferences.tags.add(title)
                DBWriter.setFeedPreferences(preferences)
            }
        }
    }
}