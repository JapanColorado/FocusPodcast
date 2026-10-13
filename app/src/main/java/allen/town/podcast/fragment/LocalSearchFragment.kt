package allen.town.podcast.fragment

import allen.town.podcast.core.view.TopAppBarLayout
import allen.town.podcast.R
import allen.town.podcast.activity.MainActivity
import allen.town.podcast.adapter.EpisodeItemListAdapter
import allen.town.podcast.adapter.FeedSearchResultAdapter
import allen.town.podcast.adapter.SubFeedsAdapter.GridDividerItemDecorator
import allen.town.podcast.core.event.DownloadEvent
import allen.town.podcast.core.event.DownloaderUpdate
import allen.town.podcast.core.pref.Prefs
import allen.town.podcast.core.storage.FeedSearcher
import allen.town.podcast.core.util.FeedItemUtil
import allen.town.podcast.core.util.menuhandler.MenuItemUtils
import allen.town.podcast.dialog.filter.EpisodeSearchFilterSheet
import allen.town.podcast.event.FeedItemEvent
import allen.town.podcast.event.PlayerStatusEvent
import allen.town.podcast.event.UnreadItemsUpdateEvent
import allen.town.podcast.event.playback.PlaybackPositionEvent
import allen.town.podcast.menuprocess.FeedItemMenuProcess
import allen.town.podcast.model.feed.EpisodeSearchOptions
import allen.town.podcast.model.feed.Feed
import allen.town.podcast.model.feed.FeedItem
import allen.town.podcast.view.EmptyViewHandler
import allen.town.podcast.viewholder.EpisodeItemViewHolder
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Pair
import android.view.*
import android.view.ContextMenu.ContextMenuInfo
import android.view.View.OnFocusChangeListener
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import allen.town.podcast.theme.util.EditTextUtil
import allen.town.podcast.theme.util.scroll.ThemedFastScroller.create
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.concurrent.Callable

/**
 * Performs a search operation on all feeds or one specific feed and displays the search result.
 *
 * Episode searches can be sorted, filtered and scoped to one podcast in [EpisodeSearchFilterSheet]
 * ("Sort & filter" in the toolbar); the database does all of it ([FeedSearcher.searchFeedItems]).
 * The choice except the podcast is kept in [Prefs.episodeSearchOptions]; the podcast comes from the
 * arguments (a search started from a podcast's page) or the sheet's picker, for this search only.
 */
class LocalSearchFragment constructor() : Fragment(), EpisodeSearchFilterSheet.Host {
    private lateinit var adapter: EpisodeItemListAdapter
    private lateinit var adapterFeeds: FeedSearchResultAdapter
    private var disposable: Disposable? = null
    private lateinit var emptyViewHandler: EmptyViewHandler
    private lateinit var recyclerView: RecyclerView
    private var results: MutableList<FeedItem>? = null
    private lateinit var searchView: SearchView
    private lateinit var automaticSearchDebouncer: Handler
    private var lastQueryChange: Long = 0
    private lateinit var prefs: SharedPreferences
    private var searchFeeds: Boolean = false
    private lateinit var toolbar: Toolbar
    override var searchOptions: EpisodeSearchOptions = EpisodeSearchOptions.defaults()
        private set
    override var searchScopeTitle: String? = null
        private set
    override val searchQuery: String
        get() = searchView.getQuery().toString()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setRetainInstance(true)
        automaticSearchDebouncer = Handler(Looper.getMainLooper())
        searchOptions = Prefs.episodeSearchOptions.withFeedId(requireArguments().getLong(ARG_FEED, 0))
        searchScopeTitle = requireArguments().getString(ARG_FEED_NAME, null)
    }

    public override fun onStop() {
        super.onStop()
        disposable?.dispose()
    }

    public override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val layout: View = inflater.inflate(R.layout.search_fragment, container, false)
        toolbar = layout.findViewById<TopAppBarLayout>(R.id.appBarLayout).toolbar
        setupToolbar(toolbar)
        prefs = requireActivity().getSharedPreferences(SubFeedsFragment.PREFS, Context.MODE_PRIVATE)
        recyclerView = layout.findViewById(R.id.recyclerView)
        recyclerView.setLayoutManager(LinearLayoutManager(getActivity()))
        val mainActivity = requireActivity() as MainActivity
        recyclerView.setRecycledViewPool(mainActivity.recycledViewPool)
        adapter = object : EpisodeItemListAdapter(mainActivity, 0) {
            public override fun onCreateContextMenu(
                menu: ContextMenu,
                v: View,
                menuInfo: ContextMenuInfo?
            ) {
                super.onCreateContextMenu(menu, v, menuInfo)
                MenuItemUtils.setOnClickListeners(
                    menu,
                    MenuItem.OnMenuItemClickListener({ item: MenuItem ->
                        this@LocalSearchFragment.onContextItemSelected(item)
                    })
                )
            }
        }
        recyclerView.setAdapter(adapter)
        val recyclerViewFeeds: RecyclerView = layout.findViewById(R.id.recyclerViewFeeds)
        val gridLayoutManager: GridLayoutManager = GridLayoutManager(
            getContext(),
            prefs.getInt(
                SubFeedsFragment.PREF_NUM_COLUMNS,
                getResources().getInteger(R.integer.subscriptions_default_num_of_columns)
            ), RecyclerView.VERTICAL, false
        )
        recyclerViewFeeds.addItemDecoration(GridDividerItemDecorator())
        recyclerViewFeeds.setLayoutManager(gridLayoutManager)
        adapterFeeds = FeedSearchResultAdapter(mainActivity)
        recyclerViewFeeds.setAdapter(adapterFeeds)
        create(recyclerView)
        create(recyclerViewFeeds)
        emptyViewHandler = EmptyViewHandler(getContext())
        emptyViewHandler.setIcon(R.drawable.ic_search)
        emptyViewHandler.setTitle(R.string.search_status_no_results)
        searchFeeds = requireArguments().getBoolean(ARG_SEARCH_FEED, false)
        toolbar.menu.findItem(R.id.action_search_filter)?.isVisible = !searchFeeds
        updateFilterIndicator()
        EventBus.getDefault().register(this)
        if (requireArguments().getString(ARG_QUERY, null) != null) {
            searchView.setQuery(requireArguments().getString(ARG_QUERY, null), false)
            searchWithProgressBar()
        }
        searchView.setOnQueryTextFocusChangeListener(OnFocusChangeListener({ view: View, hasFocus: Boolean ->
            if (hasFocus) {
                showInputMethod(view.findFocus())
            }
        }))
        EditTextUtil.setCursorDrawableForSearchView(searchView)
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            public override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    val imm: InputMethodManager =
                        requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(recyclerView.getWindowToken(), 0)
                }
            }
        })
        if (searchFeeds) {
            recyclerView.setVisibility(View.GONE)
            recyclerViewFeeds.setVisibility(View.VISIBLE)
            emptyViewHandler.attachToRecyclerView(recyclerViewFeeds)
        } else {
            recyclerView.setVisibility(View.VISIBLE)
            recyclerViewFeeds.setVisibility(View.GONE)
            emptyViewHandler.attachToRecyclerView(recyclerView)
        }
        return layout
    }

    public override fun onDestroyView() {
        super.onDestroyView()
        automaticSearchDebouncer.removeCallbacksAndMessages(null)
        EventBus.getDefault().unregister(this)
    }

    private fun setupToolbar(toolbar: Toolbar) {
        toolbar.setTitle(R.string.search_label)
        toolbar.setNavigationOnClickListener(View.OnClickListener({ v: View? -> getParentFragmentManager().popBackStack() }))
        toolbar.inflateMenu(R.menu.search)
        val item: MenuItem = toolbar.getMenu().findItem(R.id.action_search)
        item.expandActionView()
        searchView = item.getActionView() as SearchView
        updateQueryHint()
        searchView.requestFocus()
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            public override fun onQueryTextSubmit(s: String): Boolean {
                searchView.clearFocus()
                searchWithProgressBar()
                return true
            }

            public override fun onQueryTextChange(s: String): Boolean {
                automaticSearchDebouncer.removeCallbacksAndMessages(null)
                if (s.isEmpty() || s.endsWith(" ") || ((lastQueryChange != 0L && System.currentTimeMillis() > lastQueryChange + SEARCH_DEBOUNCE_INTERVAL))) {
                    search()
                } else {
                    automaticSearchDebouncer.postDelayed(Runnable({
                        search()
                        lastQueryChange =
                            0 // Don't search instantly with first symbol after some pause
                    }), (SEARCH_DEBOUNCE_INTERVAL / 2).toLong())
                }
                lastQueryChange = System.currentTimeMillis()
                return false
            }
        })
        toolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_search_filter) {
                showFilterSheet()
                true
            } else {
                false
            }
        }
        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            public override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                return true
            }

            public override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                getParentFragmentManager().popBackStack()
                return true
            }
        })
    }

    public override fun onContextItemSelected(item: MenuItem): Boolean {
        val selectedItem: FeedItem? = adapter.longPressedItem
        if (selectedItem == null) {
            Log.i(TAG, "Selected item at current position was null, ignoring selection")
            return super.onContextItemSelected(item)
        }
        return FeedItemMenuProcess.onMenuItemClicked(this, item.getItemId(), selectedItem)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUnreadItemsChanged(event: UnreadItemsUpdateEvent?) {
        search()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent) {
        val results = this.results ?: return
        var i: Int = 0
        val size: Int = event.items.size
        while (i < size) {
            val item: FeedItem = event.items.get(i)
            val pos: Int = FeedItemUtil.indexOfItemWithId(results, item.getId())
            if (pos >= 0) {
                results.removeAt(pos)
                results.add(pos, item)
                adapter.notifyItemChangedCompat(pos)
            }
            i++
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: DownloadEvent) {
        val update: DownloaderUpdate = event.update
        if (update.mediaIds.size > 0) {
            for (mediaId: Long in update.mediaIds) {
                val pos: Int = FeedItemUtil.indexOfItemWithMediaId(results, mediaId)
                if (pos >= 0) {
                    adapter.notifyItemChangedCompat(pos)
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        for (i in 0 until adapter.getItemCount()) {
            val holder: EpisodeItemViewHolder? =
                recyclerView.findViewHolderForAdapterPosition(i) as EpisodeItemViewHolder?
            if (holder != null && holder.isCurrentlyPlayingItem) {
                holder.notifyPlaybackPositionUpdated(event)
                break
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusChanged(event: PlayerStatusEvent?) {
        search()
    }

    private fun searchWithProgressBar() {
        emptyViewHandler.hide()
        if (requireArguments().getLong(ARG_FEED, 0) == 0L) {
            //not shown when searching within a feed
//            feedSkeleton.showSkeleton();
        }
        search()
    }

    private fun search() {
        val query: String = searchView.getQuery().toString()
        val options = searchOptions
        val feedSearch = searchFeeds
        disposable?.dispose()
        disposable = Observable.fromCallable(
            Callable({ performSearch(query, options, feedSearch) })
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ results: Pair<List<FeedItem>, List<Feed>> ->
                @Suppress("UNCHECKED_CAST")
                this.results = results.first as MutableList<FeedItem>
                adapter.updateItems(results.first)
                // a podcast scope hides the podcast results
                if (options.feedId == 0L) {
                    adapterFeeds.updateData(results.second)
                } else {
                    adapterFeeds.updateData(emptyList())
                }
                updateEmptyView()
            }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    private fun performSearch(
        query: String,
        options: EpisodeSearchOptions,
        feedSearch: Boolean
    ): Pair<List<FeedItem>, List<Feed>> {
        if (query.isEmpty()) {
            return Pair(ArrayList(), emptyList<Feed>())
        }
        var items: List<FeedItem> = ArrayList()
        var feeds: List<Feed> = ArrayList()
        if (feedSearch) {
            feeds = FeedSearcher.searchFeeds(query)
        } else {
            items = FeedSearcher.searchFeedItems(query, options)
        }
        return Pair(items, feeds)
    }

    /** Says why the list is empty: nothing typed, nothing found, or the filters hide everything. */
    private fun updateEmptyView() {
        val query = searchView.getQuery()
        emptyViewHandler.clearActions()
        if (query.isEmpty()) {
            emptyViewHandler.setMessage(R.string.type_to_search)
        } else if (!searchFeeds && searchOptions.isFiltering) {
            emptyViewHandler.setMessage(getString(R.string.search_no_results_filtered, query))
            emptyViewHandler.addAction(R.string.clear_filter_label, R.drawable.ic_filter_disable) {
                // keeps the sort order, the fields searched and the podcast
                val cleared = EpisodeSearchOptions.defaults()
                    .withSort(searchOptions.sort)
                    .withFeedId(searchOptions.feedId)
                onSearchOptionsChanged(cleared, searchScopeTitle)
            }
        } else {
            emptyViewHandler.setMessage(getString(R.string.no_results_for_query, query))
        }
    }

    private fun showFilterSheet() {
        if (childFragmentManager.findFragmentByTag(EpisodeSearchFilterSheet.TAG) == null) {
            EpisodeSearchFilterSheet().show(childFragmentManager, EpisodeSearchFilterSheet.TAG)
        }
    }

    override fun onSearchOptionsChanged(options: EpisodeSearchOptions, scopeTitle: String?) {
        searchOptions = options
        searchScopeTitle = if (options.feedId != 0L) scopeTitle else null
        // the arguments survive recreation, so the podcast scope does too
        requireArguments().putLong(ARG_FEED, options.feedId)
        requireArguments().putString(ARG_FEED_NAME, searchScopeTitle)
        Prefs.episodeSearchOptions = options.withFeedId(0)
        updateFilterIndicator()
        updateQueryHint()
        search()
    }

    /** The filter icon shows whether a filter or a podcast scope narrows the results. */
    private fun updateFilterIndicator() {
        val item = toolbar.menu.findItem(R.id.action_search_filter) ?: return
        val narrowed = searchOptions.isFiltering || searchOptions.feedId != 0L
        item.setIcon(if (narrowed) R.drawable.ic_filter_disable else R.drawable.ic_filter)
        item.setTitle(if (narrowed) R.string.sort_and_filter_active else R.string.sort_and_filter)
    }

    private fun updateQueryHint() {
        val title = searchScopeTitle
        searchView.setQueryHint(
            if (searchOptions.feedId != 0L && !title.isNullOrEmpty()) {
                getString(R.string.search_in_feed_hint, title)
            } else {
                getString(R.string.search_label)
            }
        )
    }

    private fun showInputMethod(view: View) {
        val imm: InputMethodManager? =
            requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        if (imm != null) {
            imm.showSoftInput(view, 0)
        }
    }

    companion object {
        private val TAG: String = "LocalSearchFragment"
        private val ARG_QUERY: String = "query"
        private val ARG_FEED: String = "feed"
        private val ARG_FEED_NAME: String = "feedName"
        private val ARG_SEARCH_FEED: String = "search_feed"
        private val SEARCH_DEBOUNCE_INTERVAL: Int = 1500

        /**
         * Create a new SearchFragment that searches all feeds.
         */
        @JvmStatic
        fun newInstance(): LocalSearchFragment {
            val fragment: LocalSearchFragment = LocalSearchFragment()
            val args: Bundle = Bundle()
            args.putLong(ARG_FEED, 0)
            fragment.setArguments(args)
            return fragment
        }

        /**
         * Create a new SearchFragment that searches all feeds with pre-defined query.
         */
        @JvmStatic
        fun newInstance(query: String?): LocalSearchFragment {
            val fragment: LocalSearchFragment = newInstance()
            fragment.requireArguments().putString(ARG_QUERY, query)
            return fragment
        }

        /**
         * Create a new SearchFragment that searches one specific feed.
         */
        @JvmStatic
        fun newInstance(feed: Long, feedTitle: String?): LocalSearchFragment {
            val fragment: LocalSearchFragment = newInstance()
            fragment.requireArguments().putLong(ARG_FEED, feed)
            fragment.requireArguments()
                .putString(ARG_FEED_NAME, feedTitle)
            return fragment
        }

        @JvmStatic
        fun newFeedSearchInstance(): LocalSearchFragment {
            val fragment: LocalSearchFragment = newInstance()
            fragment.requireArguments().putBoolean(ARG_SEARCH_FEED, true)
            return fragment
        }
    }
}