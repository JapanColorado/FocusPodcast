package dev.russell.focuspodcast.view

import dev.russell.focuspodcast.R
import dev.russell.focuspodcast.common.util.ImageUtils
import dev.russell.focuspodcast.core.glide.ApGlideSettings
import dev.russell.focuspodcast.core.util.BookmarkUtils
import dev.russell.focuspodcast.core.util.Converter
import dev.russell.focuspodcast.core.util.IntentUtils
import dev.russell.focuspodcast.model.feed.Person
import dev.russell.focuspodcast.model.feed.Soundbite
import dev.russell.focuspodcast.theme.ThemeStore
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import java.util.Locale

/**
 * Builds the Podcasting 2.0 views shared by the episode screen and the podcast info: the row of
 * people chips ([bindPeople]) and the list of highlights ([bindHighlights]).
 */
object PodcastingViews {

    /**
     * Fills [container] (a horizontal LinearLayout) with one chip per person: round avatar, or the
     * initial when there is no picture or it fails to load, name and role. A person with a page
     * opens it in the browser when tapped.
     */
    @JvmStatic
    fun bindPeople(container: ViewGroup, persons: List<Person>) {
        container.removeAllViews()
        val context = container.context
        val inflater = LayoutInflater.from(context)
        val initialBackground = ThemeStore.accentColor(context)
        for (person in persons) {
            val chip = inflater.inflate(R.layout.person_chip, container, false)
            val role = roleLabel(context, person.role)
            chip.findViewById<TextView>(R.id.personName).text = person.name
            chip.findViewById<TextView>(R.id.personRole).text = role
            val initial = chip.findViewById<TextView>(R.id.personInitial)
            initial.text = initialOf(person.name)
            initial.background = ImageUtils.getColoredDrawable(context, R.drawable.shape_circle, initialBackground)
            val avatar = chip.findViewById<ImageView>(R.id.personAvatar)
            val imageUrl = person.imageUrl
            if (imageUrl == null) {
                avatar.visibility = View.GONE
            } else {
                // on failure the view is left empty and the initial underneath shows
                Glide.with(container)
                    .load(imageUrl)
                    .diskCacheStrategy(ApGlideSettings.AP_DISK_CACHE_STRATEGY)
                    .circleCrop()
                    .into(avatar)
            }
            val href = person.href
            if (href != null) {
                chip.contentDescription = context.getString(R.string.person_chip_open_description, person.name, role)
                chip.setOnClickListener { IntentUtils.openInBrowser(it.context, href) }
            } else {
                chip.contentDescription = context.getString(R.string.person_chip_description, person.name, role)
                chip.isClickable = false
                chip.isFocusable = true
            }
            container.addView(chip)
        }
    }

    /**
     * Fills [container] (a vertical LinearLayout) with one row per highlight: its title, or
     * "Highlight at 12:34" when it has none, and its start and length. [onClick] plays it.
     */
    @JvmStatic
    fun bindHighlights(container: ViewGroup, soundbites: List<Soundbite>, onClick: (Soundbite) -> Unit) {
        container.removeAllViews()
        val context = container.context
        val inflater = LayoutInflater.from(context)
        for (soundbite in soundbites) {
            val row = inflater.inflate(R.layout.soundbite_item, container, false)
            val title = highlightTitle(context, soundbite)
            val start = BookmarkUtils.formatPosition(soundbite.startMs)
            row.findViewById<TextView>(R.id.soundbiteTitle).text = title
            row.findViewById<TextView>(R.id.soundbiteTime).text =
                start + " · " + BookmarkUtils.formatPosition(soundbite.durationMs)
            row.contentDescription = context.getString(
                R.string.highlight_play_description, title, start,
                Converter.getDurationStringLocalized(context, soundbite.durationMs)
            )
            row.setOnClickListener { onClick(soundbite) }
            container.addView(row)
        }
    }

    /** The feed's title for the highlight, or "Highlight at 12:34". */
    @JvmStatic
    fun highlightTitle(context: Context, soundbite: Soundbite): String =
        soundbite.title ?: context.getString(
            R.string.highlight_at, BookmarkUtils.formatPosition(soundbite.startMs)
        )

    /** The role for display: translated for host, co-host and guest, otherwise capitalised. */
    @JvmStatic
    fun roleLabel(context: Context, role: String): String = when (role) {
        Person.DEFAULT_ROLE -> context.getString(R.string.person_role_host)
        "cohost", "co-host" -> context.getString(R.string.person_role_cohost)
        "guest" -> context.getString(R.string.person_role_guest)
        else -> role.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    private fun initialOf(name: String): String {
        if (name.isEmpty()) {
            return ""
        }
        return name.substring(0, Character.charCount(name.codePointAt(0))).uppercase(Locale.getDefault())
    }
}
