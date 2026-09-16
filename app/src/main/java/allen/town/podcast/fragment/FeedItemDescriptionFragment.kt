package allen.town.podcast.fragment

import allen.town.podcast.R
import allen.town.podcast.core.storage.DBReader
import allen.town.podcast.core.util.playback.PlaybackController
import allen.town.podcast.core.util.playback.Timeline
import allen.town.podcast.model.feed.FeedMedia
import allen.town.podcast.view.PodWebView
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.core.util.Consumer
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import code.name.monkey.appthemehelper.util.scroll.ThemedFastScroller.create
import com.faltenreich.skeletonlayout.SkeletonLayout
import io.reactivex.Maybe
import io.reactivex.MaybeEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

/**
 * Displays the episode show notes
 */
class FeedItemDescriptionFragment : Fragment() {
    private lateinit var webvDescription: PodWebView
    private var webViewLoader: Disposable? = null
    private var controller: PlaybackController? = null
    lateinit var skeletonLayout: SkeletonLayout

    /** Kept so the pending post can be cancelled when the WebView goes away. */
    private val restoreScrollPosition = Runnable { restoreFromPreference() }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.item_description_fragment, container, false)
        webvDescription = root.findViewById(R.id.webview)
        skeletonLayout = root.findViewById(R.id.skeletonLayout)
        skeletonLayout.showSkeleton()
        val nestedScrollView = root.findViewById<NestedScrollView>(R.id.nested_scroll)
        nestedScrollView.isNestedScrollingEnabled = false
        create(nestedScrollView)
        webvDescription.setTimecodeSelectedListener(Consumer { time: Int? ->
            if (time != null) {
                controller?.seekTo(time)
            }
        })
        webvDescription.setWebChromeClient(object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                //past experience shows this callback always fires
                if (newProgress == 100) {
                    skeletonLayout.setVisibility(View.GONE)
                }
            }
        })
        webvDescription.setPageFinishedListener(Runnable {
            // Restoring the scroll position might not always work
            webvDescription.postDelayed(restoreScrollPosition, 50)
        })
        root.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View, left: Int, top: Int, right: Int,
                bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
            ) {
                if (root.measuredHeight != webvDescription.getMinimumHeight()) {
                    webvDescription.setMinimumHeight(root.measuredHeight)
                }
                root.removeOnLayoutChangeListener(this)
            }
        })
        registerForContextMenu(webvDescription)
        return root
    }

    override fun onDestroy() {
        super.onDestroy()
        webvDescription.removeCallbacks(restoreScrollPosition)
        webvDescription.removeAllViews()
        webvDescription.destroy()
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return webvDescription.onContextItemSelected(item)
    }

    private fun load() {
        webViewLoader?.dispose()
        val context = context ?: return
        val playbackController = controller ?: return
        webViewLoader = Maybe.create { emitter: MaybeEmitter<String?> ->
            val media = playbackController.media
            if (media == null) {
                emitter.onComplete()
                return@create
            }
            if (media is FeedMedia) {
                val feedMedia = media
                if (feedMedia.item == null) {
                    feedMedia.item = DBReader.getFeedItem(feedMedia.itemId)
                }
                DBReader.loadDescriptionOfFeedItem(feedMedia.item)
            }
            val timeline = Timeline(context, media.description, media.duration)
            emitter.onSuccess(timeline.processShownotes())
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ data: String? ->
                webvDescription.loadDataWithBaseURL(
                    "https://127.0.0.1", data.orEmpty(), "text/html",
                    "utf-8", "about:blank"
                )
            }) { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) }
    }

    override fun onPause() {
        super.onPause()
        savePreference()
    }

    private fun savePreference() {
        val prefs = requireActivity().getSharedPreferences(PREF, Activity.MODE_PRIVATE)
        val editor = prefs.edit()
        val media = controller?.media
        if (media != null) {
            editor.putInt(PREF_SCROLL_Y, webvDescription.scrollY)
            editor.putString(
                PREF_PLAYABLE_ID, media.identifier
                    .toString()
            )
        } else {
            editor.putInt(PREF_SCROLL_Y, -1)
            editor.putString(PREF_PLAYABLE_ID, "")
        }
        editor.apply()
    }

    private fun restoreFromPreference(): Boolean {
        val activity: Activity? = activity
        if (activity != null) {
            val prefs = activity.getSharedPreferences(PREF, Activity.MODE_PRIVATE)
            val id = prefs.getString(PREF_PLAYABLE_ID, "")
            val scrollY = prefs.getInt(PREF_SCROLL_Y, -1)
            val media = controller?.media
            if (scrollY != -1 && media != null && id == media.identifier.toString()) {
                webvDescription.scrollTo(webvDescription.scrollX, scrollY)
                return true
            }
        }
        return false
    }


    override fun onStart() {
        super.onStart()
        val playbackController = object : PlaybackController(requireActivity()) {
            override fun loadMediaInfo() {
                load()
            }
        }
        controller = playbackController
        playbackController.init()
        load()
    }

    override fun onStop() {
        super.onStop()
        webViewLoader?.dispose()
        controller?.release()
        controller = null
    }

    companion object {
        private const val TAG = "ItemDescriptionFragment"
        private const val PREF = "ItemDescriptionFragmentPrefs"
        private const val PREF_SCROLL_Y = "prefScrollY"
        private const val PREF_PLAYABLE_ID = "prefPlayableId"
    }
}