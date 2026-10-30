package dev.russell.yagi.activity.video

import android.util.Log
import dev.russell.yagi.core.feed.util.AdSkipUtils
import dev.russell.yagi.core.storage.DBReader
import dev.russell.yagi.model.feed.AdSegment
import dev.russell.yagi.model.feed.FeedMedia
import dev.russell.yagi.model.playback.Playable
import dev.russell.yagi.view.AdMarkerSeekBar
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

/**
 * Paints the video player's position bar: the episode's ad segments (only while ad skipping is on
 * for its podcast and only those that clear the sensitivity threshold, as in the audio player), its
 * bookmarks and its chapter starts. Loads run off the
 * main thread; [dispose] cancels them.
 */
internal class SeekBarMarkers(private val seekBar: AdMarkerSeekBar) {

    private var adSegmentsDisposable: Disposable? = null
    private var bookmarksDisposable: Disposable? = null

    /** The episode whose ad segments are painted, 0 for none. */
    var adSegmentsItemId: Long = 0
        private set

    /** The episode whose bookmarks are painted, 0 for none. */
    var bookmarksItemId: Long = 0
        private set

    fun load(media: Playable) {
        loadAdSegments(media)
        loadBookmarks(media)
        showChapters(media)
    }

    /**
     * The feed's preferences are re-read from the database because a change made elsewhere (the
     * audio-effects dialog, the feed settings) may not have touched the object this screen holds.
     */
    fun loadAdSegments(media: Playable) {
        adSegmentsDisposable?.dispose()
        val item = (media as? FeedMedia)?.item
        if (item == null) {
            adSegmentsItemId = 0
            seekBar.setAdSegments(emptyList(), 0)
            return
        }
        val itemId = item.id
        val feedId = item.feedId
        val duration = media.duration.toLong()
        adSegmentsItemId = itemId
        adSegmentsDisposable = Single.fromCallable {
            val stored = if (feedId != 0L) DBReader.getFeedPreferences(feedId) else null
            val enabled = if (stored != null) AdSkipUtils.isAdSkipEnabled(stored)
                else AdSkipUtils.isAdSkipEnabled(media)
            if (enabled) AdSkipUtils.segmentsMeetingThreshold(DBReader.loadAdSegmentsOfFeedItem(itemId))
            else emptyList<AdSegment>()
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ segments -> seekBar.setAdSegments(segments, duration) },
                { error -> Log.e(TAG, "could not load the ad segments of item $itemId", error) })
    }

    fun loadBookmarks(media: Playable) {
        bookmarksDisposable?.dispose()
        val itemId = (media as? FeedMedia)?.itemId ?: 0
        if (itemId <= 0) {
            bookmarksItemId = 0
            seekBar.setBookmarks(emptyList(), 0)
            return
        }
        val duration = media.duration.toLong()
        bookmarksItemId = itemId
        bookmarksDisposable = Single.fromCallable {
            DBReader.loadBookmarksOfFeedItem(itemId).map { it.positionMs }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ positions -> seekBar.setBookmarks(positions, duration) },
                { error -> Log.e(TAG, "could not load the bookmarks of item $itemId", error) })
    }

    /** The chapters must already be loaded on [media]; without them no notches are drawn. */
    fun showChapters(media: Playable) {
        val starts = media.chapters?.map { it.start } ?: emptyList()
        seekBar.setChapters(starts, media.duration.toLong())
    }

    fun dispose() {
        adSegmentsDisposable?.dispose()
        bookmarksDisposable?.dispose()
    }

    private companion object {
        const val TAG = "SeekBarMarkers"
    }
}
