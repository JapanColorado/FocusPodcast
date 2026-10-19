package dev.russell.focuspodcast.fragment

import dev.russell.focuspodcast.R
import dev.russell.focuspodcast.activity.MainActivity
import dev.russell.focuspodcast.adapter.BookmarkAdapter
import dev.russell.focuspodcast.common.util.DoubleClickBackToContentTopListener
import dev.russell.focuspodcast.core.storage.DBReader
import dev.russell.focuspodcast.core.util.playback.PlaybackController
import dev.russell.focuspodcast.databinding.BookmarksFragmentBinding
import dev.russell.focuspodcast.event.BookmarksChangedEvent
import dev.russell.focuspodcast.event.FeedListUpdateEvent
import dev.russell.focuspodcast.model.feed.Bookmark
import dev.russell.focuspodcast.util.BookmarkActions
import dev.russell.focuspodcast.util.PlayAtPosition
import dev.russell.focuspodcast.view.EmptyViewHandler
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
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Every bookmark, most recently made first, with its episode's cover and title. Tapping one plays
 * its episode from the bookmarked position; the overflow menu edits the note, shares or deletes
 * it (with undo). A navigation drawer page.
 */
class BookmarksFragment : Fragment(), BookmarkAdapter.Listener,
    DoubleClickBackToContentTopListener.IBackToContentTopView {

    private var _binding: BookmarksFragmentBinding? = null
    private val adapter = BookmarkAdapter(true, this)
    private lateinit var emptyView: EmptyViewHandler
    private var controller: PlaybackController? = null
    private var loadDisposable: Disposable? = null
    private val playDisposables = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = BookmarksFragmentBinding.inflate(inflater, container, false)
        _binding = binding
        val toolbar = binding.appBarLayout.toolbar
        toolbar.setTitle(R.string.bookmarks_label)
        var displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)
        }
        (requireActivity() as MainActivity).setupToolbarToggle(toolbar, displayUpArrow)
        toolbar.setOnClickListener(DoubleClickBackToContentTopListener(this))

        binding.bookmarkList.layoutManager = LinearLayoutManager(requireContext())
        binding.bookmarkList.adapter = adapter

        emptyView = EmptyViewHandler(context)
        emptyView.setIcon(R.drawable.ic_bookmark)
        emptyView.setTitle(R.string.no_bookmarks_head_label)
        emptyView.setMessage(R.string.no_bookmarks_label)
        emptyView.attachToRecyclerView(binding.bookmarkList)
        emptyView.hide()
        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, parentFragmentManager.backStackEntryCount != 0)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        val controller = object : PlaybackController(requireActivity()) {
            override fun loadMediaInfo() {}
        }
        this.controller = controller
        controller.init()
        EventBus.getDefault().register(this)
        loadBookmarks()
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        loadDisposable?.dispose()
        controller?.release()
        controller = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        playDisposables.dispose()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBookmarksChanged(event: BookmarksChangedEvent) {
        loadBookmarks()
    }

    /** A feed was removed (and its bookmarks with it) or renamed. */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedListChanged(event: FeedListUpdateEvent) {
        loadBookmarks()
    }

    private fun loadBookmarks() {
        loadDisposable?.dispose()
        loadDisposable = Single.fromCallable { DBReader.getAllBookmarksWithItems() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { bookmarks: List<Bookmark> ->
                    adapter.setBookmarks(bookmarks)
                    emptyView.updateVisibility()
                },
                { error: Throwable -> Log.e(TAG, "could not load the bookmarks", error) }
            )
    }

    /** Plays the bookmark's episode from the bookmarked position (see [PlayAtPosition]). */
    override fun onBookmarkClicked(bookmark: Bookmark) {
        val item = bookmark.item ?: return
        PlayAtPosition.play({ activity }, controller, item, bookmark.positionMs)
            ?.let { playDisposables.add(it) }
    }

    override fun onBookmarkMenu(bookmark: Bookmark, anchor: View) {
        BookmarkActions.showMenu(anchor, bookmark, bookmark.item) { text ->
            (activity as? MainActivity)?.showSnackbarAbovePlayer(text, Snackbar.LENGTH_LONG)
        }
    }

    override fun backToContentTop() {
        val list = _binding?.bookmarkList ?: return
        list.scrollToPosition(5)
        list.post { list.smoothScrollToPosition(0) }
    }

    companion object {
        const val TAG = "BookmarksFragment"
        private const val KEY_UP_ARROW = "up_arrow"
    }
}
