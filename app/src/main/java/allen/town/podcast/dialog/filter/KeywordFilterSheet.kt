package allen.town.podcast.dialog.filter

import allen.town.podcast.R
import allen.town.podcast.core.storage.DBWriter
import allen.town.podcast.databinding.KeywordFilterSheetBinding
import allen.town.podcast.model.feed.FeedFilter
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip

/**
 * A podcast's auto-download keyword filter in the look of the filter sheets: Include / Exclude,
 * the terms as removable chips, and a minimum duration. Unlike the other sheets nothing applies
 * until Save, since this changes what gets downloaded automatically. The stored [FeedFilter]
 * format is unchanged: quoted terms in either the include or the exclude list, and the minimum
 * duration in seconds (-1 for none).
 */
class KeywordFilterSheet : BottomSheetDialogFragment() {

    /** The feed settings screen; it stores the filter with the rest of the feed's preferences. */
    interface Host {
        fun onKeywordFilterSaved(filter: FeedFilter)
    }

    private var _binding: KeywordFilterSheetBinding? = null
    private val terms = ArrayList<String>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = KeywordFilterSheetBinding.inflate(inflater, container, false)
        _binding = binding
        val filter = storedFilter()
        val exclude: Boolean
        if (savedInstanceState != null) {
            terms += savedInstanceState.getStringArrayList(STATE_TERMS).orEmpty()
            exclude = savedInstanceState.getBoolean(STATE_EXCLUDE)
        } else {
            exclude = filter.excludeOnly()
            terms += if (exclude) filter.excludeFilter else filter.includeFilter
            if (filter.hasMinimalDurationFilter()) {
                // stored in seconds, shown in minutes
                binding.keywordDurationText.setText((filter.minimalDurationFilter / 60).toString())
            }
            binding.keywordDurationSwitch.isChecked = filter.hasMinimalDurationFilter()
        }
        binding.keywordModeGroup.check(if (exclude) R.id.keywordExclude else R.id.keywordInclude)
        binding.keywordDurationInput.isEnabled = binding.keywordDurationSwitch.isChecked
        binding.keywordDurationSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.keywordDurationInput.isEnabled = isChecked
            if (isChecked) {
                binding.keywordDurationText.requestFocus()
            }
        }

        binding.keywordInput.setEndIconOnClickListener { addTypedTerm() }
        binding.keywordInputText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTypedTerm()
                true
            } else {
                false
            }
        }
        showTerms()

        binding.keywordCancel.setOnClickListener { dismiss() }
        binding.keywordSave.setOnClickListener {
            save()
            dismiss()
        }
        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_TERMS, ArrayList(terms))
        _binding?.let { outState.putBoolean(STATE_EXCLUDE, it.keywordModeGroup.checkedButtonId == R.id.keywordExclude) }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    @Suppress("DEPRECATION") // detekt: getSerializable(String, Class) needs API 33
    private fun storedFilter(): FeedFilter =
        arguments?.getSerializable(ARG_FILTER) as? FeedFilter ?: FeedFilter()

    private fun addTypedTerm() {
        val binding = _binding ?: return
        val term = binding.keywordInputText.text?.toString().orEmpty().replace("\"", "").trim()
        if (term.isEmpty() || terms.contains(term)) {
            return
        }
        terms += term
        binding.keywordInputText.setText("")
        showTerms()
    }

    private fun showTerms() {
        val binding = _binding ?: return
        val group = binding.keywordTerms
        group.removeAllViews()
        val inflater = LayoutInflater.from(group.context)
        for (term in terms) {
            val chip = inflater.inflate(R.layout.keyword_term_chip, group, false) as Chip
            chip.text = term
            chip.closeIconContentDescription = getString(R.string.episode_filters_remove_term, term)
            chip.setOnCloseIconClickListener {
                terms.remove(term)
                showTerms()
            }
            group.addView(chip)
        }
        group.visibility = if (terms.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun save() {
        val binding = _binding ?: return
        // a term typed but not added yet is what the user meant to add
        addTypedTerm()
        val minutes = binding.keywordDurationText.text?.toString()?.toIntOrNull()
        val minimalDuration = if (binding.keywordDurationSwitch.isChecked && minutes != null && minutes > 0) {
            minutes * 60
        } else {
            -1
        }
        val termString = terms.joinToString("") { "\"$it\" " }
        val exclude = binding.keywordModeGroup.checkedButtonId == R.id.keywordExclude
        val filter = FeedFilter(
            if (exclude) "" else termString,
            if (exclude) termString else "",
            minimalDuration
        )
        val host = parentFragment as? Host
        if (host != null) {
            host.onKeywordFilterSaved(filter)
        } else {
            val feedId = arguments?.getLong(ARG_FEED_ID) ?: return
            DBWriter.updateFeedPreferences(feedId) { it.filter = filter }
        }
    }

    companion object {
        const val TAG = "KeywordFilterSheet"
        private const val ARG_FEED_ID = "feed_id"
        private const val ARG_FILTER = "filter"
        private const val STATE_TERMS = "terms"
        private const val STATE_EXCLUDE = "exclude"

        /** Show it from the [Host]'s child fragment manager. */
        fun newInstance(feedId: Long, filter: FeedFilter): KeywordFilterSheet {
            val sheet = KeywordFilterSheet()
            sheet.arguments = Bundle().apply {
                putLong(ARG_FEED_ID, feedId)
                putSerializable(ARG_FILTER, filter)
            }
            return sheet
        }
    }
}
