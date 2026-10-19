package dev.russell.focuspodcast.adapter

import dev.russell.focuspodcast.common.util.BasePreferenceUtil.libraryCategory
import dev.russell.focuspodcast.common.views.AccentMaterialDialog
import dev.russell.focuspodcast.R
import dev.russell.focuspodcast.activity.SettingsActivity
import dev.russell.focuspodcast.core.pref.Prefs
import dev.russell.focuspodcast.fragment.*
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import dev.russell.focuspodcast.theme.ThemeStore.Companion.accentColor
import dev.russell.focuspodcast.theme.ThemeStore.Companion.textColorPrimary
import dev.russell.focuspodcast.theme.constants.ThemeConstants
import com.google.android.material.card.MaterialCardView
import com.joanzapata.iconify.Iconify
import java.lang.ref.WeakReference
import java.text.NumberFormat
import java.util.*

/**
 * BaseAdapter for the navigation drawer
 */
class NavigationListAdapter(private val itemAccess: ItemAccess, context: Activity) :
    RecyclerView.Adapter<NavigationListAdapter.NavHolder>(), OnSharedPreferenceChangeListener {
    val tags: MutableList<String> = ArrayList()
    private val activity: WeakReference<Activity>
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (ThemeConstants.LIBRARY_CATEGORIES == key) {
            loadItems()
        }
    }

    private fun loadItems() {
        val categoryInfoList = libraryCategory
        val newTags: MutableList<String> = ArrayList()
        for ((tag, visible) in categoryInfoList) {
            if (visible) {
                newTags.add(tag)
            }
        }
        tags.clear()
        tags.addAll(newTags)
        notifyDataSetChanged()
    }

    fun getLabel(tag: String?): String {
        val context = activity.get() ?: return ""
        return when (tag) {
            PlaylistFragment.TAG -> context.getString(R.string.playlist_label)
            EpisodesFragment.TAG -> context.getString(R.string.episodes_label)
            DownloadPagerFragment.TAG -> context.getString(R.string.downloads_label)
            SubFeedsFragment.TAG -> context.getString(R.string.library_label)
            FavoriteEpisodesFragment.TAG -> context.getString(R.string.favorite_episodes_label)
            BookmarksFragment.TAG -> context.getString(R.string.bookmarks_label)
            else -> ""
        }
    }

    @DrawableRes
    private fun getDrawable(tag: String): Int {
        return when (tag) {
            PlaylistFragment.TAG -> R.drawable.ic_playlist
            EpisodesFragment.TAG -> R.drawable.ic_episodes
            DownloadPagerFragment.TAG -> R.drawable.ic_download
            SubFeedsFragment.TAG -> R.drawable.ic_my_subs
            FavoriteEpisodesFragment.TAG -> R.drawable.ic_star
            BookmarksFragment.TAG -> R.drawable.ic_bookmark
            else -> 0
        }
    }

    fun getFragmentTags(): List<String> {
        return Collections.unmodifiableList(tags)
    }

    override fun getItemCount(): Int = tags.size

    override fun getItemId(position: Int): Long = tags[position].hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NavHolder {
        val inflater = LayoutInflater.from(activity.get())
        return NavHolder(inflater.inflate(R.layout.nav_listitem, parent, false))
    }

    override fun onBindViewHolder(holder: NavHolder, position: Int) {
        bindNavView(getLabel(tags[position]), position, holder)
        val hostActivity = activity.get()
        if (hostActivity != null) {
            val typedValue = TypedValue()
            hostActivity.theme.resolveAttribute(R.attr.colorSurface, typedValue, true)
            //only this produces the desired result; reason unknown, and other screens seem not to need it
            holder.itemView.setBackgroundResource(typedValue.resourceId)
        }
        (holder.itemView as MaterialCardView).isChecked = itemAccess.isSelected(position)
        holder.itemView.setOnClickListener { itemAccess.onItemClick(position) }
    }

    private fun bindNavView(title: String, position: Int, holder: NavHolder) {
        val context = activity.get() ?: return
        holder.title.text = title

        // reset for re-use
        holder.count.visibility = View.GONE
        holder.count.setOnClickListener(null)
        holder.count.isClickable = false
        val tag = tags[position]
        if (tag == PlaylistFragment.TAG) {
            val queueSize = itemAccess.queueSize
            if (queueSize > 0) {
                holder.count.text = NumberFormat.getInstance().format(queueSize.toLong())
                holder.count.visibility = View.VISIBLE
            }
        } else if (tag == EpisodesFragment.TAG) {
            //new items, but removed
//            int unreadItems = itemAccess.getNumberOfNewItems();
            val sum = itemAccess.feedCounterSum
            if (sum > 0) {
                holder.count.text = NumberFormat.getInstance().format(sum.toLong())
                holder.count.visibility = View.VISIBLE
            }
        } else if (tag == SubFeedsFragment.TAG) {
            /*int sum = itemAccess.getFeedCounterSum();
            if (sum > 0) {
                holder.count.setText(NumberFormat.getInstance().format(sum));
                holder.count.setVisibility(View.VISIBLE);
            }*/
        } else if (tag == DownloadPagerFragment.TAG && Prefs.isEnableAutodownload) {
            val epCacheSize = Prefs.episodeCacheSize
            // don't count episodes that can be reclaimed
            val spaceUsed = (itemAccess.numberOfDownloadedItems
                    - itemAccess.reclaimableItems)
            if (epCacheSize > 0 && spaceUsed >= epCacheSize) {
                holder.count.text = "{md-disc-full 150%}"
                Iconify.addIcons(holder.count)
                holder.count.visibility = View.VISIBLE
                holder.count.setOnClickListener { v: View? ->
                    AccentMaterialDialog(
                        context,
                        R.style.MaterialAlertDialogTheme
                    )
                        .setTitle(R.string.episode_cache_full_title)
                        .setMessage(R.string.episode_cache_full_message)
                        .setPositiveButton(android.R.string.cancel, null)
                        .setNeutralButton(R.string.open_autodownload_settings) { dialog: DialogInterface?, which: Int ->
                            val intent = Intent(context, SettingsActivity::class.java)
                            intent.putExtra(SettingsActivity.OPEN_AUTO_DOWNLOAD_SETTINGS, true)
                            context.startActivity(intent)
                        }
                        .show()
                }
            }
        }
        holder.title.setTextColor(textColorPrimary(context))
        val drawable = AppCompatResources.getDrawable(context, getDrawable(tags[position]))
        if (itemAccess.isSelected(position)) {
            if (drawable != null) {
                drawable.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                    accentColor(context), BlendModeCompat.SRC_IN
                )
            }
            holder.title.setTextColor(accentColor(context))
        }
        holder.image.setImageDrawable(drawable)
    }

    class NavHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView
        val title: TextView
        val count: TextView

        init {
            image = itemView.findViewById(R.id.imgvCover)
            title = itemView.findViewById(R.id.txtvTitle)
            count = itemView.findViewById(R.id.txtvCount)
        }
    }

    interface ItemAccess {
        fun isSelected(position: Int): Boolean
        val queueSize: Int
        val numberOfNewItems: Int
        val numberOfDownloadedItems: Int
        val reclaimableItems: Int
        val feedCounterSum: Int
        fun onItemClick(position: Int)
    }

    init {
        activity = WeakReference(context)
        loadItems()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.registerOnSharedPreferenceChangeListener(this)
    }
}