package allen.town.podcast.dialog

import allen.town.podcast.common.util.Timber
import allen.town.podcast.common.util.TopSnackbarUtil.showSnack
import allen.town.podcast.common.views.AccentMaterialDialog
import allen.town.podcast.common.views.ItemOffsetDecoration
import allen.town.podcast.R
import allen.town.podcast.core.pref.SleepTimerPreferences
import allen.town.podcast.core.service.playback.PlaybackService
import allen.town.podcast.core.util.Converter
import allen.town.podcast.core.util.playback.PlaybackController
import allen.town.podcast.databinding.TimeDialogBinding
import allen.town.podcast.event.playback.SleepTimerUpdatedEvent
import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import com.beloo.widget.chipslayoutmanager.ChipsLayoutManager
import com.google.android.material.chip.Chip
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class SleepTimerDialog : DialogFragment() {
    private var controller: PlaybackController? = null
    private var _binding: TimeDialogBinding? = null
    private lateinit var adapter: TimesAdapter
    var spinnerContent = arrayOf(
        "5",
        "10",
        "15",
        "30",
        "45",
        "60",
        "120"
    )

    override fun onStart() {
        super.onStart()
        val playbackController = object : PlaybackController(requireActivity()) {
            override fun loadMediaInfo() {}
        }
        controller = playbackController
        playbackController.init()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        controller?.release()
        EventBus.getDefault().unregister(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = TimeDialogBinding.inflate(layoutInflater)
        _binding = binding
        val builder: AlertDialog.Builder = AccentMaterialDialog(
            requireContext(),
            R.style.MaterialAlertDialogTheme
        )
        builder.setTitle(R.string.sleep_timer_label)
        builder.setView(binding.root)
        builder.setPositiveButton(R.string.close_label, null)
        binding.timeDisplay.visibility = View.GONE
        binding.extendSleepFiveMinutesButton.text = getString(R.string.extend_sleep_timer_label, 5)
        binding.extendSleepTenMinutesButton.text = getString(R.string.extend_sleep_timer_label, 10)
        binding.extendSleepTwentyMinutesButton.text =
            getString(R.string.extend_sleep_timer_label, 20)
        binding.extendSleepFiveMinutesButton.setOnClickListener {
            controller?.extendSleepTimer((5 * 1000 * 60).toLong())
        }
        binding.extendSleepTenMinutesButton.setOnClickListener {
            controller?.extendSleepTimer((10 * 1000 * 60).toLong())
        }
        binding.extendSleepTwentyMinutesButton.setOnClickListener {
            controller?.extendSleepTimer((20 * 1000 * 60).toLong())
        }
        for (i in spinnerContent.indices) {
            if (spinnerContent[i] == SleepTimerPreferences.lastTimerValue()) {
                selectedIndex = i
            }
        }
        //https://github.com/BelooS/ChipsLayoutManager
        val chipsLayoutManager = ChipsLayoutManager.newBuilder(context).build()
        binding.timesRecyclerView.layoutManager = chipsLayoutManager
        binding.timesRecyclerView.addItemDecoration(ItemOffsetDecoration(requireContext(), 4))
        adapter = TimesAdapter()
        adapter.setHasStableIds(true)
        binding.timesRecyclerView.adapter = adapter
        binding.cbShakeToReset.isChecked = SleepTimerPreferences.shakeToReset()
        binding.cbVibrate.isChecked = SleepTimerPreferences.vibrate()
        binding.chAutoEnable.isChecked = SleepTimerPreferences.autoEnable()
        binding.cbShakeToReset.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            SleepTimerPreferences.setShakeToReset(
                isChecked
            )
        }
        binding.cbVibrate.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            SleepTimerPreferences.setVibrate(
                isChecked
            )
        }
        binding.chAutoEnable.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            SleepTimerPreferences.setAutoEnable(
                isChecked
            )
        }
        binding.disableSleeptimerButton.setOnClickListener {
            controller?.disableSleepTimer()
        }
        binding.setSleeptimerButton.setOnClickListener {
            if (!PlaybackService.isRunning) {
                showSnack(activity, R.string.no_media_playing_label, Toast.LENGTH_LONG)
                return@setOnClickListener
            }
            try {
                val time = spinnerContent[selectedIndex].toLong()
                if (time == 0L) {
                    throw NumberFormatException("Timer must not be zero")
                }
                SleepTimerPreferences.setLastTimer(spinnerContent[selectedIndex])
                controller?.setSleepTimer(SleepTimerPreferences.timerMillis())
                closeKeyboard(binding.root)
            } catch (e: NumberFormatException) {
                Timber.w(e, "the sleep timer input is not a usable number")
                showSnack(activity, R.string.time_dialog_invalid_input, Toast.LENGTH_LONG)
            }
        }
        return builder.create()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun timerUpdated(event: SleepTimerUpdatedEvent) {
        val binding = _binding ?: return
        binding.timeDisplay.visibility =
            if (event.isOver || event.isCancelled) View.GONE else View.VISIBLE
        binding.timeSetup.visibility =
            if (event.isOver || event.isCancelled) View.VISIBLE else View.GONE
        binding.time.text =
            Converter.getDurationStringLong(event.timeLeft.toInt())
    }

    private fun closeKeyboard(content: View) {
        val imm = requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(content.windowToken, 0)
    }

    var selectedIndex = 0

    internal inner class TimesAdapter : RecyclerView.Adapter<TimesAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val entryView = inflater.inflate(R.layout.single_tag_chip, parent, false)
            val chip = entryView.findViewById<Chip>(R.id.chip)
            return ViewHolder(entryView as Chip)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.chip.text = spinnerContent[position] + getString(R.string.time_minutes)
            holder.chip.isChecked = selectedIndex == position
            holder.chip.isCheckedIconVisible = holder.chip.isChecked
            holder.chip.setOnClickListener {
                // the row can be detached by the time the tap lands
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || pos >= spinnerContent.size) {
                    return@setOnClickListener
                }
                selectedIndex = pos
                notifyDataSetChanged()
            }
        }

        override fun getItemCount(): Int {
            return spinnerContent.size
        }

        override fun getItemId(position: Int): Long {
            return spinnerContent[position].hashCode().toLong()
        }

        inner class ViewHolder internal constructor(var chip: Chip) : RecyclerView.ViewHolder(
            chip
        )
    }
}
