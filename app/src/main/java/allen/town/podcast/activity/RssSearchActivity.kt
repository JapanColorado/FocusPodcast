package allen.town.podcast.activity

import allen.town.focus_common.activity.DialogActivity
import allen.town.focus_common.views.AccentMaterialDialog
import allen.town.podcast.R
import allen.town.podcast.activity.MainActivity.Companion.getIntentToOpenFeed
import allen.town.podcast.core.feed.FeedUrlNotFoundException
import allen.town.podcast.core.service.download.Downloader
import allen.town.podcast.core.util.StorageUtils
import allen.town.podcast.core.util.URLChecker
import allen.town.podcast.databinding.RssSearchActivityBinding
import allen.town.podcast.discovery.PodcastSearcherRegistry
import allen.town.podcast.discovery.RetrieveFeedUtil
import allen.town.podcast.model.feed.Feed
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.annotation.UiThread
import androidx.core.app.NavUtils
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus

/**
 * Downloads a feed from a feed URL and parses it. Subclasses can display the
 * feed object that was parsed. This activity MUST be started with a given URL
 * or an Exception will be thrown.
 *
 *
 * If the feed cannot be downloaded or parsed, an error dialog will be displayed
 * and the activity will finish as soon as the error dialog is closed.
 */
class RssSearchActivity : DialogActivity() {
    private val feeds: List<Feed>? = null
    private var feed: Feed? = null
    private val downloader: Downloader? = null
    private var username: String? = null
    private var password: String? = null
    private var isPaused = false
    private var dialog: Dialog? = null
    private var download: Disposable? = null
    private val parser: Disposable? = null
    private val updater: Disposable? = null
    private var feedTitle: String? = null
    private var feedAuthor: String? = null
    private var feedCoverUrl: String? = null
    private lateinit var viewBinding: RssSearchActivityBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StorageUtils.checkStorageAvailability(this)
        viewBinding = RssSearchActivityBinding.inflate(
            layoutInflater
        )
        setContentView(viewBinding.root)
        viewBinding.transparentBackground.setOnClickListener { v: View? -> finish() }
        var feedUrl: String? = null
        if (intent.hasExtra(ARG_FEEDURL)) {
            feedUrl = intent.getStringExtra(ARG_FEEDURL)
        } else if (TextUtils.equals(intent.action, Intent.ACTION_SEND)) {
            feedUrl = intent.getStringExtra(Intent.EXTRA_TEXT)
        } else if (TextUtils.equals(intent.action, Intent.ACTION_VIEW)) {
            feedUrl = intent.dataString
        }
        if (feedUrl == null) {
            Log.e(TAG, "feedUrl is null.")
            showNoPodcastFoundError()
        } else {
            if (intent.hasExtra(ARG_FEED_TITLE)) {
                feedTitle = intent.getStringExtra(ARG_FEED_TITLE)
            }
            if (intent.hasExtra(ARG_FEED_AUTHOR)) {
                feedAuthor = intent.getStringExtra(ARG_FEED_AUTHOR)
            }
            if (intent.hasExtra(ARG_FEED_COVER_URL)) {
                feedCoverUrl = intent.getStringExtra(ARG_FEED_COVER_URL)
            }
            Log.d(TAG, "feed url $feedUrl")
            setLoadingLayout()
            // Remove subscribeonandroid.com from feed URL in order to subscribe to the actual feed URL
            if (feedUrl.contains("subscribeonandroid.com")) {
                feedUrl = feedUrl.replaceFirst("((www.)?(subscribeonandroid.com/))".toRegex(), "")
            }
            if (savedInstanceState != null) {
                username = savedInstanceState.getString("username")
                password = savedInstanceState.getString("password")
            }
            lookupUrlAndDownload(feedUrl)
        }
    }

    /**
     * Dialog shown when the podcast was not found
     */
    @UiThread
    fun showNoPodcastFoundError() {
        if (isFinishing || isDestroyed) {
            return
        }
        AccentMaterialDialog(
            this@RssSearchActivity,
            R.style.MaterialAlertDialogTheme
        )
            .setNeutralButton(android.R.string.ok) { dialog: DialogInterface?, which: Int -> finish() }
            .setTitle(R.string.error_label)
            .setMessage(R.string.null_value_podcast_error)
            .setOnDismissListener { dialog1: DialogInterface? ->
                setResult(RESULT_ERROR)
                finish()
            }
            .show()
    }

    /**
     * Displays a progress indicator.
     */
    private fun setLoadingLayout() {
        viewBinding.progressBar.visibility = View.VISIBLE
    }

    override fun onStart() {
        super.onStart()
        isPaused = false
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        isPaused = true
        EventBus.getDefault().unregister(this)
        if (downloader != null && !downloader.isFinished) {
            downloader.cancel()
        }
        dialog?.let {
            if (it.isShowing) {
                it.dismiss()
            }
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        updater?.dispose()
        download?.dispose()
        parser?.dispose()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("username", username)
        outState.putString("password", password)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            val destIntent = Intent(this, MainActivity::class.java)
            if (NavUtils.shouldUpRecreateTask(this, destIntent)) {
                startActivity(destIntent)
            } else {
                NavUtils.navigateUpFromSameTask(this)
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Parse items from a feed url
     *
     * @param url
     */
    private fun lookupUrlAndDownload(url: String) {
        download = PodcastSearcherRegistry.lookupUrl(url)
            .subscribeOn(Schedulers.io())
            // the fallback search blocks, so it has to stay on the io thread the lookup failed on
            .onErrorResumeNext { error: Throwable ->
                if (error is FeedUrlNotFoundException) {
                    val retrieved = RetrieveFeedUtil.tryToRetrieveFeedUrlBySearch(error)
                    if (TextUtils.isEmpty(retrieved)) Single.error(error) else Single.just(retrieved)
                } else {
                    Single.error(error)
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { resolved: String -> startFeedDownload(resolved) }
            ) { error: Throwable? ->
                Log.e(TAG, "looking up the feed url failed", error)
                showNoPodcastFoundError()
            }
    }

    /**
     * Download the feed RSS file from its url to local storage
     *
     * @param url
     */
    private fun startFeedDownload(url: String) {
        Log.d(TAG, "prepare url")
        val preparedUrl = URLChecker.prepareURL(url)
        val feed = Feed(preparedUrl, null)
        this.feed = feed
        if (!TextUtils.isEmpty(feedTitle)) {
            feed.title = feedTitle
        }
        if (!TextUtils.isEmpty(feedAuthor)) {
            feed.author = feedAuthor
        }
        if (!TextUtils.isEmpty(feedCoverUrl)) {
            feed.imageUrl = feedCoverUrl
        }
        openFeed()
    }

    private fun openFeed() {
        // feed.getId() is always 0, we have to retrieve the id from the feed list from
        // the database
        val intent = getIntentToOpenFeed(this, feed)
        finish()
        startActivity(intent)
    }



    companion object {
        const val ARG_FEEDURL = "arg.feedurl"
        const val ARG_FEED_TITLE = "arg.feed_title"
        const val ARG_FEED_AUTHOR = "arg.feed_author"
        const val ARG_FEED_COVER_URL = "arg.feed_cover_url"

        // Optional argument: specify a title for the actionbar.
        private const val RESULT_ERROR = 2
        private const val TAG = "OnlineFeedViewActivity"
        fun newFeedParams(
            intent: Intent,
            feedAuthor: String?,
            feedTitle: String?,
            feedCoverUrl: String?
        ) {
            intent.putExtra(ARG_FEED_AUTHOR, feedAuthor)
            intent.putExtra(ARG_FEED_TITLE, feedTitle)
            intent.putExtra(ARG_FEED_COVER_URL, feedCoverUrl)
        }

        @JvmStatic
        fun feedInFeedlist(feeds: List<Feed>?, feed: Feed?): Boolean {
            if (feeds == null || feed == null) {
                return false
            }
            for (f in feeds) {
                if (f.identifyingValue == feed.identifyingValue) {
                    return true
                }
            }
            return false
        }

        @JvmStatic
        fun getFeedId(feeds: List<Feed>?, feed: Feed?): Long {
            if (feeds == null || feed == null) {
                return 0
            }
            for (f in feeds) {
                if (f.identifyingValue == feed.identifyingValue) {
                    return f.id
                }
            }
            return 0
        }
    }
}