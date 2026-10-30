package dev.russell.yagi.fragment

import dev.russell.yagi.adapter.BookmarkAdapter
import dev.russell.yagi.core.storage.DBReader
import dev.russell.yagi.core.util.playback.PlaybackController
import dev.russell.yagi.databinding.EpisodeBookmarksFragmentBinding
import dev.russell.yagi.event.BookmarksChangedEvent
import dev.russell.yagi.model.feed.Bookmark
import dev.russell.yagi.model.feed.FeedItem
import dev.russell.yagi.model.feed.FeedMedia
import dev.russell.yagi.util.BookmarkActions
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * The Bookmarks page of the player's episode sheet ([FeedItemExtraInfoFragment]): the bookmarks
 * of the episode that is playing in position order. Tapping one seeks there; its overflow menu
 * edits the note, shares or deletes it (with undo).
 */
class EpisodeBookmarksFragment : Fragment(), BookmarkAdapter.Listener {

    private var _binding: EpisodeBookmarksFragmentBinding? = null
    private val adapter = BookmarkAdapter(false, this)
    private var controller: PlaybackController? = null
    private var disposable: Disposable? = null
    /** The episode whose bookmarks are shown, 0 for none. */
    private var shownItemId: Long = 0
    private var shownItem: FeedItem? = null

    /** What loading found for the episode that is playing. */
    private class Loaded(val item: FeedItem?, val bookmarks: List<Bookmark>)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = EpisodeBookmarksFragmentBinding.inflate(inflater, container, false)
        _binding = binding
        binding.bookmarkList.layoutManager = LinearLayoutManager(requireContext())
        binding.bookmarkList.adapter = adapter
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onStart() {
        super.onStart()
        val controller = object : PlaybackController(requireActivity()) {
            override fun loadMediaInfo() {
                this@EpisodeBookmarksFragment.loadBookmarks()
            }
        }
        this.controller = controller
        controller.init()
        EventBus.getDefault().register(this)
        loadBookmarks()
    }

    override fun onStop() {
        super.onStop()
        disposable?.dispose()
        controller?.release()
        controller = null
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBookmarksChanged(event: BookmarksChangedEvent) {
        if (event.feedItemId == shownItemId) {
            loadBookmarks()
        }
    }

    private fun loadBookmarks() {
        val controller = this.controller ?: return
        disposable?.dispose()
        disposable = Single.fromCallable { load(controller) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { loaded: Loaded -> onLoaded(loaded) },
                { error: Throwable -> Log.e(TAG, "could not load the bookmarks", error) }
            )
    }

    /** Runs off the main thread. */
    private fun load(controller: PlaybackController): Loaded {
        val media = controller.media as? FeedMedia ?: return Loaded(null, emptyList())
        val item = DBReader.getFeedItem(media.itemId) ?: return Loaded(null, emptyList())
        val bookmarks = DBReader.loadBookmarksOfFeedItem(item.id)
        bookmarks.forEach { it.item = item }
        return Loaded(item, bookmarks)
    }

    private fun onLoaded(loaded: Loaded) {
        val binding = _binding ?: return
        shownItem = loaded.item
        shownItemId = loaded.item?.id ?: 0
        adapter.setBookmarks(loaded.bookmarks)
        val empty = loaded.bookmarks.isEmpty()
        binding.bookmarkList.visibility = if (empty) View.GONE else View.VISIBLE
        binding.bookmarkMessageContainer.visibility = if (empty) View.VISIBLE else View.GONE
    }

    override fun onBookmarkClicked(bookmark: Bookmark) {
        controller?.seekTo(bookmark.positionMs.toInt())
    }

    override fun onBookmarkMenu(bookmark: Bookmark, anchor: View) {
        BookmarkActions.showMenu(anchor, bookmark, shownItem) { text ->
            val root = view ?: return@showMenu null
            Snackbar.make(root, text, Snackbar.LENGTH_LONG).also { it.show() }
        }
    }

    companion object {
        private const val TAG = "EpisodeBookmarks"
    }
}
