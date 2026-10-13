package allen.town.podcast.fragment

import allen.town.podcast.R
import allen.town.podcast.adapter.TranscriptAdapter
import allen.town.podcast.core.storage.DBReader
import allen.town.podcast.core.util.TranscriptUtils
import allen.town.podcast.core.util.playback.PlaybackController
import allen.town.podcast.databinding.TranscriptFragmentBinding
import allen.town.podcast.event.playback.PlaybackPositionEvent
import allen.town.podcast.model.feed.FeedItem
import allen.town.podcast.model.feed.FeedMedia
import allen.town.podcast.model.transcript.Transcript
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * The Transcript page of the player's episode sheet ([FeedItemExtraInfoFragment]). Shows the
 * transcript of the episode that is playing, fetching it when the page is first shown and nothing
 * is cached yet; follows
 * playback by highlighting the line being spoken and scrolling to it, unless the user scrolled
 * in the last [USER_SCROLL_PAUSE_MS]; tapping a line seeks there. An untimed transcript is
 * shown as text only.
 */
class TranscriptFragment : Fragment() {

    private var _binding: TranscriptFragmentBinding? = null
    private val adapter = TranscriptAdapter { index -> seekToLine(index) }
    private lateinit var layoutManager: LinearLayoutManager
    private var controller: PlaybackController? = null
    private var disposable: Disposable? = null
    private var transcript: Transcript = Transcript.empty()
    /** Id of the episode whose transcript is shown, 0 for none. */
    private var shownItemId: Long = 0
    private var lastUserScrollAt: Long = 0
    private var userDragging = false

    /** What loading found for the episode that is playing. */
    private class Loaded(val itemId: Long, val transcript: Transcript?)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = TranscriptFragmentBinding.inflate(inflater, container, false)
        _binding = binding
        layoutManager = LinearLayoutManager(requireContext())
        binding.transcriptList.layoutManager = layoutManager
        binding.transcriptList.adapter = adapter
        binding.transcriptList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    userDragging = true
                    lastUserScrollAt = SystemClock.elapsedRealtime()
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE && userDragging) {
                    // the pause counts from when the list came to rest
                    userDragging = false
                    lastUserScrollAt = SystemClock.elapsedRealtime()
                }
            }
        })
        binding.transcriptRetry.setOnClickListener {
            shownItemId = 0
            loadTranscript()
        }
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
                // the pager keeps this page alive next to the others; fetch only once it is shown
                if (isResumed) {
                    this@TranscriptFragment.loadTranscript()
                }
            }
        }
        this.controller = controller
        controller.init()
        EventBus.getDefault().register(this)
    }

    override fun onResume() {
        super.onResume()
        loadTranscript()
    }

    override fun onStop() {
        super.onStop()
        disposable?.dispose()
        controller?.release()
        controller = null
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        updateCurrentLine(event.position.toLong(), false)
    }

    private fun loadTranscript() {
        val controller = this.controller ?: return
        disposable?.dispose()
        if (shownItemId == 0L) {
            showProgress()
        }
        // read on the main thread, where they are written
        val currentItemId = shownItemId
        val currentTranscript = transcript
        disposable = Single.fromCallable { load(controller, currentItemId, currentTranscript) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { loaded: Loaded -> onLoaded(loaded) },
                { error: Throwable ->
                    Log.w(TAG, "could not load the transcript", error)
                    shownItemId = 0
                    showMessage(R.string.transcript_error, true)
                }
            )
    }

    /** Runs off the main thread. The transcript is null when the episode offers none. */
    private fun load(controller: PlaybackController, currentItemId: Long, currentTranscript: Transcript): Loaded {
        val media = controller.media as? FeedMedia ?: return Loaded(0, null)
        val item: FeedItem = media.item ?: DBReader.getFeedItem(media.itemId) ?: return Loaded(0, null)
        if (item.id == currentItemId) {
            return Loaded(item.id, currentTranscript) // same episode, already shown
        }
        if (!item.hasTranscript() && DBReader.loadCachedTranscript(item.id) == null) {
            return Loaded(item.id, null)
        }
        return Loaded(item.id, TranscriptUtils.load(item))
    }

    private fun onLoaded(loaded: Loaded) {
        val loadedTranscript = loaded.transcript
        if (loadedTranscript == null) {
            shownItemId = 0
            showMessage(R.string.transcript_none, false)
            return
        }
        if (loaded.itemId == shownItemId && loadedTranscript === transcript) {
            return
        }
        shownItemId = loaded.itemId
        transcript = loadedTranscript
        if (loadedTranscript.isEmpty) {
            showMessage(R.string.transcript_empty, false)
            return
        }
        val binding = _binding ?: return
        binding.transcriptProgress.visibility = View.GONE
        binding.transcriptMessageContainer.visibility = View.GONE
        binding.transcriptList.visibility = View.VISIBLE
        adapter.setTranscript(loadedTranscript)
        lastUserScrollAt = 0
        val position = controller?.position ?: -1
        if (position >= 0) {
            updateCurrentLine(position.toLong(), true)
        }
    }

    private fun updateCurrentLine(positionMs: Long, jump: Boolean) {
        if (!transcript.isTimed || _binding == null) {
            return
        }
        val index = transcript.indexAt(positionMs)
        if (index == adapter.currentIndex) {
            return
        }
        adapter.setCurrentIndex(index)
        if (index >= 0 && SystemClock.elapsedRealtime() - lastUserScrollAt >= USER_SCROLL_PAUSE_MS) {
            scrollToLine(index, jump)
        }
    }

    private fun seekToLine(index: Int) {
        val controller = this.controller ?: return
        val segment = transcript.segments.getOrNull(index) ?: return
        if (!segment.isTimed) {
            return
        }
        controller.seekTo(segment.startMs.toInt())
        adapter.setCurrentIndex(index)
    }

    /** Puts the line a third of the way down the list: smoothly when it is near, at once otherwise. */
    private fun scrollToLine(index: Int, jump: Boolean) {
        val list = _binding?.transcriptList ?: return
        val first = layoutManager.findFirstVisibleItemPosition()
        if (jump || first == RecyclerView.NO_POSITION || kotlin.math.abs(index - first) > MAX_SMOOTH_SCROLL_LINES) {
            layoutManager.scrollToPositionWithOffset(index, list.height / 3)
            return
        }
        val scroller = object : LinearSmoothScroller(list.context) {
            override fun calculateDtToFit(viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int,
                                          snapPreference: Int): Int =
                boxStart + (boxEnd - boxStart) / 3 - viewStart
        }
        scroller.targetPosition = index
        layoutManager.startSmoothScroll(scroller)
    }

    private fun showProgress() {
        val binding = _binding ?: return
        binding.transcriptList.visibility = View.GONE
        binding.transcriptMessageContainer.visibility = View.GONE
        binding.transcriptProgress.visibility = View.VISIBLE
    }

    private fun showMessage(message: Int, retry: Boolean) {
        val binding = _binding ?: return
        binding.transcriptList.visibility = View.GONE
        binding.transcriptProgress.visibility = View.GONE
        binding.transcriptMessageContainer.visibility = View.VISIBLE
        binding.transcriptMessage.setText(message)
        binding.transcriptRetry.visibility = if (retry) View.VISIBLE else View.GONE
    }

    companion object {
        private const val TAG = "TranscriptFragment"
        /** How long after the user scrolled the list stops following playback. */
        const val USER_SCROLL_PAUSE_MS = 5_000L
        /** Further than this the list jumps instead of scrolling smoothly. */
        private const val MAX_SMOOTH_SCROLL_LINES = 20
    }
}
