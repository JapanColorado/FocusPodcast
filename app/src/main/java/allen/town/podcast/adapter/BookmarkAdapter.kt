package allen.town.podcast.adapter

import allen.town.podcast.R
import allen.town.podcast.core.feed.util.ImageResourceUtils
import allen.town.podcast.core.util.BookmarkUtils
import allen.town.podcast.databinding.BookmarkListItemBinding
import allen.town.podcast.model.feed.Bookmark
import allen.town.podcast.theme.ThemeStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * Rows of bookmarks. With [showEpisode] (the Bookmarks screen) each row also shows the episode's
 * cover and title and the podcast's name; without it (the player's per-episode tab) only the
 * position and the note. Tapping a row and its overflow button are handed to [listener].
 */
class BookmarkAdapter(
    private val showEpisode: Boolean,
    private val listener: Listener
) : RecyclerView.Adapter<BookmarkAdapter.Holder>() {

    interface Listener {
        fun onBookmarkClicked(bookmark: Bookmark)
        fun onBookmarkMenu(bookmark: Bookmark, anchor: View)
    }

    private var bookmarks: List<Bookmark> = emptyList()

    init {
        setHasStableIds(true)
    }

    fun setBookmarks(bookmarks: List<Bookmark>) {
        this.bookmarks = bookmarks
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = bookmarks.size

    override fun getItemId(position: Int): Long = bookmarks[position].id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(BookmarkListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(bookmarks[position])
    }

    inner class Holder(private val binding: BookmarkListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(bookmark: Bookmark) {
            val context = binding.root.context
            val item = bookmark.item
            val time = BookmarkUtils.formatPosition(bookmark.positionMs)
            val podcast = item?.feed?.title
            binding.bookmarkPosition.setTextColor(ThemeStore.accentColor(context))
            if (showEpisode && item != null) {
                binding.bookmarkCover.visibility = View.VISIBLE
                binding.bookmarkEpisodeTitle.visibility = View.VISIBLE
                binding.bookmarkEpisodeTitle.text = item.title
                binding.bookmarkPosition.text = if (podcast.isNullOrBlank()) time else "$time · $podcast"
                CoverLoader()
                    .withUri(ImageResourceUtils.getEpisodeListImageLocation(item))
                    .withFallbackUri(item.feed?.imageUrl)
                    .withCoverView(binding.bookmarkCover)
                    .load()
            } else {
                binding.bookmarkCover.visibility = View.GONE
                binding.bookmarkEpisodeTitle.visibility = View.GONE
                binding.bookmarkPosition.text = time
            }
            val note = bookmark.note
            if (bookmark.hasNote() && note != null) {
                binding.bookmarkNote.visibility = View.VISIBLE
                binding.bookmarkNote.text = note
            } else {
                binding.bookmarkNote.visibility = View.GONE
            }

            // read as one item: "episode, 12:34 · podcast, note"
            val parts = mutableListOf<String>()
            if (showEpisode && item != null) {
                item.title?.let { parts.add(it) }
            }
            parts.add(binding.bookmarkPosition.text.toString())
            if (bookmark.hasNote() && note != null) {
                parts.add(note)
            }
            binding.root.contentDescription = parts.reduce { acc, part ->
                context.getString(R.string.bookmark_row_description, acc, part)
            }
            binding.root.setOnClickListener { listener.onBookmarkClicked(bookmark) }
            binding.bookmarkMenu.setOnClickListener { listener.onBookmarkMenu(bookmark, it) }
            ViewCompat.replaceAccessibilityAction(
                binding.root, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                context.getString(R.string.transcript_seek_action), null
            )
        }
    }
}
