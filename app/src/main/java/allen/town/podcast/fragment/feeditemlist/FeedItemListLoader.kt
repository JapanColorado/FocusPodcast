package allen.town.podcast.fragment.feeditemlist

import allen.town.podcast.common.util.Timber
import allen.town.podcast.MyApp.Companion.runOnUiThread
import allen.town.podcast.activity.RssSearchActivity.Companion.feedInFeedlist
import allen.town.podcast.activity.RssSearchActivity.Companion.getFeedId
import allen.town.podcast.core.feed.FeedUrlNotFoundException
import allen.town.podcast.core.service.download.DownloadRequestCreator
import allen.town.podcast.core.service.download.DownloadService
import allen.town.podcast.core.storage.DBReader
import allen.town.podcast.core.util.FeedItemPermutors
import allen.town.podcast.discovery.PodcastSearcherRegistry
import allen.town.podcast.discovery.RetrieveFeedUtil.tryToRetrieveFeedUrlBySearch
import allen.town.podcast.model.feed.Feed
import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.util.Log
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

/**
 * Owns the feed that [allen.town.podcast.fragment.FeedItemlistFragment] displays and everything
 * needed to get hold of it: reading it from the database by id, by iTunes id or by download url,
 * looking the feed url up through [PodcastSearcherRegistry] when the feed is not local yet, kicking
 * off (exactly once) the download of an unsubscribed feed, sorting the loaded items, and watching
 * download events until the freshly downloaded feed turns up in the database so its real id can be
 * picked up. It exposes the loaded [feed] and [feedID] as mutable state for the fragment to render
 * and disposes both of its subscriptions in [dispose].
 */
internal class FeedItemListLoader(
    private val contextProvider: () -> Context?,
    private val onDetailViewReady: () -> Unit,
    private val onFeedUrlFailure: () -> Unit
) {
    var feedID: Long = 0
    var feed: Feed? = null

    var isDownloadingFeed = false
        private set

    private var finalGetFeedUrl = false
    private var feeds: List<Feed>? = null
    private var disposable: Disposable? = null
    private var updateDownloadStatus: Disposable? = null

    /**
     * Load the items
     */
    fun loadItems(onLoaded: (Feed?) -> Unit, onError: (Throwable?) -> Unit) {
        disposable?.dispose()
        disposable = Observable.fromCallable({ loadData() })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result: Feed? -> onLoaded(result) },
                { error: Throwable? -> onError(error) }
            )
    }

    /**
     * A download finished: once the feed shows up in the database, adopt its real id.
     * @param onFeedIdResolved run on the main thread after [feedID] was updated
     */
    fun refreshDownloadingFeedState(onFeedIdResolved: () -> Unit) {
        val currentFeed = feed
        if ((currentFeed != null) && !DownloadService.isDownloadingFile(
                currentFeed.download_url
            ) && isDownloadingFeed
        ) {
            // DownloadEvents arrive on every progress tick; do not stack one query per event
            updateDownloadStatus?.dispose()
            updateDownloadStatus = Observable.fromCallable(
                { DBReader.getAllFeedList() })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { feeds: List<Feed>? ->
                        this.feeds = feeds
                        if (feedInFeedlist(feeds, feed)) {
                            //download finished, the feed is now in the database
                            isDownloadingFeed = false
                            Log.i(TAG, "set isDownloadingFeed = false ")
                            //look up the feedId in the database (by download url)
                            feedID = getFeedId(feeds, feed)
                            onFeedIdResolved()
                        }
                    }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) }
                )
        }
    }

    fun dispose() {
        disposable?.dispose()
        updateDownloadStatus?.dispose()
    }

    @SuppressLint("CheckResult") // fire-and-forget: app-scoped DB work with its own onError; nothing to dispose
    private fun loadData(): Feed? {
        if (feedID > 0) {
            //a feedId means the feed exists in the database
            feed = DBReader.getFeed(feedID, true)
        }
        var currentFeed = feed ?: return null
        onDetailViewReady()
        if (currentFeed.id == 0L) {
            val loadedFeed = currentFeed
            val feedFromDb: Feed? = if (!TextUtils.isEmpty(loadedFeed.itunesId)) {
                //comes from iTunes
                Timber.i("feedUrl from itunes ")
                DBReader.getFeedByItunesFeedId(loadedFeed.itunesId, true)
            } else {
                DBReader.getFeed(loadedFeed.download_url, true)
            }
            if (feedFromDb == null) {
                //only reached when the id is 0 and the feedUrl lookup also fails; otherwise duplicate rows would be created
                PodcastSearcherRegistry.lookupUrl(loadedFeed.download_url)
                    .subscribeOn(Schedulers.trampoline())
                    .observeOn(Schedulers.trampoline())
                    .subscribe(
                        { feedUrl: String ->
                            Timber.i("get feedUrl from itunes " + feedUrl)
                            loadedFeed.setDownload_url(feedUrl)
                            finalGetFeedUrl = true
                        },
                        { error: Throwable? ->
                            if (error is FeedUrlNotFoundException) {
                                finalGetFeedUrl = !TextUtils.isEmpty(
                                    tryToRetrieveFeedUrlBySearch(error)
                                )
                            } else {
                                loadedFeed.setLastUpdateFailed(true)
                                runOnUiThread {
                                    //run on the main thread
                                    onFeedUrlFailure()
                                }
                                Log.e(TAG, Log.getStackTraceString(error))
                            }
                        })
                Log.i(TAG, "check isDownloadingFeed $isDownloadingFeed")
                if (finalGetFeedUrl) {
                    if (!isDownloadingFeed) {
                        DownloadService.download(
                            contextProvider(), false,
                            DownloadRequestCreator.create(loadedFeed).build()
                        )
                        isDownloadingFeed = true
                    } else {
                        //loadData also runs again after a failed download, and we must not start another download then (other events can reach this branch too, so verify nothing is actually downloading) or we end up in an infinite loop
                        if (!DownloadService.isDownloadingFile(
                                loadedFeed.download_url
                            )
                        ) {
                            Log.i(TAG, "not downloading setLastUpdateFailed ")
                            isDownloadingFeed = false
                            loadedFeed.setLastUpdateFailed(true)
                        }
                    }
                }
                return loadedFeed
            } else {
                //the feed was found in the database, so use the stored values
                feed = feedFromDb
                currentFeed = feedFromDb
                feedID = feedFromDb.id
            }
        }
        DBReader.loadAdditionalFeedItemListData(currentFeed.items)
        val sortOrder = currentFeed.sortOrder
        if (sortOrder != null) {
            val feedItems = currentFeed.items
            FeedItemPermutors.getPermutor(sortOrder).reorder(feedItems)
            currentFeed.items = feedItems
        }
        return currentFeed
    }

    private companion object {
        private const val TAG = "ItemlistFragment"
    }
}
