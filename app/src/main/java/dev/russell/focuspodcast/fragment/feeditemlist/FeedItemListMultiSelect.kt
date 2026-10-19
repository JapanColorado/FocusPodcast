package dev.russell.focuspodcast.fragment.feeditemlist

import dev.russell.focuspodcast.R
import dev.russell.focuspodcast.activity.MainActivity
import dev.russell.focuspodcast.adapter.EpisodeItemListAdapter
import dev.russell.focuspodcast.adapter.MultiSelectAdapter
import dev.russell.focuspodcast.adapter.MultiSelectAdapter.OnPrepareActionModeListener
import dev.russell.focuspodcast.fragment.actions.EpisodeMultiSelectActionHandler
import dev.russell.focuspodcast.model.feed.Feed
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.ActionMode

/**
 * Owns the multi-select action mode of [dev.russell.focuspodcast.fragment.FeedItemlistFragment]'s episode
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
