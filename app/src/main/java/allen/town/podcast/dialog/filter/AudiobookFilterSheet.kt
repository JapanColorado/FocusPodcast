package allen.town.podcast.dialog.filter

import allen.town.podcast.R
import allen.town.podcast.adapter.AudiobookEntry
import allen.town.podcast.core.feed.AudiobookShelfFilter
import allen.town.podcast.core.pref.Prefs
import allen.town.podcast.dialog.filter.FilterSheet.Companion.single

/**
 * "Sort & filter" of the Audiobooks page: sort order, status, author (only with two or more
 * authors) and length. Stored in [Prefs.audiobookShelfFilter]; the parent fragment ([Host]) holds
 * the books, which are filtered in memory, and re-applies the choice.
 */
class AudiobookFilterSheet : FilterSheet() {

    /** The Audiobooks page. */
    interface Host {
        /** Every audiobook, unfiltered (empty until loaded). */
        val allAudiobooks: List<AudiobookEntry>

        fun onAudiobookFilterChanged()
    }

    /** Captured on the main thread, read by the count off it; the list itself is never changed. */
    private var books: List<AudiobookEntry> = emptyList()

    override val titleRes: Int
        get() = R.string.sort_and_filter

    override val countPluralsRes: Int
        get() = R.plurals.filter_audiobooks_shown

    override fun buildSections(): List<FilterSection> {
        books = (parentFragment as? Host)?.allAudiobooks.orEmpty()
        val sections = mutableListOf(
            FilterSection(
                SECTION_SORT, getString(R.string.sort), listOf(
                    option(AudiobookShelfFilter.Sort.RECENTLY_PLAYED, R.string.audiobook_sort_recently_played),
                    option(AudiobookShelfFilter.Sort.TITLE, R.string.audiobook_sort_title),
                    option(AudiobookShelfFilter.Sort.AUTHOR, R.string.audiobook_sort_author),
                    option(AudiobookShelfFilter.Sort.PROGRESS, R.string.audiobook_sort_progress),
                    option(AudiobookShelfFilter.Sort.DATE_ADDED, R.string.audiobook_sort_date_added)
                ), selectionRequired = true
            ),
            FilterSection(
                SECTION_STATUS, getString(R.string.filter_section_status), listOf(
                    option(AudiobookShelfFilter.Status.NOT_STARTED, R.string.audiobook_status_not_started),
                    option(AudiobookShelfFilter.Status.IN_PROGRESS, R.string.filter_in_progress),
                    option(AudiobookShelfFilter.Status.FINISHED, R.string.audiobook_finished)
                )
            )
        )
        val authors = AudiobookShelfFilter.authorsOf(books.map { it.feed })
        if (authors.size >= 2) {
            sections += FilterSection(
                SECTION_AUTHOR, getString(R.string.filter_section_author),
                authors.map { FilterOption(it, it) }, multiSelect = true
            )
        }
        sections += FilterSection(
            SECTION_LENGTH, getString(R.string.filter_section_length), listOf(
                option(AudiobookShelfFilter.Length.UNDER_5_HOURS, R.string.audiobook_length_under_5h),
                option(AudiobookShelfFilter.Length.FROM_5_TO_15_HOURS, R.string.audiobook_length_5_to_15h),
                option(AudiobookShelfFilter.Length.OVER_15_HOURS, R.string.audiobook_length_over_15h)
            )
        )
        return sections
    }

    private fun option(value: Enum<*>, label: Int) = FilterOption(value.name, getString(label))

    override fun currentSelection(): FilterSelection = toSelection(Prefs.audiobookShelfFilter)

    override fun defaultSelection(): FilterSelection = toSelection(AudiobookShelfFilter.defaults())

    override fun onSelectionChanged(selection: FilterSelection) {
        Prefs.audiobookShelfFilter = toFilter(selection)
        (parentFragment as? Host)?.onAudiobookFilterChanged()
    }

    override fun countShown(selection: FilterSelection): Int =
        toFilter(selection).apply(books, { it.feed }, { it.progress }).size

    companion object {
        const val TAG = "AudiobookFilterSheet"
        private const val SECTION_SORT = "sort"
        private const val SECTION_STATUS = "status"
        private const val SECTION_AUTHOR = "author"
        private const val SECTION_LENGTH = "length"

        private fun toSelection(filter: AudiobookShelfFilter): FilterSelection {
            val result = HashMap<String, Set<String>>()
            result[SECTION_SORT] = setOf(filter.sort.name)
            filter.status?.let { result[SECTION_STATUS] = setOf(it.name) }
            if (filter.authors.isNotEmpty()) {
                result[SECTION_AUTHOR] = filter.authors
            }
            filter.length?.let { result[SECTION_LENGTH] = setOf(it.name) }
            return result
        }

        private fun toFilter(selection: FilterSelection): AudiobookShelfFilter = AudiobookShelfFilter(
            AudiobookShelfFilter.Sort.entries.firstOrNull { it.name == selection.single(SECTION_SORT) },
            AudiobookShelfFilter.Status.entries.firstOrNull { it.name == selection.single(SECTION_STATUS) },
            selection[SECTION_AUTHOR].orEmpty(),
            AudiobookShelfFilter.Length.entries.firstOrNull { it.name == selection.single(SECTION_LENGTH) }
        )
    }
}
