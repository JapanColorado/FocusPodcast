package dev.russell.yagi.actionbuttons

import android.content.Context
import android.content.DialogInterface
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.russell.yagi.theme.ThemeStore.Companion.accentColor
import dev.russell.yagi.model.feed.FeedItem
import dev.russell.yagi.R
import dev.russell.yagi.model.feed.FeedMedia
import dev.russell.yagi.core.dialog.ConfirmationDialog
import dev.russell.yagi.core.storage.DBWriter
import android.app.Activity

class DeleteActionButton(val item: FeedItem) : ItemActionButton {
    override fun getDrawableTintColor(context: Context?): Int {
        // -1 is the interface's "no tint" answer when there is no context to resolve one from.
        return if (context == null) -1 else accentColor(context)
    }

    @get:StringRes
    override val label: Int
        get() = R.string.delete_label

    @get:DrawableRes
    override val drawable: Int
        get() = R.drawable.ic_round_check_circle_outline_24

    override fun onClick(context: Activity?) {
        val activity = context ?: return
        val media: FeedMedia = item.getMedia() ?: return
        val dialog: ConfirmationDialog = object : ConfirmationDialog(
            activity,
            R.string.delete_label,
            R.string.confirm_delete_download_file
        ) {
            override fun onConfirmButtonPressed(clickedDialog: DialogInterface) {
                clickedDialog.dismiss()
                DBWriter.deleteFeedMediaOfItem(activity, media.id)
            }
        }
        dialog.createNewDialog().show()
    }

    override val isVisibility: Int
        get() = if (item.getMedia()?.isDownloaded() == true) View.VISIBLE else View.INVISIBLE
}