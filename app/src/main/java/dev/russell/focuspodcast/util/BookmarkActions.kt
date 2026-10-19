package dev.russell.focuspodcast.util

import dev.russell.focuspodcast.R
import dev.russell.focuspodcast.core.storage.DBWriter
import dev.russell.focuspodcast.core.util.BookmarkUtils
import dev.russell.focuspodcast.core.util.ShareUtils
import dev.russell.focuspodcast.dialog.BookmarkNoteDialog
import dev.russell.focuspodcast.model.feed.Bookmark
import dev.russell.focuspodcast.model.feed.FeedItem
import android.content.Context
import android.view.View
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.snackbar.Snackbar

/**
 * What a bookmark row's overflow menu does, shared by the player's per-episode tab and the
 * Bookmarks screen: edit the note, share, and delete with an undo.
 */
object BookmarkActions {

    /** Where a list shows the "Bookmark deleted" snackbar; null when it cannot show one now. */
    fun interface SnackbarHost {
        fun showSnackbar(text: CharSequence): Snackbar?
    }

    /**
     * Shows the overflow menu of [bookmark] at [anchor]. [item] is the bookmark's episode, used
     * for sharing; without it Share is left out.
     */
    fun showMenu(anchor: View, bookmark: Bookmark, item: FeedItem?, snackbarHost: SnackbarHost) {
        val context = anchor.context
        val popup = PopupMenu(context, anchor)
        popup.inflate(R.menu.bookmark_item)
        popup.menu.findItem(R.id.bookmark_edit_note)
            .setTitle(if (bookmark.hasNote()) R.string.bookmark_edit_note else R.string.bookmark_add_note)
        popup.menu.findItem(R.id.bookmark_share).isVisible = item != null
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.bookmark_edit_note -> {
                    editNote(context, bookmark)
                    true
                }
                R.id.bookmark_share -> {
                    item?.let { share(context, bookmark, it) }
                    true
                }
                R.id.bookmark_delete -> {
                    delete(context, bookmark, snackbarHost)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    fun editNote(context: Context, bookmark: Bookmark) {
        BookmarkNoteDialog.show(
            context,
            if (bookmark.hasNote()) R.string.bookmark_edit_note else R.string.bookmark_add_note,
            bookmark.positionMs, bookmark.note
        ) { note -> DBWriter.setBookmarkNote(bookmark, note) }
    }

    /** Shares "Episode — Podcast at 12:34", the note and the episode's (or podcast's) website. */
    fun share(context: Context, bookmark: Bookmark, item: FeedItem) {
        val link = item.link?.takeIf { it.isNotBlank() } ?: item.feed?.link?.takeIf { it.isNotBlank() }
        val text = BookmarkUtils.buildShareText(
            context.getString(R.string.bookmark_share_headline),
            item.title, item.feed?.title, bookmark.positionMs, bookmark.note, link
        )
        ShareUtils.shareLink(context, text)
    }

    /** Deletes [bookmark] and offers to put it back. */
    fun delete(context: Context, bookmark: Bookmark, snackbarHost: SnackbarHost) {
        DBWriter.deleteBookmark(bookmark)
        snackbarHost.showSnackbar(context.getString(R.string.bookmark_deleted))
            ?.setAction(R.string.undo) { DBWriter.addBookmark(bookmark) }
    }
}
