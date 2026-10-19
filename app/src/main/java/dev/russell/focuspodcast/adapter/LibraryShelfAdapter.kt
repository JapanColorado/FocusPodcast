package dev.russell.focuspodcast.adapter

import dev.russell.focuspodcast.activity.MainActivity
import dev.russell.focuspodcast.databinding.LibraryAudiobookShelfBinding
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * The full-width header of the Library page: a horizontal row of audiobooks with their progress,
 * a "See all" button, and the "Podcasts" title above the subscription grid that follows it.
 * Has no item at all while there are no audiobooks (or while [hidden]), so the page then looks
 * exactly like the plain subscription grid.
 */
class LibraryShelfAdapter(
    activity: MainActivity,
    private val onSeeAll: () -> Unit
) : RecyclerView.Adapter<LibraryShelfAdapter.Holder>() {
    private val bookAdapter = AudiobookAdapter(
        activity,
        (SHELF_ITEM_WIDTH_DP * activity.resources.displayMetrics.density).toInt()
    )
    private var entries: List<AudiobookEntry> = emptyList()
    private var showPodcastsTitle = false

    /** Hides the shelf without forgetting it, e.g. while the grid is in multi-select mode. */
    var hidden = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    init {
        setHasStableIds(true)
    }

    /**
     * @param showPodcastsTitle whether the subscription grid below has anything to title
     */
    fun setEntries(entries: List<AudiobookEntry>, showPodcastsTitle: Boolean) {
        this.entries = entries
        this.showPodcastsTitle = showPodcastsTitle
        bookAdapter.setEntries(entries)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = if (hidden || entries.isEmpty()) 0 else 1

    override fun getItemId(position: Int): Long = SHELF_ID

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = LibraryAudiobookShelfBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.audiobookRow.layoutManager =
            LinearLayoutManager(parent.context, RecyclerView.HORIZONTAL, false)
        binding.audiobookRow.adapter = bookAdapter
        binding.butSeeAll.setOnClickListener { onSeeAll() }
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.binding.txtvPodcastsTitle.visibility = if (showPodcastsTitle) View.VISIBLE else View.GONE
    }

    class Holder(val binding: LibraryAudiobookShelfBinding) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        private const val SHELF_ITEM_WIDTH_DP = 120
        private const val SHELF_ID = Long.MIN_VALUE
    }
}
