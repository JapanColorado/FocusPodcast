package allen.town.podcast.dialog

import allen.town.podcast.common.util.TopSnackbarUtil.showSnack
import allen.town.podcast.common.views.ItemOffsetDecoration
import allen.town.podcast.R
import allen.town.podcast.core.feed.util.PlayableFeedPreferences
import allen.town.podcast.core.feed.util.PlaybackSpeedUtils
import allen.town.podcast.core.pref.Prefs
import allen.town.podcast.core.util.playback.PlaybackController
import allen.town.podcast.databinding.SpeedSelectDialogBinding
import allen.town.podcast.event.playback.SpeedChangedEvent
import allen.town.podcast.event.settings.SpeedPresetChangedEvent
import allen.town.podcast.model.feed.FeedPreferences
import allen.town.podcast.model.playback.MediaType
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.util.Consumer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

/**
 * Speed chooser with the (global) list of speed presets. Opened from the player it changes the
 * speed of what is playing, which for an episode of a subscribed podcast becomes that podcast's
 * own speed, and offers "Reset to default" while the podcast has one. Opened with
 * [newGlobalDefaultInstance] from Settings it edits the global default speed instead and does not
 * touch the playing podcast's own speed.
 */
class PlaySpeedDialog : BottomSheetDialogFragment() {
    private lateinit var adapter: SpeedSelectionAdapter
    private val speedFormat: DecimalFormat
    private var controller: PlaybackController? = null
    private val selectedSpeeds: MutableList<Float>
    private var _binding: SpeedSelectDialogBinding? = null
    private val binding get() = _binding ?: error("binding accessed outside of view lifecycle")
    private val uiHandler = Handler(Looper.getMainLooper())

    /** The slider also reports programmatic updates; those must not be stored as a user choice. */
    private var ignoreSliderChanges = false

    private val globalDefaultMode: Boolean
        get() = arguments?.getBoolean(ARG_GLOBAL_DEFAULT) == true

    override fun onStart() {
        super.onStart()
        if (globalDefaultMode) {
            return
        }
        val playbackController = object : PlaybackController(requireActivity()) {
            override fun loadMediaInfo() {
                showSpeedOfPlayingMedia()
            }
        }
        controller = playbackController
        playbackController.init()
        EventBus.getDefault().register(this)
        showSpeedOfPlayingMedia()
    }

    override fun onStop() {
        super.onStop()
        controller?.release()
        controller = null
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
    }

    override fun onDestroyView() {
        uiHandler.removeCallbacksAndMessages(null)
        super.onDestroyView()
        _binding = null
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun updateSpeed(event: SpeedChangedEvent) {
        showSpeed(event.newSpeed)
        updateResetButton()
    }

    private fun showSpeed(speed: Float) {
        _binding?.let {
            ignoreSliderChanges = true
            try {
                it.speedSeekBar.updateSpeed(speed)
            } finally {
                ignoreSliderChanges = false
            }
            it.addCurrentSpeedChip.text = speedFormat.format(speed.toDouble())
        }
    }

    /** Shows the configured speed of what is playing: its podcast's own speed or the default. */
    private fun showSpeedOfPlayingMedia() {
        val playbackController = controller ?: return
        showSpeed(PlaybackSpeedUtils.getCurrentPlaybackSpeed(playbackController.media))
        updateResetButton()
    }

    private fun updateResetButton() {
        val binding = _binding ?: return
        val feedPreferences = if (globalDefaultMode) null else PlayableFeedPreferences.of(controller?.media)
        binding.resetSpeed.visibility =
            if (PlaybackSpeedUtils.hasOwnSpeed(feedPreferences)) View.VISIBLE else View.GONE
    }

    /** Stores and applies a speed the user picked. */
    private fun applySpeed(speed: Float) {
        if (globalDefaultMode) {
            Prefs.setPlaybackSpeed(speed)
            // Podcasts that follow the default pick the new speed up while playing.
            EventBus.getDefault().post(SpeedPresetChangedEvent(FeedPreferences.SPEED_USE_GLOBAL, 0))
            _binding?.addCurrentSpeedChip?.text = speedFormat.format(speed.toDouble())
        } else {
            controller?.setPlaybackSpeed(speed)
            updateResetButton()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = SpeedSelectDialogBinding.inflate(inflater)
        _binding = binding
        binding.speedSeekBar.setProgressChangedListener(Consumer { multiplier: Float? ->
            if (!ignoreSliderChanges && multiplier != null) {
                applySpeed(multiplier)
            }
        })
        binding.selectedSpeedsGrid.layoutManager = GridLayoutManager(context, 4)
        binding.selectedSpeedsGrid.addItemDecoration(ItemOffsetDecoration(requireContext(), 4))
        adapter = SpeedSelectionAdapter()
        adapter.setHasStableIds(true)
        binding.selectedSpeedsGrid.adapter = adapter
        binding.addCurrentSpeedChip.isCloseIconVisible = true
        binding.addCurrentSpeedChip.setCloseIconResource(R.drawable.ic_add)
        binding.addCurrentSpeedChip.setOnCloseIconClickListener { addCurrentSpeed() }
        binding.addCurrentSpeedChip.setOnClickListener { addCurrentSpeed() }
        binding.resetSpeed.setOnClickListener {
            controller?.resetPlaybackSpeed()
            updateResetButton()
        }
        if (globalDefaultMode) {
            binding.speedDialogTitle.setText(R.string.default_playback_speed)
            showSpeed(Prefs.getPlaybackSpeed(MediaType.AUDIO))
        }
        return binding.root
    }

    private fun addCurrentSpeed() {
        val newSpeed = _binding?.speedSeekBar?.currentSpeed ?: return
        if (selectedSpeeds.contains(newSpeed)) {
            showSnack(
                activity,
                getString(R.string.preset_already_exists, newSpeed),
                Toast.LENGTH_LONG
            )
        } else {
            selectedSpeeds.add(newSpeed)
            Collections.sort(selectedSpeeds)
            Prefs.playbackSpeedArray = selectedSpeeds
            adapter.notifyDataSetChanged()
        }
    }

    inner class SpeedSelectionAdapter : RecyclerView.Adapter<SpeedSelectionAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val entryView = inflater.inflate(R.layout.single_assist_chip, parent, false)
            val chip = entryView.findViewById<Chip>(R.id.chip)
            chip.textAlignment = View.TEXT_ALIGNMENT_CENTER
            return ViewHolder(entryView as Chip)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val speed = selectedSpeeds[position]
            holder.chip.text = speedFormat.format(speed.toDouble())
            holder.chip.setOnLongClickListener {
                selectedSpeeds.remove(speed)
                Prefs.playbackSpeedArray = selectedSpeeds
                notifyDataSetChanged()
                true
            }
            holder.chip.setOnClickListener {
                uiHandler.postDelayed(
                    {
                        applySpeed(speed)
                        dismiss()
                    }, 200
                )
            }
        }

        override fun getItemCount(): Int {
            return selectedSpeeds.size
        }

        override fun getItemId(position: Int): Long {
            return selectedSpeeds[position].hashCode().toLong()
        }

        inner class ViewHolder internal constructor(var chip: Chip) : RecyclerView.ViewHolder(
            chip
        )
    }

    init {
        val format = DecimalFormatSymbols(Locale.US)
        format.decimalSeparator = '.'
        speedFormat = DecimalFormat("0.0", format)
        selectedSpeeds = ArrayList(Prefs.playbackSpeedArray)
    }

    companion object {
        private const val ARG_GLOBAL_DEFAULT = "global_default"

        /** The speed chooser for Settings: edits the global default speed for audio. */
        @JvmStatic
        fun newGlobalDefaultInstance(): PlaySpeedDialog {
            val dialog = PlaySpeedDialog()
            dialog.arguments = Bundle().apply { putBoolean(ARG_GLOBAL_DEFAULT, true) }
            return dialog
        }
    }
}
