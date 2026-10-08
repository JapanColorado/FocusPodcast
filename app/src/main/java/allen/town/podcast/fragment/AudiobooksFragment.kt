package allen.town.podcast.fragment

import allen.town.podcast.R
import allen.town.podcast.activity.MainActivity
import allen.town.podcast.adapter.AudiobookAdapter
import allen.town.podcast.adapter.AudiobookEntry
import allen.town.podcast.common.util.DoubleClickBackToContentTopListener
import allen.town.podcast.databinding.FragmentAudiobooksBinding
import allen.town.podcast.event.FeedItemEvent
import allen.town.podcast.event.FeedListUpdateEvent
import allen.town.podcast.event.PlayerStatusEvent
import allen.town.podcast.event.UnreadItemsUpdateEvent
import allen.town.podcast.util.LocalFeedImporter
import allen.town.podcast.view.EmptyViewHandler
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * All audiobooks as a grid of covers, each with how far the user got. Tapping a cover opens the
 * book; the button on it continues where the user left off.
 */
class AudiobooksFragment : Fragment(), DoubleClickBackToContentTopListener.IBackToContentTopView {
    private var _binding: FragmentAudiobooksBinding? = null
    private lateinit var adapter: AudiobookAdapter
    private lateinit var emptyView: EmptyViewHandler
    private val localFeedImporter = LocalFeedImporter(this)
    private var disposable: Disposable? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private val reloader = Runnable { loadAudiobooks() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentAudiobooksBinding.inflate(inflater, container, false)
        _binding = binding
        val toolbar = binding.appBarLayout.toolbar
        toolbar.setTitle(R.string.audiobooks_label)
        var displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)
        }
        (requireActivity() as MainActivity).setupToolbarToggle(toolbar, displayUpArrow)
        toolbar.setOnClickListener(DoubleClickBackToContentTopListener(this))

        adapter = AudiobookAdapter(requireActivity() as MainActivity)
        binding.audiobooksGrid.layoutManager = GridLayoutManager(
            context, resources.getInteger(R.integer.subscriptions_default_num_of_columns)
        )
        binding.audiobooksGrid.adapter = adapter

        emptyView = EmptyViewHandler(context)
        emptyView.setIcon(R.drawable.ic_audiobook)
        emptyView.setTitle(R.string.no_audiobooks_head_label)
        emptyView.setMessage(R.string.no_audiobooks_label)
        emptyView.addAction(R.string.add_audiobook, R.drawable.ic_audiobook) {
            localFeedImporter.addAudiobook()
        }
        emptyView.attachToRecyclerView(binding.audiobooksGrid)
        emptyView.hide()

        binding.audiobooksAdd.setOnClickListener { localFeedImporter.addAudiobook() }
        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, parentFragmentManager.backStackEntryCount != 0)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        loadAudiobooks()
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        disposable?.dispose()
        uiHandler.removeCallbacks(reloader)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        // back from a book's page: show the progress made there
        if (!hidden && isAdded) {
            loadAudiobooks()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        localFeedImporter.dispose()
    }

    private fun loadAudiobooks() {
        disposable?.dispose()
        disposable = Observable.fromCallable { AudiobookEntry.load() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { entries: List<AudiobookEntry> ->
                    adapter.setEntries(entries)
                    emptyView.updateVisibility()
                },
                { error: Throwable -> Log.e(TAG, "loading the audiobooks failed", error) }
            )
    }

    /** Events come in bursts (a feed refresh, marking many played): reload once they settle. */
    private fun scheduleReload() {
        uiHandler.removeCallbacks(reloader)
        uiHandler.postDelayed(reloader, RELOAD_DELAY_MS)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedListChanged(event: FeedListUpdateEvent?) {
        scheduleReload()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUnreadItemsChanged(event: UnreadItemsUpdateEvent?) {
        scheduleReload()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedItemsChanged(event: FeedItemEvent?) {
        scheduleReload()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusChanged(event: PlayerStatusEvent?) {
        scheduleReload()
    }

    override fun backToContentTop() {
        val grid = _binding?.audiobooksGrid ?: return
        grid.scrollToPosition(5)
        grid.post { grid.smoothScrollToPosition(0) }
    }

    companion object {
        const val TAG = "AudiobooksFragment"
        private const val KEY_UP_ARROW = "up_arrow"
        private const val RELOAD_DELAY_MS = 500L
    }
}
