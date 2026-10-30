package dev.russell.yagi.util

import dev.russell.yagi.core.util.FeedUrlInput
import android.content.ClipboardManager
import android.content.Context

/** Reads a feed URL from the clipboard, for pre-filling the search box and the add-by-URL dialog. */
object ClipboardFeedUrl {

    /** The clipboard's first item as a cleaned feed URL, or null if it holds no URL. */
    @JvmStatic
    fun read(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return null
        val clipData = clipboard.primaryClip ?: return null
        if (clipData.itemCount == 0) {
            return null
        }
        val text = clipData.getItemAt(0).text ?: return null
        return FeedUrlInput.normalize(text.toString())
    }
}
