package dev.russell.yagi.fragment.storage

import dev.russell.yagi.R
import dev.russell.yagi.core.glide.ApGlideSettings
import dev.russell.yagi.core.storage.StorageUsage
import dev.russell.yagi.theme.ThemeStore.Companion.accentColor
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * The storage overview list: a header with the totals and the used/other/free bar, one row per
 * podcast (largest first), and a link to the episode cleanup setting.
 */
class StorageAdapter(
    private val context: Context,
    private val listener: Listener,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Listener {
        fun onFeedClicked(summary: StorageUsage.FeedSummary)
        fun onCleanupClicked()
    }

    private var snapshot: StorageSnapshot? = null

    fun update(newSnapshot: StorageSnapshot) {
        snapshot = newSnapshot
        @Suppress("NotifyDataSetChanged") // a handful of rows, all of which can change at once
        notifyDataSetChanged()
    }

    private val feeds: List<StorageUsage.FeedSummary>
        get() = snapshot?.overview?.feeds ?: emptyList()

    /** Header, then the podcasts (or one "no downloads" row), then the cleanup link. */
    override fun getItemCount(): Int = if (snapshot == null) 0 else 2 + maxOf(1, feeds.size)

    override fun getItemViewType(position: Int): Int = when {
        position == 0 -> TYPE_HEADER
        position == itemCount - 1 -> TYPE_CLEANUP
        feeds.isEmpty() -> TYPE_EMPTY
        else -> TYPE_FEED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(inflater.inflate(R.layout.storage_header_item, parent, false))
            TYPE_FEED -> FeedHolder(inflater.inflate(R.layout.storage_feed_item, parent, false))
            else -> TextHolder(inflater.inflate(R.layout.storage_text_item, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val current = snapshot ?: return
        when (holder) {
            is HeaderHolder -> bindHeader(holder, current)
            is FeedHolder -> bindFeed(holder, feeds[position - 1], current.overview.bytes)
            is TextHolder -> if (getItemViewType(position) == TYPE_CLEANUP) {
                holder.title.setText(R.string.pref_episode_cleanup_title)
                holder.summary.setText(R.string.storage_cleanup_link_sum)
                holder.summary.visibility = View.VISIBLE
                holder.itemView.isClickable = true
                holder.itemView.setOnClickListener { listener.onCleanupClicked() }
            } else {
                holder.title.setText(R.string.no_comp_downloads_head_label)
                holder.summary.visibility = View.GONE
                holder.itemView.isClickable = false
                holder.itemView.setOnClickListener(null)
                holder.itemView.background = null
            }
        }
    }

    private fun bindHeader(holder: HeaderHolder, snapshot: StorageSnapshot) {
        val used = snapshot.overview.bytes
        holder.used.text = format(used)
        holder.usedCaption.text = context.resources.getQuantityString(
            R.plurals.storage_used_by_episodes, snapshot.overview.count, snapshot.overview.count
        )
        val accent = accentColor(context)
        val otherColor = ColorUtils.setAlphaComponent(accent, OTHER_ALPHA)
        val freeColor = ColorUtils.setAlphaComponent(accent, FREE_ALPHA)
        if (snapshot.totalBytes <= 0) {
            holder.bar.visibility = View.GONE
            holder.legend.visibility = View.GONE
            holder.free.visibility = View.GONE
            return
        }
        holder.bar.visibility = View.VISIBLE
        holder.legend.visibility = View.VISIBLE
        holder.free.visibility = View.VISIBLE
        val other = (snapshot.totalBytes - snapshot.freeBytes - used).coerceAtLeast(0)
        setSegment(holder.barDownloads, used, snapshot.totalBytes, accent)
        setSegment(holder.barOther, other, snapshot.totalBytes, otherColor)
        setSegment(holder.barFree, snapshot.freeBytes, snapshot.totalBytes, freeColor)
        holder.bar.background = rounded(0, holder.bar.layoutParams.height / 2f).also {
            it.setColor(freeColor)
        }
        holder.bar.clipToOutline = true
        holder.bar.contentDescription = context.getString(
            R.string.storage_bar_description, format(used), format(other), format(snapshot.freeBytes)
        )
        setSwatch(holder.legendDownloads, accent)
        setSwatch(holder.legendOther, otherColor)
        setSwatch(holder.legendFree, freeColor)
        holder.free.text = context.getString(
            R.string.storage_free_of_total, format(snapshot.freeBytes), format(snapshot.totalBytes)
        )
    }

    private fun setSegment(view: View, bytes: Long, total: Long, color: Int) {
        val params = view.layoutParams as LinearLayout.LayoutParams
        // weights as a share of the volume; a sliver of at least one per mille stays visible
        params.weight = if (bytes <= 0) 0f else maxOf(MIN_WEIGHT, bytes.toFloat() / total)
        view.layoutParams = params
        view.setBackgroundColor(color)
    }

    private fun setSwatch(view: TextView, color: Int) {
        val size = (SWATCH_DP * context.resources.displayMetrics.density).toInt()
        val swatch = rounded(color, size / 2f)
        swatch.setSize(size, size)
        swatch.setBounds(0, 0, size, size)
        view.setCompoundDrawablesRelative(swatch, null, null, null)
    }

    private fun rounded(color: Int, radius: Float): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.setColor(color)
        drawable.cornerRadius = radius
        return drawable
    }

    private fun bindFeed(holder: FeedHolder, summary: StorageUsage.FeedSummary, totalBytes: Long) {
        holder.title.text = if (summary.isUnsubscribedBucket) {
            context.getString(R.string.storage_unsubscribed_title)
        } else {
            summary.title ?: ""
        }
        holder.size.text = context.resources.getQuantityString(
            R.plurals.num_episodes, summary.count, summary.count
        ) + " · " + format(summary.bytes)
        val share = if (totalBytes <= 0) 0 else (summary.bytes * SHARE_MAX / totalBytes).toInt()
        holder.share.progress = share
        holder.share.setIndicatorColor(accentColor(context))
        val radius = (COVER_RADIUS_DP * context.resources.displayMetrics.density).toInt()
        Glide.with(context)
            .load(summary.imageUrl)
            .apply(
                RequestOptions()
                    .placeholder(R.drawable.ic_podcast_background_round)
                    .error(R.drawable.ic_podcast_background_round)
                    .diskCacheStrategy(ApGlideSettings.AP_DISK_CACHE_STRATEGY)
                    .transforms(CenterCrop(), RoundedCorners(radius))
                    .dontAnimate()
            )
            .into(holder.cover)
        holder.itemView.setOnClickListener { listener.onFeedClicked(summary) }
    }

    private fun format(bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val used: TextView = view.findViewById(R.id.txtvUsed)
        val usedCaption: TextView = view.findViewById(R.id.txtvUsedCaption)
        val bar: LinearLayout = view.findViewById(R.id.storageBar)
        val barDownloads: View = view.findViewById(R.id.barDownloads)
        val barOther: View = view.findViewById(R.id.barOther)
        val barFree: View = view.findViewById(R.id.barFree)
        val legend: View = view.findViewById(R.id.storageLegend)
        val legendDownloads: TextView = view.findViewById(R.id.legendDownloads)
        val legendOther: TextView = view.findViewById(R.id.legendOther)
        val legendFree: TextView = view.findViewById(R.id.legendFree)
        val free: TextView = view.findViewById(R.id.txtvFree)
    }

    private class FeedHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cover: ImageView = view.findViewById(R.id.imgvCover)
        val title: TextView = view.findViewById(R.id.txtvTitle)
        val size: TextView = view.findViewById(R.id.txtvSize)
        val share: LinearProgressIndicator = view.findViewById(R.id.share)
    }

    private class TextHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.txtvTitle)
        val summary: TextView = view.findViewById(R.id.txtvSummary)
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_FEED = 1
        private const val TYPE_EMPTY = 2
        private const val TYPE_CLEANUP = 3
        private const val SHARE_MAX = 1000L
        private const val MIN_WEIGHT = 0.001f
        private const val OTHER_ALPHA = 0x66
        private const val FREE_ALPHA = 0x22
        private const val SWATCH_DP = 10
        private const val COVER_RADIUS_DP = 4
    }
}
