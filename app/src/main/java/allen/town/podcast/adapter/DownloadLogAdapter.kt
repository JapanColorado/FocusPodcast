package allen.town.podcast.adapter

import android.annotation.SuppressLint
import allen.town.podcast.common.util.ImageUtils.getColoredVectorDrawable
import allen.town.podcast.common.util.TopSnackbarUtil.showSnack
import allen.town.podcast.R
import allen.town.podcast.core.service.download.DownloadRequestCreator
import allen.town.podcast.core.service.download.DownloadService
import allen.town.podcast.core.service.download.Downloader
import allen.town.podcast.core.storage.DBReader
import allen.town.podcast.core.storage.DBTasks
import allen.town.podcast.core.storage.DBWriter
import allen.town.podcast.core.util.DownloadErrorLabel
import allen.town.podcast.core.util.download.DownloadLogSections
import allen.town.podcast.dialog.DownloadStatusDetailsDialog
import allen.town.podcast.model.download.DownloadError
import allen.town.podcast.model.download.DownloadStatus
import allen.town.podcast.model.feed.Feed
import allen.town.podcast.model.feed.FeedMedia
import allen.town.podcast.viewholder.DownloadLogViewHolder
import android.app.Activity
import android.text.format.DateUtils
import android.text.format.Formatter
import android.util.Log
import io.reactivex.Completable
import io.reactivex.schedulers.Schedulers
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import allen.town.podcast.theme.ThemeStore.Companion.accentColor

/**
 * The downloads "Activity" list: an "In progress" section with the running downloads, then a
 * "Problems" section with the download log (which only keeps failures and warnings). Row
 * positions are mapped by [DownloadLogSections]; never compute offsets by hand here.
 */
class DownloadLogAdapter(private val context: Activity) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var downloadLog: List<DownloadStatus> = ArrayList()
    private var runningDownloads: List<Downloader> = ArrayList()
    private var sections = DownloadLogSections(0, 0)

    fun setDownloadLog(downloadLog: List<DownloadStatus>) {
        this.downloadLog = downloadLog
        sections = DownloadLogSections(runningDownloads.size, downloadLog.size)
        notifyDataSetChanged()
    }

    fun setRunningDownloads(runningDownloads: List<Downloader>) {
        val sameRows = runningDownloads.size == this.runningDownloads.size
        this.runningDownloads = runningDownloads
        sections = DownloadLogSections(runningDownloads.size, downloadLog.size)
        val first = sections.firstRunningPosition()
        if (sameRows && first >= 0) {
            // progress tick: only the running rows changed
            notifyItemRangeChanged(first, runningDownloads.size)
        } else if (!sameRows) {
            notifyDataSetChanged()
        }
    }

    private class SectionHeaderViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.downloadlog_section_header, parent, false)
    ) {
        val title: TextView = itemView as TextView
    }

    private fun bind(holder: DownloadLogViewHolder, status: DownloadStatus, logIndex: Int) {
        // The row body opens the details; the round button keeps its own action (retry).
        holder.itemView.setOnClickListener { DownloadStatusDetailsDialog.show(context, status) }
        var statusText: String? = ""
        if (status.feedfileType == Feed.FEEDFILETYPE_FEED) {
            statusText += context.getString(R.string.download_type_feed)
        } else if (status.feedfileType == FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
            statusText += context.getString(R.string.download_type_media)
        }
        statusText += " · "
        statusText += DateUtils.getRelativeTimeSpanString(
            status.completionDate.time,
            System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, 0
        )
        holder.status.text = statusText
        if (status.title != null) {
            holder.title.text = status.title
        } else {
            holder.title.setText(R.string.download_log_title_unknown)
        }
        if (status.isSuccessful) {
            holder.icon.setColorFilter(
                ContextCompat.getColor(
                    context,
                    R.color.download_success_green
                )
            )
            holder.icon.setImageResource(R.drawable.ic_round_check_circle_outline_24)
            holder.icon.contentDescription = context.getString(R.string.download_successful)
            holder.secondaryActionButton.visibility = View.INVISIBLE
            holder.reason.visibility = View.GONE
        } else {
            holder.icon.setColorFilter(ContextCompat.getColor(context, R.color.download_failed_red))
            holder.icon.setImageResource(R.drawable.ic_round_error_outline_24)
            holder.icon.contentDescription = context.getString(R.string.error_label)
            holder.reason.setText(DownloadErrorLabel.from(status.reason))
            holder.reason.visibility = View.VISIBLE
            // A duplicate-episode warning is a host-side problem: the feed itself refreshed fine,
            // so a retry button would only re-report the same warning.
            val isDuplicateWarning = status.reason == DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE
            if (isDuplicateWarning || newerWasSuccessful(
                    logIndex,
                    status.feedfileType, status.feedfileId
                ) || status.feedfileId.toInt() == 0
            ) {
                //feedId=0 means it was not subscribed at the time, so refreshing is pointless. The database insert guards against this, but a normal feed can still fail one refresh and then be auto-cleaned because it was never subscribed.
                holder.secondaryActionButton.visibility = View.INVISIBLE
                holder.secondaryActionButton.setOnClickListener(null)
                holder.secondaryActionButton.tag = null
            } else {
                holder.secondaryActionButton.setPlayPauseDrawable(
                    getColoredVectorDrawable(
                        context,
                        R.drawable.ic_refresh,
                        accentColor(context)
                    ),
                    getColoredVectorDrawable(
                        context,
                        R.drawable.ic_refresh,
                        accentColor(context)
                    )
                )
                holder.secondaryActionButton.visibility = View.VISIBLE
                holder.secondaryActionButton.contentDescription = context.getString(R.string.retry_label)
                if (status.feedfileType == Feed.FEEDFILETYPE_FEED) {
                    holder.secondaryActionButton.setOnClickListener { v: View? ->
                        holder.secondaryActionButton.visibility = View.INVISIBLE
                        val feed = DBReader.getFeed(status.feedfileId)
                        if (feed == null) {
                            Log.e(TAG, "Could not find feed for feed id: " + status.feedfileId)
                            return@setOnClickListener
                        }
                        DBTasks.forceRefreshFeed(context, feed, true)
                    }
                } else if (status.feedfileType == FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
                    holder.secondaryActionButton.setOnClickListener { v: View? ->
                        holder.secondaryActionButton.visibility = View.INVISIBLE
                        val media = DBReader.getFeedMedia(status.feedfileId)
                        if (media == null) {
                            Log.e(
                                TAG,
                                "Could not find feed media for feed id: " + status.feedfileId
                            )
                            return@setOnClickListener
                        }
                        DownloadService.download(
                            context,
                            true,
                            DownloadRequestCreator.create(media).build()
                        )
                        showSnack(context, R.string.status_downloading_label, Toast.LENGTH_SHORT)
                    }
                }
            }
        }
    }

    @SuppressLint("CheckResult") // fire-and-forget: app-scoped DB work with its own onError; nothing to dispose
    private fun bind(holder: DownloadLogViewHolder, downloader: Downloader) {
        holder.itemView.setOnClickListener(null)
        holder.itemView.isClickable = false
        val request = downloader.downloadRequest
        holder.title.text = request.title
        holder.secondaryActionButton.setPlayPauseDrawable(
            getColoredVectorDrawable(
                context,
                R.drawable.ic_cancel,
                accentColor(context)
            ),
            getColoredVectorDrawable(
                context,
                R.drawable.ic_cancel,
                accentColor(context)
            )
        )
        holder.secondaryActionButton.contentDescription =
            context.getString(R.string.cancel_download_label)
        holder.secondaryActionButton.visibility = View.VISIBLE
        holder.secondaryActionButton.tag = downloader
        holder.secondaryActionButton.setOnClickListener { v: View? ->
            DownloadService.cancel(
                context, request.source
            )
            if (request.feedfileType == FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
                // the media row may be gone (feed unsubscribed / cleaned up) by the time the
                // user taps cancel; also keep the DB read off the main thread
                Completable.fromAction {
                    val feedItem = DBReader.getFeedMedia(request.feedfileId)?.item ?: return@fromAction
                    feedItem.disableAutoDownload()
                    DBWriter.setFeedItem(feedItem)
                }.subscribeOn(Schedulers.io())
                    .subscribe({ }, { Log.e(TAG, Log.getStackTraceString(it)) })
            }
        }
        holder.reason.visibility = View.GONE
        holder.icon.setImageResource(R.drawable.ic_round_arrow_circle_down_24)
        holder.icon.setColorFilter(accentColor(context))
        holder.icon.contentDescription = context.getString(R.string.status_downloading_label)
        var percentageWasSet = false
        var status: String? = ""
        if (request.feedfileType == Feed.FEEDFILETYPE_FEED) {
            status += context.getString(R.string.download_type_feed)
        } else if (request.feedfileType == FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
            status += context.getString(R.string.download_type_media)
        }
        status += " · "
        if (request.soFar <= 0) {
            status += context.getString(R.string.download_pending)
        } else {
            status += Formatter.formatShortFileSize(context, request.soFar)
            if (request.size != DownloadStatus.SIZE_UNKNOWN.toLong()) {
                status += " / " + Formatter.formatShortFileSize(context, request.size)
                holder.secondaryActionButton.progress = request.progressPercent
                //                holder.secondaryActionProgress.setPercentage(
//                        0.01f * Math.max(1, request.getProgressPercent()), request);
                percentageWasSet = true
            }
        }
        if (!percentageWasSet) {
            holder.secondaryActionButton.progress = 0
        }
        holder.status.text = status
    }

    private fun newerWasSuccessful(downloadStatusIndex: Int, feedTypeId: Int, id: Long): Boolean {
        for (i in 0 until downloadStatusIndex) {
            val status = downloadLog[i]
            if (status.feedfileType == feedTypeId && status.feedfileId == id && status.isSuccessful) {
                return true
            }
        }
        return false
    }

    /** The running download, the log entry, or null for a header or a stale position. */
    fun getItem(position: Int): Any? {
        val runningIndex = sections.toRunningIndex(position)
        if (runningIndex in runningDownloads.indices) {
            return runningDownloads[runningIndex]
        }
        val logIndex = sections.toLogIndex(position)
        if (logIndex in downloadLog.indices) {
            return downloadLog[logIndex]
        }
        return null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == R.id.view_type_download_section_header) {
            SectionHeaderViewHolder(parent)
        } else {
            DownloadLogViewHolder(context, parent)
        }
    }

    // The view types are resource ids because the recycled view pool is shared with other lists.
    override fun getItemViewType(position: Int): Int {
        return when (sections.getRowType(position)) {
            DownloadLogSections.ROW_RUNNING_HEADER,
            DownloadLogSections.ROW_LOG_HEADER -> R.id.view_type_download_section_header
            DownloadLogSections.ROW_RUNNING -> R.id.view_type_download_running
            else -> R.id.view_type_download_status
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (sections.getRowType(position)) {
            DownloadLogSections.ROW_RUNNING_HEADER -> (holder as? SectionHeaderViewHolder)?.title?.text =
                context.getString(R.string.downloads_section_in_progress, sections.runningCount)
            DownloadLogSections.ROW_LOG_HEADER -> (holder as? SectionHeaderViewHolder)?.title?.text =
                context.getString(R.string.downloads_section_problems, sections.logCount)
            else -> {
                val rowHolder = holder as? DownloadLogViewHolder ?: return
                rowHolder.secondaryActionButton.setAccentDefaultTheme()
                // A stale position after the log changed has no row to show; leave the holder as it is.
                when (val item = getItem(position)) {
                    is DownloadStatus -> bind(rowHolder, item, sections.toLogIndex(position))
                    is Downloader -> bind(rowHolder, item)
                    else -> return
                }
            }
        }
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getItemCount(): Int {
        return sections.itemCount
    }

    companion object {
        private const val TAG = "DownloadLogAdapter"
    }
}