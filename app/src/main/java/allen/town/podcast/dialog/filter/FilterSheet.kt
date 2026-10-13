package allen.town.podcast.dialog.filter

import allen.town.podcast.R
import allen.town.podcast.common.extensions.accentColor
import allen.town.podcast.common.util.BasePreferenceUtil.materialYou
import allen.town.podcast.databinding.FilterSheetBinding
import android.app.Dialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.annotation.WorkerThread
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

/** One chip of a [FilterSection]; [key] is what the selection reports for it. */
data class FilterOption(val key: String, val label: CharSequence)

/**
 * A labelled row of chips. Single-select sections may be left empty (tapping the checked chip
 * clears it) unless [selectionRequired]; [multiSelect] sections allow any number of chips.
 */
data class FilterSection(
    val key: String,
    val title: CharSequence,
    val options: List<FilterOption>,
    val multiSelect: Boolean = false,
    val selectionRequired: Boolean = false
)

/** Section key to the keys of its checked options; sections with nothing checked may be absent. */
typealias FilterSelection = Map<String, Set<String>>

/**
 * The bottom sheet every sort / filter UI uses: a title with a Reset button, the [FilterSection]s
 * as chip groups, and a footer saying how many items the current choice shows. Every tap applies
 * at once ([onSelectionChanged]); there is no confirm step.
 *
 * Subclasses build the sections, read the stored selection and store changes. They must be able
 * to do all of that from their arguments and stored state alone, because the system recreates the
 * sheet on rotation; as the choice is stored on every tap, reading it back gives the current one.
 */
abstract class FilterSheet : BottomSheetDialogFragment() {
    private var _binding: FilterSheetBinding? = null
    private val groups = LinkedHashMap<String, ChipGroup>()
    private val optionKeys = HashMap<Int, String>()
    private val chipIds = HashMap<String, HashMap<String, Int>>()
    private var settingChips = false
    private var countDisposable: Disposable? = null

    @get:StringRes
    protected open val titleRes: Int
        get() = R.string.filter

    /** Plurals with one %d for the footer, e.g. "N episodes shown". */
    @get:PluralsRes
    protected abstract val countPluralsRes: Int

    protected abstract fun buildSections(): List<FilterSection>

    /** The stored choice. */
    protected abstract fun currentSelection(): FilterSelection

    /** What Reset goes back to; nothing checked unless a section requires a choice. */
    protected open fun defaultSelection(): FilterSelection = emptyMap()

    /** Stores and applies a choice the user just made. */
    protected abstract fun onSelectionChanged(selection: FilterSelection)

    /** How many items [selection] shows. Runs off the main thread. */
    @WorkerThread
    protected abstract fun countShown(selection: FilterSelection): Int

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        // fully open, so the footer with the count is visible right away
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FilterSheetBinding.inflate(inflater, container, false)
        _binding = binding
        binding.filterSheetTitle.setText(titleRes)
        for (section in buildSections()) {
            if (section.options.isNotEmpty()) {
                addSection(inflater, binding, section)
            }
        }
        setChips(currentSelection())
        binding.filterSheetReset.setOnClickListener {
            setChips(defaultSelection())
            selectionChanged()
        }
        updateResetButton()
        refreshCount()
        return binding.root
    }

    override fun onDestroyView() {
        countDisposable?.dispose()
        groups.clear()
        optionKeys.clear()
        chipIds.clear()
        _binding = null
        super.onDestroyView()
    }

    private fun addSection(inflater: LayoutInflater, binding: FilterSheetBinding, section: FilterSection) {
        val view = inflater.inflate(R.layout.filter_sheet_section, binding.filterSheetSections, false)
        view.findViewById<TextView>(R.id.filterSectionTitle).text = section.title
        val group = view.findViewById<ChipGroup>(R.id.filterSectionChips)
        group.isSingleSelection = !section.multiSelect
        group.isSelectionRequired = section.selectionRequired
        val ids = HashMap<String, Int>()
        for (option in section.options) {
            val chip = inflater.inflate(R.layout.filter_sheet_chip, group, false) as Chip
            chip.id = View.generateViewId()
            chip.text = option.label
            tintChip(chip)
            group.addView(chip)
            optionKeys[chip.id] = option.key
            ids[option.key] = chip.id
        }
        group.setOnCheckedStateChangeListener { _, _ ->
            if (!settingChips) {
                selectionChanged()
            }
        }
        groups[section.key] = group
        chipIds[section.key] = ids
        binding.filterSheetSections.addView(view)
    }

    /** Checked chips in the accent colour, like the app's other toggles (Material You keeps its own). */
    private fun tintChip(chip: Chip) {
        if (materialYou) {
            return
        }
        val accent = chip.context.accentColor()
        val checkedStates = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        chip.checkedIconTint = ColorStateList.valueOf(accent)
        val defaultStroke = chip.chipStrokeColor?.defaultColor ?: accent
        chip.chipStrokeColor = ColorStateList(checkedStates, intArrayOf(accent, defaultStroke))
    }

    private fun setChips(selection: FilterSelection) {
        settingChips = true
        try {
            for ((sectionKey, group) in groups) {
                val wanted = selection[sectionKey].orEmpty()
                val ids = chipIds[sectionKey].orEmpty()
                // clear first: a required single-select group refuses to uncheck its last chip
                for ((optionKey, id) in ids) {
                    if (optionKey !in wanted) {
                        group.findViewById<Chip>(id)?.isChecked = false
                    }
                }
                for ((optionKey, id) in ids) {
                    if (optionKey in wanted) {
                        group.findViewById<Chip>(id)?.isChecked = true
                    }
                }
            }
        } finally {
            settingChips = false
        }
    }

    /** The choice the chips show. */
    protected fun selection(): FilterSelection {
        val result = LinkedHashMap<String, Set<String>>()
        for ((sectionKey, group) in groups) {
            val keys = group.checkedChipIds.mapNotNull { optionKeys[it] }.toSet()
            if (keys.isNotEmpty()) {
                result[sectionKey] = keys
            }
        }
        return result
    }

    private fun selectionChanged() {
        onSelectionChanged(selection())
        updateResetButton()
        refreshCount()
    }

    private fun updateResetButton() {
        val binding = _binding ?: return
        val current = selection()
        val defaults = defaultSelection().filterValues { it.isNotEmpty() }
        binding.filterSheetReset.isEnabled = current != defaults
    }

    /** Recounts the footer, e.g. after a choice the sheet keeps outside its chips changed. */
    protected fun refreshCount() {
        val selection = selection()
        countDisposable?.dispose()
        countDisposable = Single.fromCallable { countShown(selection) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { count: Int ->
                    _binding?.filterSheetCount?.text =
                        resources.getQuantityString(countPluralsRes, count, count)
                },
                { error: Throwable -> Log.e(TAG, "counting the filtered items failed", error) }
            )
    }

    companion object {
        private const val TAG = "FilterSheet"

        /** The single checked option of a section, or null. */
        fun FilterSelection.single(sectionKey: String): String? = this[sectionKey]?.firstOrNull()
    }
}
