package allen.town.podcast.adapter

import allen.town.focus_common.util.ImageUtils.getColoredVectorDrawable
import allen.town.focus_common.util.TopSnackbarUtil.showSnack
import allen.town.podcast.R
import allen.town.podcast.core.service.download.DownloadRequestCreator
import allen.town.podcast.core.service.download.DownloadService
import allen.town.podcast.core.service.download.Downloader
import allen.town.podcast.core.storage.DBReader
import allen.town.podcast.core.storage.DBTasks
import allen.town.podcast.core.storage.DBWriter
import allen.town.podcast.core.util.DownloadErrorLabel
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
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import code.name.monkey.appthemehelper.ThemeStore.Companion.accentColor

/**
 * Displays a list of DownloadStatus entries.
 */
class DownloadLogAdapter(private val context: Activity) :
    RecyclerView.Adapter<DownloadLogViewHolder>() {
    private var downloadLog: List<DownloadStatus> = ArrayList()
    private var runningDownloads: List<Downloader> = ArrayList()
    fun setDownloadLog(downloadLog: List<DownloadStatus>) {
        this.downloadLog = downloadLog
        notifyDataSetChanged()
    }

    fun setRunningDownloads(runningDownloads: List<Downloader>) {
        val sameRows = runningDownloads.size == this.runningDownloads.size
        this.runningDownloads = runningDownloads
        if (sameRows) {
            // progress tick: only the running rows changed
            notifyItemRangeChanged(0, runningDownloads.size)
        } else {
            notifyDataSetChanged()
        }
    }

    private fun bind(holder: DownloadLogViewHolder, status: DownloadStatus?, position: Int) {
        var statusText: String? = ""
        if (status!!.feedfileType == Feed.FEEDFILETYPE_FEED) {
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
                    position - runningDownloads.size,
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

    private fun bind(holder: DownloadLogViewHolder, downloader: Downloader?, position: Int) {
        val request = downloader!!.downloadRequest
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

    fun getItem(position: Int): Any? {
        if (position < runningDownloads.size) {
            return runningDownloads[position]
        } else if (position - runningDownloads.size < downloadLog.size) {
            return downloadLog[position - runningDownloads.size]
        }
        return null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadLogViewHolder {
        return DownloadLogViewHolder(context, parent)
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return if (item is DownloadStatus) {
            VIEW_TYPE_STATUS
        } else VIEW_TYPE_DOWNLOADER
    }

    override fun onBindViewHolder(holder: DownloadLogViewHolder, position: Int) {
        val item = getItem(position)
        holder.secondaryActionButton.setAccentDefaultTheme()
        if (getItemViewType(position) == VIEW_TYPE_STATUS) {
            bind(holder, item as DownloadStatus?, position)
        } else {
            bind(holder, item as Downloader?, position)
        }
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getItemCount(): Int {
        return downloadLog.size + runningDownloads.size
    }

    companion object {
        private const val TAG = "DownloadLogAdapter"
        const val VIEW_TYPE_DOWNLOADER = 1
        private const val VIEW_TYPE_STATUS = 2
    }
}