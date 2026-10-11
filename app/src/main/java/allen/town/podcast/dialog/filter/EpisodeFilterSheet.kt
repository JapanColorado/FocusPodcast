package allen.town.podcast.dialog.filter

import allen.town.podcast.R
import allen.town.podcast.core.feed.EpisodeFilterOptions
import allen.town.podcast.core.storage.DBReader
import allen.town.podcast.core.storage.DBWriter
import allen.town.podcast.dialog.filter.FilterSheet.Companion.single
import allen.town.podcast.model.feed.FeedItemFilter
import android.os.Bundle

/**
 * Episode filter of All Episodes and of a podcast's episode list: Status, Downloaded and Favorite
 * (see [EpisodeFilterOptions] for how they map onto [FeedItemFilter] keys). For a podcast the
 * filter is stored on the feed, which reloads its list through the FILTER_CHANGED event; for All
 * Episodes the parent fragment ([Host]) stores and applies it.
 */
class EpisodeFilterSheet : FilterSheet() {

    /** The fragment showing All Episodes; it stores the filter and reloads. */
    interface Host {
        fun onEpisodeFilterChanged(filter: FeedItemFilter)
    }

    private val feedId: Long
        get() = arguments?.getLong(ARG_FEED_ID) ?: 0L

    override val countPluralsRes: Int
        get() = R.plurals.filter_episodes_shown

    override fun buildSections(): List<FilterSection> = listOf(
        FilterSection(
            SECTION_STATUS, getString(R.string.filter_section_status), listOf(
                FilterOption(EpisodeFilterOptions.Status.UNFINISHED.name, getString(R.string.filter_unfinished)),
                FilterOption(EpisodeFilterOptions.Status.FINISHED.name, getString(R.string.filter_finished))
            )
        ),
        FilterSection(
            SECTION_DOWNLOADED, getString(R.string.filter_section_downloaded), listOf(
                FilterOption(YES, getString(R.string.filter_downloaded)),
                FilterOption(NO, getString(R.string.filter_not_downloaded))
            )
        ),
        FilterSection(
            SECTION_FAVORITE, getString(R.string.filter_section_favorite), listOf(
                FilterOption(YES, getString(R.string.filter_favorite)),
                FilterOption(NO, getString(R.string.filter_not_favorite))
            )
        )
    )

    override fun currentSelection(): FilterSelection =
        toSelection(EpisodeFilterOptions.parse(arguments?.getStringArray(ARG_VALUES)))

    override fun onSelectionChanged(selection: FilterSelection) {
        val values = toOptions(selection).toValues()
        // the arguments survive recreation, so a rotated sheet shows the latest choice
        arguments?.putStringArray(ARG_VALUES, values)
        val id = feedId
        if (id > 0) {
            DBWriter.setFeedItemsFilter(id, values.toSet())
        } else {
            (parentFragment as? Host)?.onEpisodeFilterChanged(FeedItemFilter(values))
        }
    }

    override fun countShown(selection: FilterSelection): Int {
        val filter = toOptions(selection).toFilter()
        val id = feedId
        return if (id > 0) DBReader.getFeedItemCount(id, filter) else DBReader.getTotalEpisodeCount(filter)
    }

    companion object {
        const val TAG = "EpisodeFilterSheet"
        private const val ARG_FEED_ID = "feed_id"
        private const val ARG_VALUES = "values"
        private const val SECTION_STATUS = "status"
        private const val SECTION_DOWNLOADED = "downloaded"
        private const val SECTION_FAVORITE = "favorite"
        private const val YES = "yes"
        private const val NO = "no"

        /** The filter of a podcast's episode list; stored on the feed. */
        @JvmStatic
        fun newFeedInstance(feedId: Long, filter: FeedItemFilter?): EpisodeFilterSheet =
            create(feedId, filter)

        /** The All Episodes filter; show it from the [Host] fragment's child fragment manager. */
        fun newAllEpisodesInstance(filter: FeedItemFilter): EpisodeFilterSheet = create(0, filter)

        private fun create(feedId: Long, filter: FeedItemFilter?): EpisodeFilterSheet {
            val sheet = EpisodeFilterSheet()
            sheet.arguments = Bundle().apply {
                putLong(ARG_FEED_ID, feedId)
                putStringArray(ARG_VALUES, EpisodeFilterOptions.sanitize(filter?.values))
            }
            return sheet
        }

        private fun toSelection(options: EpisodeFilterOptions): FilterSelection {
            val result = HashMap<String, Set<String>>()
            options.status?.let { result[SECTION_STATUS] = setOf(it.name) }
            options.downloaded?.let { result[SECTION_DOWNLOADED] = setOf(if (it) YES else NO) }
            options.favorite?.let { result[SECTION_FAVORITE] = setOf(if (it) YES else NO) }
            return result
        }

        private fun toOptions(selection: FilterSelection): EpisodeFilterOptions {
            val status = EpisodeFilterOptions.Status.entries.firstOrNull { it.name == selection.single(SECTION_STATUS) }
            return EpisodeFilterOptions(
                status,
                selection.single(SECTION_DOWNLOADED)?.let { it == YES },
                selection.single(SECTION_FAVORITE)?.let { it == YES }
            )
        }
    }
}
