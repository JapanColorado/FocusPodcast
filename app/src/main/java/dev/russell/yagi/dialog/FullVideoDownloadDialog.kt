package dev.russell.yagi.dialog

import android.content.Context
import android.text.format.Formatter
import dev.russell.yagi.R
import dev.russell.yagi.common.views.AccentMaterialDialog
import dev.russell.yagi.core.service.download.DownloadRequestCreator
import dev.russell.yagi.core.service.download.DownloadService
import dev.russell.yagi.core.util.NetworkUtils
import dev.russell.yagi.model.feed.FeedMedia

/**
 * Offers to download the whole video when its audio could not be downloaded on its own (an
 * audio-only download that failed with `ERROR_AUDIO_ONLY_UNSUPPORTED`).
 *
 * @param fullSize the whole file's size in bytes, or a value <= 0 if unknown
 */
class FullVideoDownloadDialog(
    private val context: Context,
    private val media: FeedMedia,
    private val fullSize: Long,
) {
    fun show() {
        val title = media.getEpisodeTitle()
        var message = if (fullSize > 0) {
            context.getString(R.string.full_video_download_message,
                title, Formatter.formatShortFileSize(context, fullSize))
        } else {
            context.getString(R.string.full_video_download_message_unknown_size, title)
        }
        if (!NetworkUtils.isEpisodeDownloadAllowed()) {
            // Agreeing here stands in for the usual mobile-data download prompt.
            message += "\n\n" + context.getString(R.string.full_video_download_metered_note)
        }
        AccentMaterialDialog(context, R.style.MaterialAlertDialogTheme)
            .setTitle(R.string.full_video_download_title)
            .setMessage(message)
            .setPositiveButton(R.string.full_video_download_confirm) { _, _ ->
                DownloadService.download(context, false,
                    DownloadRequestCreator.createFullDownload(media).build())
            }
            .setNegativeButton(R.string.cancel_label, null)
            .show()
    }
}
