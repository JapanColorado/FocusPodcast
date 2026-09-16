package allen.town.podcast.dialog

import allen.town.focus_common.util.TopSnackbarUtil.showSnack
import allen.town.focus_common.views.ItemOffsetDecoration
import allen.town.podcast.R
import allen.town.podcast.core.pref.Prefs
import allen.town.podcast.core.util.playback.PlaybackController
import allen.town.podcast.databinding.SpeedSelectDialogBinding
import allen.town.podcast.event.playback.SpeedChangedEvent
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

class PlaySpeedDialog : BottomSheetDialogFragment() {
    private lateinit var adapter: SpeedSelectionAdapter
    private val speedFormat: DecimalFormat
    private var controller: PlaybackController? = null
    private val selectedSpeeds: MutableList<Float>
    private var _binding: SpeedSelectDialogBinding? = null
    private val binding get() = _binding ?: error("binding accessed outside of view lifecycle")
    private val uiHandler = Handler(Looper.getMainLooper())
    override fun onStart() {
        super.onStart()
        val playbackController = object : PlaybackController(requireActivity()) {
            override fun loadMediaInfo() {
                updateSpeed(SpeedChangedEvent(currentPlaybackSpeedMultiplier))
            }
        }
        controller = playbackController
        playbackController.init()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        controller?.release()
        controller = null
        EventBus.getDefault().unregister(this)
    }

    override fun onDestroyView() {
        uiHandler.removeCallbacksAndMessages(null)
        super.onDestroyView()
        _binding = null
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun updateSpeed(event: SpeedChangedEvent) {
        _binding?.let {
            it.speedSeekBar.updateSpeed(event.newSpeed)
            it.addCurrentSpeedChip.text = speedFormat.format(event.newSpeed.toDouble())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = SpeedSelectDialogBinding.inflate(inflater)
        _binding = binding
        binding.speedSeekBar.setProgressChangedListener(Consumer { multiplier: Float? ->
            val playbackController = controller
            if (playbackController != null && multiplier != null) {
                playbackController.setPlaybackSpeed(multiplier)
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
        val speed = Prefs.getPlaybackSpeed(MediaType.AUDIO)
        updateSpeed(SpeedChangedEvent(speed))
        return binding.root
    }

    private fun addCurrentSpeed() {
        val newSpeed = controller?.currentPlaybackSpeedMultiplier ?: return
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
                        val playbackController = controller
                        if (playbackController != null) {
                            dismiss()
                            playbackController.setPlaybackSpeed(speed)
                        }
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
}
