package allen.town.podcast.dialog.filter

import allen.town.podcast.R
import allen.town.podcast.common.views.AccentMaterialDialog
import allen.town.podcast.core.feed.EpisodeFilterOptions
import allen.town.podcast.core.storage.DBReader
import allen.town.podcast.core.storage.FeedSearcher
import allen.town.podcast.dialog.filter.FilterSheet.Companion.single
import allen.town.podcast.model.feed.EpisodeSearchOptions
import allen.town.podcast.model.feed.Feed
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

/**
 * "Sort & filter" of the episode search: sort order, where to look for the words, which podcast
 * (a chip that opens a picker of the subscriptions), date range, duration and the episode filter
 * (Status, Downloaded, Favorite, as on the episode lists). The search fragment ([Host]) holds the
 * choice, stores everything but the podcast in [allen.town.podcast.core.pref.Prefs.episodeSearchOptions]
 * and searches again. Reset keeps the podcast; its chip has its own clear button.
 */
class EpisodeSearchFilterSheet : FilterSheet() {

    /** The search fragment. */
    interface Host {
        /** The words typed. */
        val searchQuery: String

        /** The current choice, including the podcast scope. */
        val searchOptions: EpisodeSearchOptions

        /** Title of the podcast the search is scoped to, null when it is not. */
        val searchScopeTitle: String?

        fun onSearchOptionsChanged(options: EpisodeSearchOptions, scopeTitle: String?)
    }

    private val host: Host?
        get() = parentFragment as? Host

    // captured on the main thread, read by the count off it
    @Volatile
    private var query: String = ""
    @Volatile
    private var feedId: Long = 0
    private var feedTitle: String? = null
    private var scopeChip: Chip? = null
    private var feedsDisposable: Disposable? = null

    override val titleRes: Int
        get() = R.string.sort_and_filter

    override val countPluralsRes: Int
        get() = R.plurals.filter_episodes_shown

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        host?.let {
            query = it.searchQuery
            feedId = it.searchOptions.feedId
            feedTitle = it.searchScopeTitle
        }
        val root = super.onCreateView(inflater, container, savedInstanceState)
        val sections = root.findViewById<LinearLayout>(R.id.filterSheetSections)
        // after Sort and Search in
        sections.addView(createScopeSection(inflater, sections), SCOPE_SECTION_INDEX.coerceAtMost(sections.childCount))
        return root
    }

    override fun onDestroyView() {
        feedsDisposable?.dispose()
        scopeChip = null
        super.onDestroyView()
    }

    override fun buildSections(): List<FilterSection> {
        val context = requireContext()
        return listOf(
            FilterSection(
                SECTION_SORT, getString(R.string.sort), listOf(
                    option(EpisodeSearchOptions.Sort.RELEVANCE, R.string.search_sort_relevance),
                    option(EpisodeSearchOptions.Sort.NEWEST, R.string.search_sort_newest),
                    option(EpisodeSearchOptions.Sort.OLDEST, R.string.search_sort_oldest),
                    option(EpisodeSearchOptions.Sort.LONGEST, R.string.search_sort_longest),
                    option(EpisodeSearchOptions.Sort.SHORTEST, R.string.search_sort_shortest)
                ), selectionRequired = true
            ),
            FilterSection(
                SECTION_FIELDS, getString(R.string.search_section_search_in), listOf(
                    option(EpisodeSearchOptions.Field.TITLE, R.string.search_in_title),
                    option(EpisodeSearchOptions.Field.SHOW_NOTES, R.string.search_in_show_notes)
                ), multiSelect = true, selectionRequired = true
            ),
            FilterSection(
                SECTION_DATE, getString(R.string.search_section_published), listOf(
                    option(EpisodeSearchOptions.DateRange.PAST_WEEK, R.string.search_date_past_week),
                    option(EpisodeSearchOptions.DateRange.PAST_MONTH, R.string.search_date_past_month),
                    option(EpisodeSearchOptions.DateRange.PAST_YEAR, R.string.search_date_past_year)
                )
            ),
            FilterSection(
                SECTION_DURATION, getString(R.string.search_section_duration), listOf(
                    option(EpisodeSearchOptions.DurationRange.UNDER_20_MIN, R.string.search_duration_under_20),
                    option(EpisodeSearchOptions.DurationRange.FROM_20_TO_60_MIN, R.string.search_duration_20_to_60),
                    option(EpisodeSearchOptions.DurationRange.OVER_60_MIN, R.string.search_duration_over_60)
                )
            )
        ) + EpisodeFilterSheet.sections(context)
    }

    private fun option(value: Enum<*>, label: Int) = FilterOption(value.name, getString(label))

    override fun currentSelection(): FilterSelection =
        toSelection(host?.searchOptions ?: EpisodeSearchOptions.defaults())

    override fun defaultSelection(): FilterSelection = toSelection(EpisodeSearchOptions.defaults())

    override fun onSelectionChanged(selection: FilterSelection) {
        host?.onSearchOptionsChanged(toOptions(selection, feedId), feedTitle)
    }

    override fun countShown(selection: FilterSelection): Int =
        if (query.isBlank()) 0 else FeedSearcher.countFeedItems(query, toOptions(selection, feedId))

    /** "Podcast": one chip naming the scope; tapping it picks another, its close icon clears it. */
    private fun createScopeSection(inflater: LayoutInflater, parent: ViewGroup): View {
        val view = inflater.inflate(R.layout.filter_sheet_section, parent, false)
        view.findViewById<TextView>(R.id.filterSectionTitle).setText(R.string.search_section_podcast)
        val group = view.findViewById<ChipGroup>(R.id.filterSectionChips)
        val chip = inflater.inflate(R.layout.filter_sheet_chip, group, false) as Chip
        chip.isCheckable = false
        chip.setCloseIconResource(R.drawable.ic_close)
        chip.closeIconContentDescription = getString(R.string.search_scope_clear)
        chip.setOnClickListener { pickFeed() }
        chip.setOnCloseIconClickListener { setScope(0, null) }
        group.addView(chip)
        scopeChip = chip
        updateScopeChip()
        return view
    }

    private fun updateScopeChip() {
        val chip = scopeChip ?: return
        val scoped = feedId != 0L
        chip.text = if (scoped) feedTitle ?: getString(R.string.search_section_podcast) else getString(R.string.search_scope_all)
        chip.isCloseIconVisible = scoped
        chip.contentDescription = getString(R.string.search_scope_choose_description, chip.text)
    }

    private fun pickFeed() {
        feedsDisposable?.dispose()
        feedsDisposable = Single.fromCallable { DBReader.getFeedList().sortedBy { it.title?.lowercase() } }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { feeds: List<Feed> -> showFeedPicker(feeds) },
                { error: Throwable -> Log.e(TAG, "loading the subscriptions failed", error) }
            )
    }

    private fun showFeedPicker(feeds: List<Feed>) {
        val context = context ?: return
        val labels = listOf(getString(R.string.search_scope_all)) + feeds.map { it.title.orEmpty() }
        val checked = feeds.indexOfFirst { it.id == feedId } + 1
        AccentMaterialDialog(context, R.style.MaterialAlertDialogTheme)
            .setTitle(R.string.search_scope_choose)
            .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, which ->
                val feed = feeds.getOrNull(which - 1)
                setScope(feed?.id ?: 0, feed?.title)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel_label, null)
            .show()
    }

    private fun setScope(id: Long, title: String?) {
        // the picker may outlive the sheet's view; the chips then no longer hold the choice
        if (scopeChip == null) {
            return
        }
        feedId = id
        feedTitle = title
        updateScopeChip()
        host?.onSearchOptionsChanged(toOptions(selection(), id), title)
        refreshCount()
    }

    companion object {
        const val TAG = "EpisodeSearchFilterSheet"
        private const val SECTION_SORT = "sort"
        private const val SECTION_FIELDS = "fields"
        private const val SECTION_DATE = "date"
        private const val SECTION_DURATION = "duration"
        private const val SCOPE_SECTION_INDEX = 2

        private fun toSelection(options: EpisodeSearchOptions): FilterSelection {
            val result = HashMap<String, Set<String>>()
            result += EpisodeFilterSheet.toSelection(EpisodeFilterOptions.parse(options.filter.values))
            result[SECTION_SORT] = setOf(options.sort.name)
            result[SECTION_FIELDS] = options.fields.map { it.name }.toSet()
            if (options.dateRange != EpisodeSearchOptions.DateRange.ANY) {
                result[SECTION_DATE] = setOf(options.dateRange.name)
            }
            if (options.durationRange != EpisodeSearchOptions.DurationRange.ANY) {
                result[SECTION_DURATION] = setOf(options.durationRange.name)
            }
            return result
        }

        private fun toOptions(selection: FilterSelection, feedId: Long): EpisodeSearchOptions {
            val fields = selection[SECTION_FIELDS].orEmpty()
            return EpisodeSearchOptions(
                EpisodeFilterSheet.toOptions(selection).toFilter(),
                feedId,
                EpisodeSearchOptions.DateRange.entries.firstOrNull { it.name == selection.single(SECTION_DATE) },
                EpisodeSearchOptions.DurationRange.entries.firstOrNull { it.name == selection.single(SECTION_DURATION) },
                EpisodeSearchOptions.Field.entries.filter { it.name in fields },
                EpisodeSearchOptions.Sort.entries.firstOrNull { it.name == selection.single(SECTION_SORT) }
            )
        }
    }
}
