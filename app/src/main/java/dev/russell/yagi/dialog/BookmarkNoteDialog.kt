package dev.russell.yagi.dialog

import dev.russell.yagi.R
import dev.russell.yagi.common.views.AccentMaterialDialog
import dev.russell.yagi.core.util.BookmarkUtils
import dev.russell.yagi.databinding.BookmarkNoteDialogBinding
import android.content.Context
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.annotation.StringRes

/**
 * The small dialog that takes a bookmark's note: used to add a bookmark with a note (long-press
 * on the player's bookmark button) and to add or edit the note of an existing one. It shows the
 * bookmark's position as its message and hands the entered text, or null when it was left
 * blank, to [OnNoteSaved]; it writes nothing itself.
 */
object BookmarkNoteDialog {

    fun interface OnNoteSaved {
        fun onNoteSaved(note: String?)
    }

    @JvmStatic
    fun show(
        context: Context,
        @StringRes title: Int,
        positionMs: Long,
        initialNote: String?,
        onSaved: OnNoteSaved
    ) {
        val binding = BookmarkNoteDialogBinding.inflate(LayoutInflater.from(context))
        binding.bookmarkNote.setText(initialNote.orEmpty())
        binding.bookmarkNote.setSelection(binding.bookmarkNote.text?.length ?: 0)
        val dialog = AccentMaterialDialog(context, R.style.MaterialAlertDialogTheme)
            .setTitle(title)
            .setMessage(context.getString(R.string.bookmark_at_position,
                BookmarkUtils.formatPosition(positionMs)))
            .setView(binding.root)
            .setPositiveButton(R.string.save_label) { _, _ ->
                val note = binding.bookmarkNote.text?.toString()?.trim()
                onSaved.onNoteSaved(if (note.isNullOrEmpty()) null else note)
            }
            .setNegativeButton(R.string.cancel_label, null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        binding.bookmarkNote.requestFocus()
    }
}
