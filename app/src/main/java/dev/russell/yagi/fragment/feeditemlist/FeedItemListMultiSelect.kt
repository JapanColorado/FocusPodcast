package dev.russell.yagi.fragment.feeditemlist

import dev.russell.yagi.R
import dev.russell.yagi.activity.MainActivity
import dev.russell.yagi.adapter.EpisodeItemListAdapter
import dev.russell.yagi.adapter.MultiSelectAdapter
import dev.russell.yagi.adapter.MultiSelectAdapter.OnPrepareActionModeListener
import dev.russell.yagi.fragment.actions.EpisodeMultiSelectActionHandler
import dev.russell.yagi.model.feed.Feed
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.ActionMode

/**
 * Owns the multi-select action mode of [dev.russell.yagi.fragment.FeedItemlistFragment]'s episode
 * list: it wires the adapter's action-mode menu click listener to
 * [EpisodeMultiSelectActionHandler] (ending select mode afterwards) and hides the batch
 * download/delete entries for local feeds while the mode is being prepared. It keeps no state of
 * its own - the selection lives in the adapter it is attached to.
 */
internal class FeedItemListMultiSelect(
    private val activity: MainActivity,
    private val currentFeed: () -> Feed?
) {
    fun attachTo(adapter: EpisodeItemListAdapter) {
        adapter.setOnMenuItemClickListener(object : MultiSelectAdapter.OnMenuItemClickListener {
            override fun onMenuItemClick(item: MenuItem?) {
                if (item == null) {
                    return
                }
                EpisodeMultiSelectActionHandler(
                    activity,
                    adapter.selectedItems
                )
                    .handleAction(item.itemId)
                adapter.endSelectMode()
            }
        })
        adapter.setonPrepareActionListener(object : OnPrepareActionModeListener {
            override fun onPrepareActionMode(mode: ActionMode?, item: Menu?) {
                val feed = currentFeed() ?: return
                if (item != null && feed.isLocalFeed) {
                    item.findItem(R.id.download_batch).isVisible = false
                    item.findItem(R.id.delete_batch).isVisible = false
                }
            }
        })
    }
}
