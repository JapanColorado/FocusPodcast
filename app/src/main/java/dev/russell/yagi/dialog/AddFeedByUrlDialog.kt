package dev.russell.yagi.dialog

import dev.russell.yagi.R
import dev.russell.yagi.activity.RssSearchActivity
import dev.russell.yagi.common.views.AccentMaterialDialog
import dev.russell.yagi.core.util.FeedUrlInput
import dev.russell.yagi.databinding.AddFeedByUrlDialogBinding
import dev.russell.yagi.util.ClipboardFeedUrl
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged

/**
 * Asks for a feed URL and opens its preview ([RssSearchActivity]), the same place a URL typed
 * into the online search ends up. The field starts with the clipboard's URL, if it holds one.
 */
object AddFeedByUrlDialog {

    @JvmStatic
    fun show(context: Context) {
        val binding = AddFeedByUrlDialogBinding.inflate(LayoutInflater.from(context))
        val clipboardUrl = ClipboardFeedUrl.read(context)
        if (clipboardUrl != null) {
            binding.urlEditText.setText(clipboardUrl)
            binding.urlEditText.setSelection(clipboardUrl.length)
        }
        val dialog = AccentMaterialDialog(context, R.style.MaterialAlertDialogTheme)
            .setTitle(R.string.add_podcast_by_url_label)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(R.string.cancel_label, null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()

        val submit = {
            val url = FeedUrlInput.normalize(binding.urlEditText.text?.toString())
            if (url == null) {
                binding.urlInputLayout.error = context.getString(R.string.invalid_feed_url_error)
            } else {
                val intent = Intent(context, RssSearchActivity::class.java)
                intent.putExtra(RssSearchActivity.ARG_FEEDURL, url)
                context.startActivity(intent)
                dialog.dismiss()
            }
        }
        // Set after show() so an invalid URL keeps the dialog open with the error under the field
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit() }
        binding.urlEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
        binding.urlEditText.doAfterTextChanged { binding.urlInputLayout.error = null }
        binding.urlEditText.requestFocus()
    }
}
