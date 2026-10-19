package dev.russell.focuspodcast.fragment

import dev.russell.focuspodcast.common.extensions.accentColor
import dev.russell.focuspodcast.common.extensions.tint
import dev.russell.focuspodcast.common.util.TopSnackbarUtil.showSnack
import dev.russell.focuspodcast.R
import dev.russell.focuspodcast.actionbuttons.*
import dev.russell.focuspodcast.activity.MainActivity
import dev.russell.focuspodcast.core.event.DownloadEvent
import dev.russell.focuspodcast.core.feed.util.ImageResourceUtils
import dev.russell.focuspodcast.core.glide.ApGlideSettings
import dev.russell.focuspodcast.core.service.download.DownloadService
import dev.russell.focuspodcast.core.service.download.Downloader
import dev.russell.focuspodcast.core.storage.DBReader
import dev.russell.focuspodcast.core.storage.DBWriter
import dev.russell.focuspodcast.core.util.Converter
import dev.russell.focuspodcast.core.util.DateFormatter
import dev.russell.focuspodcast.core.util.FeedItemUtil
import dev.russell.focuspodcast.core.util.playback.PlaybackController
import dev.russell.focuspodcast.core.util.playback.Timeline
import dev.russell.focuspodcast.databinding.FeeditemFragmentBinding
import dev.russell.focuspodcast.event.FeedItemEvent
import dev.russell.focuspodcast.event.PlayerStatusEvent
import dev.russell.focuspodcast.event.UnreadItemsUpdateEvent
import dev.russell.focuspodcast.event.playback.PlaybackPositionEvent
import dev.russell.focuspodcast.model.feed.FeedItem
import dev.russell.focuspodcast.model.feed.FeedMedia
import dev.russell.focuspodcast.model.feed.Person
import dev.russell.focuspodcast.model.feed.Soundbite
import dev.russell.focuspodcast.util.PlayAtPosition
import dev.russell.focuspodcast.view.PodcastingViews
import dev.russell.focuspodcast.viewholder.EpisodeItemViewHolder
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.fragment.app.Fragment
import dev.russell.focuspodcast.theme.util.VersionUtils.hasMarshmallow
import dev.russell.focuspodcast.theme.util.scroll.ThemedFastScroller
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.apache.commons.lang3.ArrayUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


/**
 * Displays information about a FeedItem and actions.
 */
class FeedItemFragment : Fragment() {
    private var itemsLoaded = false
    private var itemId: Long = 0
    private var item: FeedItem? = null
    private var webviewData: String? = null
    private var downloaderList: List<Downloader>? = null
    private var actionButton1: ItemActionButton? = null
    private var actionButton2: ItemActionButton? = null
    private var disposable: Disposable? = null
    private var sizeDisposable: Disposable? = null
    private var controller: PlaybackController? = null
    private val playDisposables = CompositeDisposable()
    /** What the people row and highlights list currently show, so a reload only rebuilds on change. */
    private var boundPersons: List<Person>? = null
    private var boundSoundbites: List<Soundbite>? = null
    private lateinit var floatingPlayActionButton: ExtendedFloatingActionButton

    /** Kept so the pending post can be cancelled when the view goes away. */
    private val hideSkeleton = Runnable {
        _binding?.skeletonLayout?.visibility = View.GONE
    }
    private var _binding: FeeditemFragmentBinding? = null
    private val feedItemListFragmentBinding: FeeditemFragmentBinding
        get() = _binding ?: error("binding accessed outside of view lifecycle")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        itemId = requireArguments().getLong(ARG_FEEDITEM)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val layout = inflater.inflate(R.layout.feeditem_fragment, container, false)
        val binding = FeeditemFragmentBinding.bind(layout)
        _binding = binding
        binding.txtvPodcastL.setOnClickListener { v: View? -> openPodcast() }
        if (Build.VERSION.SDK_INT >= 23) {
            binding.txtvTitle.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
        }
        val scrollView = binding.scrollView
        ThemedFastScroller.create(scrollView)
        val playButton = (requireParentFragment() as FeedItemsViewPagerFragment)
            .extendedFloatingActionButton
        floatingPlayActionButton = playButton
        if (hasMarshmallow()) {
            scrollView.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
                if (scrollY > 0) {
                    playButton.shrink()
                } else if (scrollY < 0) {
                    playButton.extend()
                }
            }
        }
        binding.txtvTitle.setEllipsize(TextUtils.TruncateAt.END)
        binding.webvDescription.setTimecodeSelectedListener(Consumer { time: Int? ->
            val playbackController = controller
            val itemMedia = item?.media
            val controllerMedia = playbackController?.media
            if (time != null && itemMedia != null && controllerMedia != null
                && itemMedia.identifier == controllerMedia.identifier
            ) {
                playbackController.seekTo(time)
            } else {
                showSnack(activity, R.string.play_this_to_seek_position, Toast.LENGTH_LONG)
            }
        })
        registerForContextMenu(binding.webvDescription)
        binding.imgvCover.setOnClickListener(View.OnClickListener { v: View? -> openPodcast() })
        binding.progbarDownload.accentColor()

        binding.progbarPlayed.accentColor()

        binding.downloadIcon.setOnClickListener(View.OnClickListener { v: View? ->
            actionButton2?.onClick(
                activity
            )
        })
        binding.skeletonLayout.showSkeleton()

        binding.webvDescription.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress == 100) {
                    //the ScrollView nests other layouts, so the WebView takes a while to actually appear
                    _binding?.skeletonLayout?.postDelayed(hideSkeleton, 350)
                }
            }
        }
        return layout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        feedItemListFragmentBinding.addToFavoritesItemIcon.setOnClickListener {
            DBWriter.addFavoriteItem(item)
        }
        feedItemListFragmentBinding.removeFromFavoritesItemIcon.setOnClickListener {
            DBWriter.removeFavoriteItem(item)
        }
        feedItemListFragmentBinding.addToQueueItemIcon.setOnClickListener {
            DBWriter.addQueueItem(context, item)
        }
        feedItemListFragmentBinding.removeFromQueueItemIcon.setOnClickListener {
            DBWriter.removeQueueItem(context, true, item)
        }

    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        val playbackController = object : PlaybackController(requireActivity()) {
            override fun loadMediaInfo() {
                // Do nothing
            }
        }
        controller = playbackController
        playbackController.init()
        load()
    }

    override fun onResume() {
        super.onResume()
        if (itemsLoaded) {
            feedItemListFragmentBinding.skeletonLayout.visibility = View.GONE
            updateAppearance()
        }
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        controller?.release()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val binding = _binding
        binding?.skeletonLayout?.removeCallbacks(hideSkeleton)
        sizeDisposable?.dispose()
        disposable?.dispose()
        if (binding != null) {
            binding.contentRoot.removeView(binding.webvDescription)
            binding.webvDescription.destroy()
        }
        _binding = null
        boundPersons = null
        boundSoundbites = null
    }

    override fun onDestroy() {
        super.onDestroy()
        playDisposables.dispose()
    }

    private fun onFragmentLoaded() {
        val data = webviewData
        if (data != null && !itemsLoaded) {
            feedItemListFragmentBinding.webvDescription.loadDataWithBaseURL(
                "https://127.0.0.1",
                data,
                "text/html",
                "utf-8",
                "about:blank"
            )
        }
        updateAppearance()
    }

    private fun updateAppearance() {
        val item = this.item
        if (item == null) {
            Log.d(TAG, "update appearance item is null")
            return
        }
        val binding = feedItemListFragmentBinding
        binding.txtvPodcast.text = item.feed.title
        binding.txtvTitle.text = item.title
        if (item.pubDate != null) {
            val pubDateStr = DateFormatter.formatAbbrev(activity, item.pubDate)
            binding.txtvPublished.text = pubDateStr
            binding.txtvPublished.contentDescription = DateFormatter.formatForAccessibility(
                item.pubDate
            )
        }
        binding.txtvTranscriptAvailable.visibility = if (item.hasTranscript()) View.VISIBLE else View.GONE
        bindPodcastingExtras(item)
        val options = RequestOptions()
            .error(R.drawable.ic_podcast_background_round)
            .diskCacheStrategy(ApGlideSettings.AP_DISK_CACHE_STRATEGY)
            .dontAnimate()
        Glide.with(this)
            .load(item.imageLocation)
            .error(
                Glide.with(this)
                    .load(ImageResourceUtils.getFallbackImageLocation(item))
                    .apply(options)
            )
            .apply(options)
            .centerCrop()
            .into(binding.imgvCover)
        updateButtons()
    }

    /** The people chips (the episode's own, else the podcast's) and the highlights. */
    private fun bindPodcastingExtras(item: FeedItem) {
        val binding = feedItemListFragmentBinding
        val persons = item.effectivePersons
        if (persons != boundPersons) {
            boundPersons = persons
            binding.peopleSection.visibility = if (persons.isEmpty()) View.GONE else View.VISIBLE
            PodcastingViews.bindPeople(binding.peopleRow, persons)
        }
        val soundbites = item.soundbites.orEmpty()
        if (soundbites != boundSoundbites) {
            boundSoundbites = soundbites
            binding.highlightsSection.visibility =
                if (soundbites.isEmpty() || item.media == null) View.GONE else View.VISIBLE
            PodcastingViews.bindHighlights(binding.highlightsList, soundbites) { playHighlight(it) }
        }
    }

    private fun playHighlight(soundbite: Soundbite) {
        val item = this.item ?: return
        PlayAtPosition.play({ activity }, controller, item, soundbite.startMs)
            ?.let { playDisposables.add(it) }
    }

    private fun updatePlayButton() {
        //the float button is shared; ViewPager2 does not call onResume for off-screen pages, so use that to decide whether to update it
        if (!isResumed) {
            return
        }
        val playButton = actionButton1 ?: return
        val media = item?.media
        floatingPlayActionButton.icon =
            ContextCompat.getDrawable(requireContext(), playButton.drawable)
        floatingPlayActionButton.visibility = playButton.isVisibility

        floatingPlayActionButton.setOnClickListener(View.OnClickListener { v: View? ->
            actionButton1?.onClick(
                activity
            )
        })
        if (media != null) {
            if (media.duration > 0) {
                floatingPlayActionButton.text =
                    Converter.getDurationStringLong(media.duration)
                floatingPlayActionButton.contentDescription =
                    Converter.getDurationStringLocalized(
                        context, media.duration.toLong()
                    )
                floatingPlayActionButton.extend()
            } else {
                floatingPlayActionButton.setText(playButton.label)
            }
        } else {
            //no media info, so no duration is shown; setting the text before shrinking has no effect
            floatingPlayActionButton.shrink()
            floatingPlayActionButton.text = ""
        }
    }

    private var lastPosition: Int = 0

    private fun updateButtons() {
        val item = this.item ?: return // load() has not delivered yet
        val binding = feedItemListFragmentBinding
        val media = item.media
        binding.progbarDownload.visibility = View.INVISIBLE
        val downloaders = downloaderList
        if (media != null && downloaders != null) {
            for (downloader in downloaders) {
                if (downloader.downloadRequest.feedfileType == FeedMedia.FEEDFILETYPE_FEEDMEDIA
                    && downloader.downloadRequest.feedfileId == media.id
                ) {
                    binding.progbarDownload.visibility = View.VISIBLE
                    binding.progbarDownload.progress = downloader.downloadRequest.progressPercent
                }
            }
        }

        if (media == null) {
            actionButton1 = MarkAsPlayedActionButton(item)
            actionButton2 = VisitWebsiteActionButton(item)
            binding.downloadLayout.visibility = View.GONE
            binding.progbarPlayed.setVisibility(View.GONE)
        } else {
            sizeDisposable?.dispose()
            sizeDisposable =
                EpisodeItemViewHolder.setSizeTextView(media, context, binding.tvItemSize, null)
            actionButton1 = if (FeedItemUtil.isCurrentlyPlaying(media)) {
                PauseActionButton(item)
            } else if (item.feed.isLocalFeed) {
                PlayLocalActionButton(item)
            } else if (media.isDownloaded) {
                PlayActionButton(item)
            } else {
                StreamActionButton(item)
            }
            actionButton2 = if (DownloadService.isDownloadingFile(media.download_url)) {
                CancelDownloadActionButton(item)
            } else if (!media.isDownloaded) {
                DownloadActionButton(item)
            } else {
                DeleteActionButton(item)
            }

            if (FeedItemUtil.isPlaying(media) || item.isInProgress) {
                if (lastPosition == 0) {
                    //use the most recent position; after a pause, re-reading it from the item gives a stale value
                    lastPosition = media.getPosition()
                }
                val progress: Int = (100.0 * lastPosition / media.getDuration()).toInt()
                binding.progbarPlayed.setProgress(progress)
                binding.progbarPlayed.setVisibility(View.VISIBLE)
            } else {
                binding.progbarPlayed.setVisibility(View.GONE)
            }
        }

        val downloadButton = actionButton2
        if (downloadButton != null) {
            //an accent color was set
            val tintColor = downloadButton.getDrawableTintColor(context)
            val drawable = ContextCompat.getDrawable(requireContext(), downloadButton.drawable)
            if (tintColor != -1 && drawable != null) {
                binding.downloadIcon.setImageDrawable(drawable.tint(tintColor))
            } else {
                binding.downloadIcon.setImageResource(downloadButton.drawable)
            }
            binding.downloadIcon.contentDescription = getString(downloadButton.label)

            binding.downloadLayout.visibility = downloadButton.isVisibility
        }


        val isInQueue: Boolean = item.isTagged(FeedItem.TAG_QUEUE)
        val isFavorite: Boolean = item.isTagged(FeedItem.TAG_FAVORITE)

        binding.addToQueueItem.visibility =
            if (!isInQueue && media != null)
                View.VISIBLE
            else
                View.GONE

        binding.removeFromQueueItem.visibility =
            if (isInQueue)
                View.VISIBLE
            else
                View.GONE
        binding.addToFavoritesItem.visibility =
            if (!isFavorite)
                View.VISIBLE
            else
                View.GONE
        binding.removeFromFavoritesItem.visibility =
            if (isFavorite)
                View.VISIBLE
            else
                View.GONE

        updatePlayButton()
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val binding = _binding ?: return false
        return binding.webvDescription.onContextItemSelected(item)
    }

    private fun openPodcast() {
        val item = this.item ?: return

        val fragment: Fragment = FeedItemlistFragment.newInstance(item.feedId)
        (requireActivity() as MainActivity).loadChildFragment(fragment)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent) {
        val currentId = this.item?.id ?: return
        for (item in event.items) {
            if (currentId == item.id) {
                load()
                return
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        if (FeedItemUtil.isCurrentlyPlaying(item?.getMedia())) {
            lastPosition = event.position
            _binding?.progbarPlayed?.setProgress(
                (100.0 * lastPosition / event.duration).toInt()
            )
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: DownloadEvent) {
        val update = event.update
        downloaderList = update.downloaders
        val mediaId = item?.media?.id ?: return
        if (ArrayUtils.contains(update.mediaIds, mediaId)) {
            if (itemsLoaded && activity != null) {
                updateButtons()
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusChanged(event: PlayerStatusEvent?) {
        updateButtons()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUnreadItemsChanged(event: UnreadItemsUpdateEvent?) {
        load()
    }

    private fun load() {
        disposable?.dispose()
        if (!itemsLoaded) {
            _binding?.skeletonLayout?.showSkeleton()
        }
        disposable = Observable.fromCallable { loadInBackground() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result: FeedItem? ->
                item = result
                onFragmentLoaded()
                itemsLoaded = true
            }) { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) }
    }

    private fun loadInBackground(): FeedItem? {
        val feedItem = DBReader.getFeedItem(itemId)
        val context = context
        if (feedItem != null && context != null) {
            val duration = feedItem.media?.duration ?: Int.MAX_VALUE
            DBReader.loadDescriptionOfFeedItem(feedItem)
            DBReader.loadPodcastingExtrasOfFeedItem(feedItem)
            val t = Timeline(context, feedItem.description, duration)
            webviewData = t.processShownotes()
        }
        return feedItem
    }

    companion object {
        private const val TAG = "ItemFragment"
        private const val ARG_FEEDITEM = "feeditem"

        /**
         * Creates a new instance of an ItemFragment
         *
         * @param feeditem The ID of the FeedItem to show
         * @return The ItemFragment instance
         */
        @JvmStatic
        fun newInstance(feeditem: Long): FeedItemFragment {
            val fragment = FeedItemFragment()
            val args = Bundle()
            args.putLong(ARG_FEEDITEM, feeditem)
            fragment.arguments = args
            return fragment
        }
    }
}