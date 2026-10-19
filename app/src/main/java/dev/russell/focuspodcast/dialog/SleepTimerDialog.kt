package dev.russell.focuspodcast.dialog

import dev.russell.focuspodcast.common.util.Timber
import dev.russell.focuspodcast.common.util.TopSnackbarUtil.showSnack
import dev.russell.focuspodcast.common.views.AccentMaterialDialog
import dev.russell.focuspodcast.common.views.ItemOffsetDecoration
import dev.russell.focuspodcast.R
import dev.russell.focuspodcast.core.pref.SleepTimerPreferences
import dev.russell.focuspodcast.core.service.playback.PlaybackService
import dev.russell.focuspodcast.core.service.playback.SleepTimerEndCondition
import dev.russell.focuspodcast.core.util.Converter
import dev.russell.focuspodcast.core.util.playback.PlaybackController
import dev.russell.focuspodcast.databinding.TimeDialogBinding
import dev.russell.focuspodcast.event.playback.SleepTimerMode
import dev.russell.focuspodcast.event.playback.SleepTimerUpdatedEvent
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
    private val spinnerContent = arrayOf(
        "5",
        "10",
        "15",
        "30",
        "45",
        "60",
        "120"
    )

    /** One chip of the dialog: a duration in minutes, or one of the boundary modes. */
    private data class TimerChoice(val mode: SleepTimerMode, val minutes: String? = null)

    /** The chips: every duration, "end of episode", and "end of chapter" if the media has chapters. */
    private var choices: List<TimerChoice> = buildChoices(false)
    private var selected: TimerChoice = choices.first()
    /** True once the user tapped a chip, so a later refresh keeps their choice. */
    private var userPicked = false

    override fun onStart() {
        super.onStart()
        val playbackController = object : PlaybackController(requireActivity()) {
            override fun loadMediaInfo() {
                refreshChoices()
            }
        }
        controller = playbackController
        playbackController.init()
        EventBus.getDefault().register(this)
    }

    private fun buildChoices(withChapters: Boolean): List<TimerChoice> {
        val list = spinnerContent.map { TimerChoice(SleepTimerMode.DURATION, it) }.toMutableList()
        list.add(TimerChoice(SleepTimerMode.END_OF_EPISODE))
        if (withChapters) {
            list.add(TimerChoice(SleepTimerMode.END_OF_CHAPTER))
        }
        return list
    }

    /** The choice last used, or the first duration if it is not offered (no chapters). */
    private fun rememberedChoice(): TimerChoice {
        val mode = SleepTimerPreferences.lastTimerMode()
        val wanted = if (mode == SleepTimerMode.DURATION) {
            TimerChoice(mode, SleepTimerPreferences.lastTimerValue())
        } else {
            TimerChoice(mode)
        }
        return choices.firstOrNull { it == wanted }
            ?: choices.firstOrNull { it.minutes == SleepTimerPreferences.lastTimerValue() }
            ?: choices.first()
    }

    /**
     * Called once the controller knows the playing media: offers "end of chapter" only if that
     * media has chapter boundaries, and shows a timer that is already running (its events only
     * arrive while playing).
     */
    private fun refreshChoices() {
        val playbackController = controller ?: return
        val hasChapters = SleepTimerEndCondition.hasChapterBoundaries(playbackController.media?.chapters)
        val newChoices = buildChoices(hasChapters)
        if (newChoices != choices) {
            choices = newChoices
            // the remembered choice may be "end of chapter", which is only offered now
            selected = if (userPicked && selected in choices) selected else rememberedChoice()
            if (::adapter.isInitialized) {
                adapter.notifyDataSetChanged()
            }
        }
        val binding = _binding ?: return
        if (playbackController.sleepTimerActive()) {
            showActive(binding, playbackController.getSleepTimerMode(), playbackController.sleepTimerTimeLeft)
        }
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
        selected = rememberedChoice()
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
            val choice = selected
            if (choice.mode != SleepTimerMode.DURATION) {
                SleepTimerPreferences.setLastTimerMode(choice.mode)
                controller?.setSleepTimer(choice.mode)
                return@setOnClickListener
            }
            try {
                val minutes = choice.minutes ?: throw NumberFormatException("No duration chosen")
                val time = minutes.toLong()
                if (time == 0L) {
                    throw NumberFormatException("Timer must not be zero")
                }
                SleepTimerPreferences.setLastTimer(minutes)
                SleepTimerPreferences.setLastTimerMode(SleepTimerMode.DURATION)
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
        if (event.isOver || event.isCancelled) {
            binding.timeDisplay.visibility = View.GONE
            binding.timeSetup.visibility = View.VISIBLE
            return
        }
        showActive(binding, event.mode, event.timeLeft)
    }

    /**
     * The running timer: the time left (wall-clock, so already divided by the speed for the
     * boundary modes), what it stops at, and the "+N min" buttons for a duration timer only.
     */
    private fun showActive(binding: TimeDialogBinding, mode: SleepTimerMode?, timeLeft: Long) {
        binding.timeDisplay.visibility = View.VISIBLE
        binding.timeSetup.visibility = View.GONE
        val known = timeLeft != SleepTimerUpdatedEvent.TIME_LEFT_UNKNOWN
        binding.time.visibility = if (known) View.VISIBLE else View.GONE
        if (known) {
            binding.time.text = Converter.getDurationStringLong(timeLeft.toInt())
        }
        val modeText = when (mode) {
            SleepTimerMode.END_OF_EPISODE -> getString(R.string.sleep_timer_stops_end_of_episode)
            SleepTimerMode.END_OF_CHAPTER -> getString(R.string.sleep_timer_stops_end_of_chapter)
            else -> null
        }
        binding.timeMode.visibility = if (modeText != null) View.VISIBLE else View.GONE
        binding.timeMode.text = modeText
        binding.extendButtons.visibility =
            if (mode == null || mode == SleepTimerMode.DURATION) View.VISIBLE else View.GONE
    }

    private fun closeKeyboard(content: View) {
        val imm = requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(content.windowToken, 0)
    }

    internal inner class TimesAdapter : RecyclerView.Adapter<TimesAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val entryView = inflater.inflate(R.layout.single_tag_chip, parent, false)
            return ViewHolder(entryView as Chip)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val choice = choices[position]
            holder.chip.text = when (choice.mode) {
                SleepTimerMode.END_OF_EPISODE -> getString(R.string.sleep_timer_end_of_episode)
                SleepTimerMode.END_OF_CHAPTER -> getString(R.string.sleep_timer_end_of_chapter)
                SleepTimerMode.DURATION -> {
                    val minutes = choice.minutes?.toIntOrNull()
                    if (minutes != null) {
                        resources.getQuantityString(R.plurals.time_minutes_quantified, minutes, minutes)
                    } else {
                        "${choice.minutes} ${getString(R.string.time_minutes)}"
                    }
                }
            }
            holder.chip.isChecked = selected == choice
            holder.chip.isCheckedIconVisible = holder.chip.isChecked
            holder.chip.setOnClickListener {
                // the row can be detached by the time the tap lands
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || pos >= choices.size) {
                    return@setOnClickListener
                }
                selected = choices[pos]
                userPicked = true
                notifyDataSetChanged()
            }
        }

        override fun getItemCount(): Int {
            return choices.size
        }

        override fun getItemId(position: Int): Long {
            return choices[position].hashCode().toLong()
        }

        inner class ViewHolder internal constructor(var chip: Chip) : RecyclerView.ViewHolder(
            chip
        )
    }
}
