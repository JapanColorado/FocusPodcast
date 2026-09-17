package allen.town.podcast.dialog

import allen.town.podcast.R
import allen.town.podcast.common.views.AccentMaterialDialog
import allen.town.podcast.core.storage.DBReader
import allen.town.podcast.core.storage.DBWriter
import allen.town.podcast.core.util.Converter
import allen.town.podcast.core.util.playback.PlaybackController
import allen.town.podcast.databinding.AdSegmentListItemBinding
import allen.town.podcast.databinding.AdSegmentsDialogBinding
import allen.town.podcast.event.adskip.AdSegmentsChangedEvent
import allen.town.podcast.model.feed.AdSegment
import allen.town.podcast.model.feed.FeedMedia
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.Maybe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.math.roundToInt

/**
 * Lists the ad segments stored for one episode and lets the user curate them: each row can be
 * switched off (it is then kept but never skipped) or deleted outright, and tapping a row seeks
 * the player to the start of that segment when the episode in question is the one playing.
 *
 * The dialog owns no detection logic; it reads through [DBReader], writes through [DBWriter] and
 * reloads whenever a write (its own or the analyser's) posts an [AdSegmentsChangedEvent].
 */
class AdSegmentsDialog : DialogFragment() {

    private var binding: AdSegmentsDialogBinding? = null
    private var disposable: Disposable? = null
    private var controller: PlaybackController? = null
    private val adapter = SegmentsAdapter()
    private var feedItemId: Long = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        feedItemId = requireArguments().getLong(ARG_FEED_ITEM_ID)
        val viewBinding = AdSegmentsDialogBinding.inflate(layoutInflater)
        binding = viewBinding
        viewBinding.adSegmentsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.adSegmentsRecyclerView.adapter = adapter
        showSegments(emptyList())

        val builder: AlertDialog.Builder = AccentMaterialDialog(
            requireContext(), R.style.MaterialAlertDialogTheme
        )
        builder.setTitle(R.string.ad_segments_title)
        builder.setView(viewBinding.root)
        builder.setPositiveButton(R.string.close_label, null)
        return builder.create()
    }

    override fun onStart() {
        super.onStart()
        val playbackController = object : PlaybackController(requireActivity()) {
            override fun loadMediaInfo() {}
        }
        controller = playbackController
        playbackController.init()
        EventBus.getDefault().register(this)
        loadSegments()
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        controller?.release()
        controller = null
        disposable?.dispose()
        disposable = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAdSegmentsChanged(event: AdSegmentsChangedEvent) {
        if (event.feedItemId == feedItemId) {
            loadSegments()
        }
    }

    private fun loadSegments() {
        val itemId = feedItemId
        disposable?.dispose()
        disposable = Maybe.fromCallable { DBReader.loadAdSegmentsOfFeedItem(itemId) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { segments -> showSegments(segments) },
                { error -> Log.e(TAG, "could not load the ad segments of item $itemId", error) }
            )
    }

    private fun showSegments(segments: List<AdSegment>) {
        adapter.submit(segments)
        val viewBinding = binding ?: return
        viewBinding.emptyView.visibility = if (segments.isEmpty()) View.VISIBLE else View.GONE
        viewBinding.adSegmentsRecyclerView.visibility =
            if (segments.isEmpty()) View.GONE else View.VISIBLE
    }

    /** Seeks the player to a segment, but only while it is playing the episode being listed. */
    private fun seekTo(segment: AdSegment) {
        val playbackController = controller ?: return
        val media = playbackController.media as? FeedMedia ?: return
        if (media.item?.id != feedItemId) {
            return
        }
        playbackController.seekTo(segment.startMs.toInt())
    }

    private fun sourceLabel(segment: AdSegment): String = when (segment.source) {
        AdSegment.Source.DETECTED -> getString(
            R.string.ad_segment_source_detected_confidence,
            (segment.confidence * PERCENT).roundToInt()
        )
        AdSegment.Source.CHAPTER -> getString(R.string.ad_segment_source_chapter)
        AdSegment.Source.MANUAL -> getString(R.string.ad_segment_source_manual)
    }

    private inner class SegmentsAdapter : RecyclerView.Adapter<SegmentsAdapter.ViewHolder>() {

        private val segments = mutableListOf<AdSegment>()

        fun submit(newSegments: List<AdSegment>) {
            segments.clear()
            segments.addAll(newSegments)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(
                AdSegmentListItemBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val segment = segments[position]
            holder.binding.rangeLabel.text = getString(
                R.string.ad_segment_range,
                Converter.getDurationStringLong(segment.startMs.toInt()),
                Converter.getDurationStringLong(segment.endMs.toInt())
            )
            holder.binding.detailLabel.text = getString(
                R.string.ad_segment_summary,
                Converter.getDurationStringShort(segment.durationMs.toInt(), false),
                sourceLabel(segment)
            )
            // rebinding a recycled row must not fire the listener of the row it used to show
            holder.binding.enabledSwitch.setOnCheckedChangeListener(null)
            holder.binding.enabledSwitch.isChecked = segment.isEnabled
            holder.binding.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                segment.isEnabled = isChecked
                DBWriter.setAdSegmentEnabled(segment.id, feedItemId, isChecked)
            }
            holder.binding.deleteButton.setOnClickListener {
                DBWriter.deleteAdSegment(segment.id, feedItemId)
            }
            holder.binding.root.setOnClickListener { seekTo(segment) }
        }

        override fun getItemCount(): Int = segments.size

        inner class ViewHolder(val binding: AdSegmentListItemBinding) :
            RecyclerView.ViewHolder(binding.root)
    }

    companion object {
        const val TAG = "AdSegmentsDialog"
        private const val ARG_FEED_ITEM_ID = "allen.town.podcast.extra.adSegmentsFeedItemId"
        private const val PERCENT = 100

        @JvmStatic
        fun newInstance(feedItemId: Long): AdSegmentsDialog {
            val dialog = AdSegmentsDialog()
            dialog.arguments = Bundle().apply { putLong(ARG_FEED_ITEM_ID, feedItemId) }
            return dialog
        }
    }
}
