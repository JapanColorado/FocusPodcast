package allen.town.podcast.adapter

import allen.town.podcast.R
import allen.town.podcast.common.extensions.accentColor
import allen.town.podcast.core.util.Converter
import allen.town.podcast.model.transcript.Transcript
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * The lines of a [Transcript]. A line shows its speaker when the speaker changes and, in a timed
 * transcript, its start time; the line being spoken ([currentIndex]) is highlighted and the
 * others are dimmed. Tapping a timed line calls [onLineClicked]; an untimed transcript is only
 * read, nothing is highlighted or tappable.
 */
class TranscriptAdapter(private val onLineClicked: (Int) -> Unit) :
    RecyclerView.Adapter<TranscriptAdapter.LineHolder>() {

    private var transcript: Transcript = Transcript.empty()

    /** Index of the line being spoken, -1 for none. */
    var currentIndex: Int = -1
        private set

    fun setTranscript(transcript: Transcript) {
        this.transcript = transcript
        currentIndex = -1
        notifyDataSetChanged()
    }

    fun setCurrentIndex(index: Int) {
        if (index == currentIndex) {
            return
        }
        val previous = currentIndex
        currentIndex = index
        if (previous in 0 until itemCount) {
            notifyItemChanged(previous, PAYLOAD_HIGHLIGHT)
        }
        if (index in 0 until itemCount) {
            notifyItemChanged(index, PAYLOAD_HIGHLIGHT)
        }
    }

    override fun getItemCount(): Int = transcript.size()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LineHolder =
        LineHolder(LayoutInflater.from(parent.context).inflate(R.layout.transcript_line_item, parent, false))

    override fun onBindViewHolder(holder: LineHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_HIGHLIGHT }) {
            bindHighlight(holder, position)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: LineHolder, position: Int) {
        val context = holder.itemView.context
        val segments = transcript.segments
        val segment = segments[position]
        val timed = transcript.isTimed
        val speaker = segment.speaker
        val previousSpeaker = if (position > 0) segments[position - 1].speaker else null
        val showSpeaker = speaker != null && (position == 0 || speaker != previousSpeaker)

        holder.speaker.visibility = if (showSpeaker) View.VISIBLE else View.GONE
        holder.speaker.text = speaker.orEmpty()
        holder.speaker.setTextColor(context.accentColor())
        holder.time.visibility = if (timed) View.VISIBLE else View.GONE
        holder.time.text = if (timed) Converter.getDurationStringLong(segment.startMs.toInt()) else ""
        holder.header.visibility = if (showSpeaker || timed) View.VISIBLE else View.GONE
        holder.text.text = segment.text

        val words = if (speaker != null) {
            context.getString(R.string.transcript_speaker_line, speaker, segment.text)
        } else {
            segment.text
        }
        holder.itemView.contentDescription = if (timed) {
            context.getString(
                R.string.transcript_line_description,
                Converter.getDurationStringLocalized(context, segment.startMs), words
            )
        } else {
            words
        }
        if (timed) {
            holder.itemView.setOnClickListener {
                val index = holder.bindingAdapterPosition
                if (index != RecyclerView.NO_POSITION) {
                    onLineClicked(index)
                }
            }
            ViewCompat.replaceAccessibilityAction(
                holder.itemView, AccessibilityActionCompat.ACTION_CLICK,
                context.getString(R.string.transcript_seek_action), null
            )
        } else {
            holder.itemView.setOnClickListener(null)
            holder.itemView.isClickable = false
        }
        bindHighlight(holder, position)
    }

    private fun bindHighlight(holder: LineHolder, position: Int) {
        val current = transcript.isTimed && position == currentIndex
        val dimmed = transcript.isTimed && !current
        holder.text.alpha = if (dimmed) DIMMED_ALPHA else 1f
        holder.text.setTypeface(null, if (current) Typeface.BOLD else Typeface.NORMAL)
        holder.itemView.setBackgroundColor(
            if (current) ColorUtils.setAlphaComponent(holder.itemView.context.accentColor(), HIGHLIGHT_ALPHA) else 0
        )
        holder.itemView.isSelected = current
    }

    class LineHolder(view: View) : RecyclerView.ViewHolder(view) {
        val header: View = view.findViewById(R.id.transcriptLineHeader)
        val speaker: TextView = view.findViewById(R.id.transcriptLineSpeaker)
        val time: TextView = view.findViewById(R.id.transcriptLineTime)
        val text: TextView = view.findViewById(R.id.transcriptLineText)
    }

    private companion object {
        const val PAYLOAD_HIGHLIGHT = "highlight"
        const val DIMMED_ALPHA = 0.6f
        const val HIGHLIGHT_ALPHA = 0x26
    }
}
