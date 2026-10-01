package allen.town.podcast.dialog

import allen.town.podcast.R
import allen.town.podcast.activity.MainActivity
import allen.town.podcast.common.util.TopSnackbarUtil.showSnack
import allen.town.podcast.common.views.AccentMaterialDialog
import allen.town.podcast.core.storage.DBWriter
import allen.town.podcast.core.util.DownloadErrorLabel
import allen.town.podcast.model.download.DownloadStatus
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.text.format.DateUtils
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar

/**
 * Shows everything the download log knows about one entry: the title, the readable reason, when it
 * happened and the full explanation ([DownloadStatus.getReasonDetailed]), which the list row has
 * no room for. It can be copied, and a non-fatal feed warning (one with a
 * [DownloadStatus.getWarningKey]) can be hidden for good. Real failures can never be hidden.
 */
object DownloadStatusDetailsDialog {

    fun show(activity: Activity, status: DownloadStatus) {
        val title = status.title ?: activity.getString(R.string.download_log_title_unknown)
        val reason = activity.getString(DownloadErrorLabel.from(status.reason))
        val time = DateUtils.formatDateTime(
            activity, status.completionDate.time,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME
        )
        val message = buildString {
            append(reason)
            append("\n")
            append(time)
            val detailed = status.reasonDetailed
            if (!detailed.isNullOrBlank()) {
                append("\n\n")
                append(detailed)
            }
        }
        val builder = AccentMaterialDialog(activity, R.style.MaterialAlertDialogTheme)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.close_label, null)
        // Never more than two buttons: three do not fit on one row, and a stacked button bar
        // under a long message (duplicate-episode warnings) is squeezed into its own scrolling
        // strip. A warning trades the Copy button for "Don't show again"; its text stays
        // selectable below, so it can still be copied.
        if (status.warningKey != null) {
            builder.setNegativeButton(R.string.warning_dont_show_again) { _, _ ->
                dismissForGood(activity, status)
            }
        } else {
            builder.setNeutralButton(android.R.string.copy) { _, _ ->
                copyToClipboard(activity, "$title\n$message")
            }
        }
        val dialog = builder.show()
        dialog.findViewById<TextView>(android.R.id.message)?.setTextIsSelectable(true)
    }

    private fun copyToClipboard(activity: Activity, text: String) {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.download_error_details), text))
        // Android 13 and later confirm a copy themselves.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            showSnack(activity, R.string.copied_to_clipboard, Toast.LENGTH_SHORT)
        }
    }

    private fun dismissForGood(activity: Activity, status: DownloadStatus) {
        DBWriter.dismissFeedWarning(status)
        val mainActivity = activity as? MainActivity
        if (mainActivity == null) {
            showSnack(activity, R.string.warning_dismissed, Toast.LENGTH_SHORT)
            return
        }
        mainActivity.showSnackbarAbovePlayer(R.string.warning_dismissed, Snackbar.LENGTH_LONG)
            .setAction(activity.getString(R.string.undo)) { DBWriter.undoDismissFeedWarning(status) }
    }
}
