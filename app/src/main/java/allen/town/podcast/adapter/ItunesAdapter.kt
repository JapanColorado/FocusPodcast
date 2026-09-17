package allen.town.podcast.adapter

import allen.town.podcast.common.util.ImageUtils.getColoredVectorDrawable
import allen.town.podcast.common.util.Timber
import allen.town.podcast.common.util.TopSnackbarUtil.showSnack
import allen.town.podcast.R
import allen.town.podcast.activity.MainActivity
import allen.town.podcast.activity.RssSearchActivity
import allen.town.podcast.adapter.ItunesAdapter.PodcastViewHolder
import allen.town.podcast.core.feed.FeedUrlNotFoundException
import allen.town.podcast.core.service.download.DownloadRequestCreator
import allen.town.podcast.core.service.download.DownloadService
import allen.town.podcast.core.storage.DBReader
import allen.town.podcast.core.storage.DBWriter
import allen.town.podcast.core.util.Converter
import allen.town.podcast.core.util.FeedItemUtil
import allen.town.podcast.dialog.RemoveFeedDialog
import allen.town.podcast.discovery.EpisodeSearchResult
import allen.town.podcast.discovery.PodcastSearchResult
import allen.town.podcast.discovery.PodcastSearcherRegistry
import allen.town.podcast.discovery.RetrieveFeedUtil
import allen.town.podcast.event.playback.PlaybackPositionEvent
import allen.town.podcast.fragment.FeedItemsViewPagerFragment
import allen.town.podcast.model.feed.Feed
import allen.town.podcast.model.feed.FeedItem
import allen.town.podcast.model.feed.FeedMedia
import allen.town.podcast.model.playback.MediaType
import allen.town.podcast.view.PlayPauseProgressButton
import android.content.Intent
import android.text.Html
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import allen.town.podcast.theme.ThemeStore.Companion.accentColor
import allen.town.podcast.theme.util.ATHUtil.resolveColor
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.card.MaterialCardView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers

class ItunesAdapter(
    /**
     * Related Context
     */
    private val context: FragmentActivity,
    /**
     * List holding the podcasts found in the search
     */
    private val data: MutableList<PodcastSearchResult>,
    val fromSearch: Boolean,
    val typeEpisodes: Boolean
) : RecyclerView.Adapter<PodcastViewHolder>() {
    private var subscribedFeedsList: List<Feed> = ArrayList()

    /**
     * Row-scoped lookups and subscribe actions. Cleared when the adapter leaves its RecyclerView,
     * so a late callback cannot bind into a recycled row or touch a finished activity.
     */
    private val pendingWork = CompositeDisposable()

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        pendingWork.clear()
        super.onDetachedFromRecyclerView(recyclerView)
    }

    fun addAll(newData: List<PodcastSearchResult>?) {
        data.clear()
        data.addAll(newData ?: emptyList())
        notifyDataSetChanged()
    }

    fun setSubscribedFeeds(list: List<Feed>) {
        subscribedFeedsList = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PodcastViewHolder {
        val view = context.layoutInflater
            .inflate(R.layout.itunes_podcast_listitem, parent, false)
        return PodcastViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: PodcastViewHolder, position: Int) {
        //Current podcast
        val podcast = data[viewHolder.bindingAdapterPosition]
        val feedOut = createFeed(podcast)
        viewHolder.dateView.text = podcast.publishDate
        viewHolder.sumView.text =
            if (!TextUtils.isEmpty(podcast.description)) Html.fromHtml(podcast.description) else ""
        viewHolder.sumView.visibility =
            if (!TextUtils.isEmpty(podcast.description)) View.VISIBLE else View.GONE
        val isSubscribedOut = RssSearchActivity.feedInFeedlist(subscribedFeedsList, feedOut)
        //subscribe button state
        viewHolder.subscribe_button.setAccentDefaultTheme()
        (viewHolder.itemView as MaterialCardView).isChecked = false
        viewHolder.playing_lottie.visibility = View.GONE

        //Update the empty imageView with the image from the feed
        Glide.with(context)
            .load(podcast.imageUrl)
            .apply(
                RequestOptions()
                    .placeholder(R.drawable.ic_podcast_background_round)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .transforms(
                        CenterCrop(),
                        RoundedCorners((4 * context.resources.displayMetrics.density).toInt())
                    )
                    .dontAnimate()
            )
            .into(viewHolder.coverView)
        if (typeEpisodes) {
            bindEpisodesView(podcast, viewHolder)
        } else {
            bindFeedView(podcast, isSubscribedOut, viewHolder)
        }
    }

    private val feedItems: ArrayList<FeedItem> = ArrayList()
    fun findItemInList(feedItem: FeedItem?): FeedItem? {
        feedItem?.run {
            for (item in feedItems) {
                if (TextUtils.equals(item.identifyingValue, feedItem.identifyingValue)) {
                    return item
                }
            }
        }
        return null
    }

    fun bindEpisodesView(
        podcast: PodcastSearchResult,
        viewHolder: PodcastViewHolder
    ) {
        val episodeSearchResult = podcast as EpisodeSearchResult
        viewHolder.authorView.text = Converter.getDurationStringLocalized(
            context, episodeSearchResult.duration
        )
        viewHolder.titleView.text = episodeSearchResult.episodeTitle
        val item: FeedItem? = findItemInList(FeedItem().also {
            it.title = episodeSearchResult.episodeTitle
            it.itemIdentifier = episodeSearchResult.episodeUuid
        })
        viewHolder.subscribe_button.progress = 0
        viewHolder.subscribe_button.setPlayPauseDrawable(
            getColoredVectorDrawable(
                context,
                R.drawable.ic_play_48dp,
                accentColor(context)
            ),
            getColoredVectorDrawable(
                context,
                R.drawable.ic_pause,
                accentColor(context)
            )
        )
        viewHolder.subscribe_button.setOnClickListener { view: View? ->
            viewHolder.subscribe_button.onClick(
                context
            )
        }

        if (item != null) {
            Timber.v("we found FeedItem in cache {${episodeSearchResult.title}}")
            viewHolder.bindFeedItem(item)
        } else {
            //look it up in the database and cache the result
            pendingWork.add(Observable.fromCallable {
                val feedItemFromDb = DBReader.getFeedItemByGuidOrEpisodeUrl(
                    episodeSearchResult.episodeUuid,
                    episodeSearchResult.episodeUrl
                )
                feedItemFromDb
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    Timber.v("we got FeedItem in db {${episodeSearchResult.title}}")
                    feedItems.add(it)
                    viewHolder.bindFeedItem(it)
                }) {
                    // fromCallable treats a null row as an error, so this is also the
                    // "episode not in the database yet" path
                    Timber.v(it, "no cached FeedItem for {${episodeSearchResult.title}}")
                    pendingWork.add(Observable.fromCallable {
                        val feedFromDb = DBReader.getFeed(
                            episodeSearchResult.feedUrl,
                            false
                        )
                        feedFromDb
                    }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ feed ->
                            // fromCallable turns a null row into onError, so this only runs with
                            // a real Feed; the check keeps the compiler happy about the platform type.
                            if (feed == null) {
                                return@subscribe
                            }
                            //the feed exists but not this episode, so insert the episode
                            Timber.v("we got Feed in db , but not found episode {${episodeSearchResult.title}}")
                            val feedItemToInsert = getFeedItemToInsert(episodeSearchResult, feed)
                            DBWriter.setFeedItemExcludeFeed(feedItemToInsert)
                            feedItems.add(feedItemToInsert)
                            viewHolder.bindFeedItem(feedItemToInsert)
                        }, { notFound: Throwable? ->
                            // fromCallable treats a null row as an error, so this is also the
                            // "feed not in the database yet" path
                            Timber.v(notFound, "no Feed in db for {${episodeSearchResult.title}}")
                            val feedToInsert = Feed().also {
                                it.title = episodeSearchResult.title
                                it.description = episodeSearchResult.description
                                it.author = episodeSearchResult.author
                                it.imageUrl = episodeSearchResult.imageUrl
                                it.download_url = episodeSearchResult.feedUrl
                                it.itunesId = episodeSearchResult.itunesFeedId
                            }
                            val itemToInsert =
                                getFeedItemToInsert(episodeSearchResult, feedToInsert)
                            feedToInsert.items = ArrayList<FeedItem?>().also {
                                it.add(itemToInsert)
                            }
                            DBWriter.addNewFeed(viewHolder.itemView.context, feedToInsert)
                            feedItems.add(itemToInsert)
                            viewHolder.bindFeedItem(itemToInsert)

                            Timber.v("we found nothing in db , insert feed and item {${episodeSearchResult.title}}")
                            //no feed in the database for this item, so build one
//                            notifyItemChanged(viewHolder.bindingAdapterPosition, "search_episodes")
                        }))
                })
        }
    }

    fun getFeedItemToInsert(episodeSearchResult: EpisodeSearchResult, feed: Feed): FeedItem {
        val feedItemToInsert = FeedItem().also {
            it.id = 0
            it.title = episodeSearchResult.episodeTitle
            it.pubDate = episodeSearchResult.publishDateTypeDate
            it.setDescriptionIfLonger(episodeSearchResult.description)
            it.imageUrl = episodeSearchResult.imageUrl
            it.itemIdentifier = episodeSearchResult.episodeUuid
            it.feedId = feed.id
            it.feed = feed
        }
        val itemMediaToInsert = FeedMedia(
            feedItemToInsert,
            episodeSearchResult.episodeUrl,
            0,
            episodeSearchResult.episodeMimeType
        ).also {
            it.duration = episodeSearchResult.duration.toInt()
        }
        feedItemToInsert.media = itemMediaToInsert
        return feedItemToInsert
    }

    fun bindFeedView(podcast: PodcastSearchResult, isSub: Boolean, viewHolder: PodcastViewHolder) {
        viewHolder.subscribe_button.setPlayPauseDrawable(
            getColoredVectorDrawable(
                context,
                R.drawable.ic_add,
                accentColor(context)
            ),
            getColoredVectorDrawable(
                context,
                R.drawable.ic_round_check_24,
                resolveColor(viewHolder.itemView.context, android.R.attr.windowBackground)
            )
        )
        if (isSub) {
            viewHolder.subscribe_button.setPlayingAndPlayed(true, false, false)
            viewHolder.subscribe_button.setCircleFillAndRingColor(
                accentColor(context), accentColor(context)
            )
        } else {
            viewHolder.subscribe_button.setPlayingAndPlayed(false, false, false)
            if (!podcast.itunesFeedId.isNullOrEmpty() && !podcast.feedUrl.isNullOrEmpty()) {
                //for an iTunes feed, resolve the real url first, then check the database for a subscription; if subscribed, update the iTunes id and refresh this item

                pendingWork.add(PodcastSearcherRegistry.lookupUrl(podcast.feedUrl)
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .subscribe(
                        { url: String? ->
                            val feedToModify = Feed(url, null)
                            val feedIdIndb = RssSearchActivity.getFeedId(
                                subscribedFeedsList,
                                feedToModify
                            )
                            if (feedIdIndb > 0) {
                                Timber.d("we found the feed ${podcast.title} , ${feedIdIndb} from itunes actually subed in db , just modify itunes id")
                                DBWriter.setFeedItunesId(feedToModify.also {
                                    it.itunesId = podcast.itunesFeedId
                                    it.id = feedIdIndb
                                })
                            }

                        }
                    ) { error2: Throwable? ->
                        // only used to keep an already-subscribed feed's iTunes id in sync;
                        // failing to resolve the url just leaves the row as it is
                        Timber.d(error2, "could not resolve the iTunes url for ${podcast.title}")
                    })
            }
        }
        viewHolder.subscribe_button.setOnClickListener { v: View? ->
            val feedToRemove = createFeed(podcast)
            feedToRemove.id = RssSearchActivity.getFeedId(subscribedFeedsList, feedToRemove)
            if (isSub) {
                RemoveFeedDialog.show(
                    viewHolder.itemView.context,
                    feedToRemove, object : RemoveFeedDialog.OnFeedRemovedListener {
                        override fun onFeedRemoved() {
                            notifyDataSetChanged()
                        }
                    }
                )
            } else {
                pendingWork.add(Observable.fromCallable {
                    val feedFromDb = DBReader.getFeedByItunesFeedId(feedToRemove.itunesId, true)
                    feedFromDb
                }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { feedFromDb: Feed? ->
                            DBWriter.subscribeFeed(
                                feedFromDb,
                                viewHolder.itemView.context
                            )
                        }) { error: Throwable? ->
                        // fromCallable treats a null row as an error, so this is also the
                        // "not subscribed yet" path: resolve the real url and download it
                        Timber.d(error, "feed ${podcast.title} not found by iTunes id")
                        pendingWork.add(PodcastSearcherRegistry.lookupUrl(feedToRemove.download_url)
                            .subscribeOn(Schedulers.io())
                            .observeOn(Schedulers.io())
                            .subscribe(
                                { url: String? ->
                                    feedToRemove.download_url = url
                                    feedToRemove.isNeedAutoSubscribe = true
                                    DownloadService.download(
                                        viewHolder.itemView.context,
                                        false,
                                        DownloadRequestCreator.create(feedToRemove).build()
                                    )
                                }
                            ) { error2: Throwable? ->
                                if (error2 is FeedUrlNotFoundException) {
                                    val retrieveFeedUrl =
                                        RetrieveFeedUtil.tryToRetrieveFeedUrlBySearch(error2)
                                    if (!TextUtils.isEmpty(retrieveFeedUrl)) {
                                        feedToRemove.download_url = retrieveFeedUrl
                                        feedToRemove.isNeedAutoSubscribe = true
                                        DownloadService.download(
                                            viewHolder.itemView.context,
                                            false,
                                            DownloadRequestCreator.create(feedToRemove).build()
                                        )
                                    } else {
                                        Log.e(TAG, "FeedUrlNotFoundException")
                                    }
                                } else {
                                    Log.e(TAG, Log.getStackTraceString(error2))
                                }
                            })

                        showSnack(
                            viewHolder.itemView.context,
                            R.string.subscribing_label,
                            Toast.LENGTH_SHORT
                        )
                    })
            }
        }
        viewHolder.itemView.setOnClickListener { v: View ->
            if (podcast.feedUrl == null) {
                return@setOnClickListener
            }
            val feed = createFeed(podcast)
            val intent: Intent
            val isSubscribed = RssSearchActivity.feedInFeedlist(subscribedFeedsList, feed)
            if (isSubscribed) {
                intent = MainActivity.getIntentToOpenFeedWithId(
                    v.context,
                    RssSearchActivity.getFeedId(subscribedFeedsList, feed)
                )
                intent.putExtra(MainActivity.EXTRA_STARTED_FROM_SEARCH, true)
            } else {
                intent = MainActivity.getIntentToOpenFeed(v.context, feed)
            }
            context.startActivity(intent)
        }

        // Set the title
        viewHolder.titleView.text = podcast.title
        if (podcast.author != null && !podcast.author.trim { it <= ' ' }.isEmpty()) {
            viewHolder.authorView.text = podcast.author
            viewHolder.authorView.visibility = View.VISIBLE
        } else if (podcast.feedUrl != null && !podcast.feedUrl.contains("itunes.apple.com")) {
            viewHolder.authorView.text = podcast.feedUrl
            viewHolder.authorView.visibility = View.VISIBLE
        } else {
            viewHolder.authorView.visibility = View.GONE
        }
    }

    private fun createFeed(podcastSearchResult: PodcastSearchResult): Feed {
        val feed = Feed(podcastSearchResult.feedUrl, null, podcastSearchResult.title)
        feed.author = podcastSearchResult.author
        feed.imageUrl = podcastSearchResult.imageUrl
        feed.download_url = podcastSearchResult.feedUrl
        feed.itunesId = podcastSearchResult.itunesFeedId
        feed.description = podcastSearchResult.description
        return feed
    }

    override fun getItemCount(): Int {
        return data.size
    }

    /**
     * View holder object for the GridView
     */
    class PodcastViewHolder internal constructor(view: View) : RecyclerView.ViewHolder(view) {
        /**
         * ImageView holding the Podcast image
         */
        val coverView: ImageView

        /**
         * TextView holding the Podcast title
         */
        val titleView: TextView
        val authorView: TextView
        val subscribe_button: PlayPauseProgressButton
        val dateView: TextView
        val sumView: TextView
        val playing_lottie: LottieAnimationView
        val ivIsVideo: ImageView
        private var mfeedItem: FeedItem? = null
        fun bindFeedItem(item: FeedItem) {
            Timber.v("bindFeedItem {${item.title}}")
            this.mfeedItem = item
            subscribe_button.setFeedItem(item)
            var isVideo = false
            ivIsVideo.visibility = View.GONE
            val media = item.media
            if (media != null) {
                isVideo = media.mediaType == MediaType.VIDEO
                if (isVideo) {
                    ivIsVideo.visibility = View.VISIBLE
                }
                if (FeedItemUtil.isPlaying(media) || item.isInProgress) {
                    val progress = (100.0 * media.position / media.duration).toInt()
                    Timber.d(item.title + " : " + progress + " " + item.toString())
                    subscribe_button.progress = progress
                }
                if (FeedItemUtil.isCurrentlyPlaying(media)) {
                    subscribe_button.setPlayingAndPlayed(true, false, false)
                } else {
                    subscribe_button.setPlayingAndPlayed(false, false, false)
                }
                if (FeedItemUtil.isCurrentlyPlaying(media)) {
                    (itemView as MaterialCardView).isChecked = true
                    playing_lottie.visibility = View.VISIBLE
                }
            }

            itemView.setOnClickListener { v: View ->
                (itemView.context as MainActivity).loadChildFragment(
                    FeedItemsViewPagerFragment.newInstance(
                        longArrayOf(item.id), 0
                    )
                )
            }
        }


        val isCurrentlyPlayingItem: Boolean
            get() = mfeedItem?.media != null && FeedItemUtil.isCurrentlyPlaying(mfeedItem?.media)

        fun notifyPlaybackPositionUpdated(event: PlaybackPositionEvent) {
            subscribe_button.progress = (100.0 * event.position / event.duration).toInt()
        }

        /**
         * Constructor
         *
         * @param view GridView cell
         */
        init {
            coverView = view.findViewById(R.id.imgvCover)
            titleView = view.findViewById(R.id.txtvTitle)
            authorView = view.findViewById(R.id.txtvAuthor)
            subscribe_button = view.findViewById(R.id.subscribe_button)
            dateView = view.findViewById(R.id.txtvPubDate)
            sumView = view.findViewById(R.id.txtvSum)
            playing_lottie = view.findViewById(R.id.playing_lottie)
            ivIsVideo = view.findViewById(R.id.ivIsVideo)
        }
    }

    companion object {
        private const val TAG = "ItunesAdapter"
    }

}