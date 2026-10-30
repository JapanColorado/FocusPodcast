package dev.russell.yagi.dialog

import dev.russell.yagi.common.views.AccentMaterialDialog
import dev.russell.yagi.R
import dev.russell.yagi.core.pref.Prefs
import dev.russell.yagi.core.util.playback.PlaybackServiceStarter
import dev.russell.yagi.model.playback.Playable
import android.content.Context
import android.content.DialogInterface

/**
 * Asks before streaming on a metered network. [onStream] replaces the default action (start
 * [playable] from its stream), e.g. to switch an episode that is already playing to its video.
 */
class UseStreamConfirmDialog(
    private val context: Context,
    private val playable: Playable,
    private val onStream: (() -> Unit)? = null,
) {
    fun show() {
        AccentMaterialDialog(
            context,
            R.style.MaterialAlertDialogTheme
        )
            .setTitle(R.string.stream_label)
            .setMessage(R.string.confirm_mobile_streaming_notification_message)
            .setPositiveButton(R.string.confirm_mobile_streaming_button_once) { dialog: DialogInterface?, which: Int -> stream() }
            .setNegativeButton(R.string.confirm_mobile_streaming_button_always) { dialog: DialogInterface?, which: Int ->
                Prefs.isAllowMobileStreaming = true
                stream()
            }
            .setNeutralButton(R.string.cancel_label, null)
            .show()
    }

    private fun stream() {
        val action = onStream
        if (action != null) {
            action()
            return
        }
        PlaybackServiceStarter(context, playable)
            .callEvenIfRunning(true)
            .shouldStreamThisTime(true)
            .start()
    }
}