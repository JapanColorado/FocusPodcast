package allen.town.podcast.util

import allen.town.podcast.actionbuttons.PlayActionButton
import allen.town.podcast.actionbuttons.PlayLocalActionButton
import allen.town.podcast.actionbuttons.StreamActionButton
import allen.town.podcast.core.pref.PlaybackPreferences
import allen.town.podcast.core.service.playback.PlaybackService
import allen.town.podcast.core.storage.DBReader
import allen.town.podcast.core.storage.DBWriter
import allen.town.podcast.core.util.playback.PlaybackController
import allen.town.podcast.model.feed.FeedItem
import allen.town.podcast.playback.base.PlayerStatus
import android.app.Activity
import android.util.Log
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

/**
 * Plays an episode from a given position: a bookmark, a soundbite. Shared by the Bookmarks screen,
 * the episode screen's highlights and the podcast preview.
 */
object PlayAtPosition {
    private const val TAG = "PlayAtPosition"

    /**
     * When [item] is the episode loaded in the player, seeks [controller] to [positionMs] and
     * resumes. Otherwise stores the position first and starts the episode the usual way (play,
     * play local or stream, with the streaming confirmation), which picks it up. [activity] is
     * asked again once the position is stored, since the screen may be gone by then.
     *
     * @return the pending store, for the caller to dispose of; null when nothing is pending
     */
    fun play(
        activity: () -> Activity?,
        controller: PlaybackController?,
        item: FeedItem,
        positionMs: Long
    ): Disposable? {
        val media = item.media ?: return null
        val position = positionMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        if (controller != null && PlaybackService.isRunning
            && PlaybackPreferences.getCurrentlyPlayingFeedMediaId() == media.id
        ) {
            controller.seekTo(position)
            if (controller.status != PlayerStatus.PLAYING) {
                controller.playPause()
            }
            return null
        }
        return Completable.fromAction {
            val stored = DBReader.getFeedMedia(media.id) ?: media
            stored.setPosition(position)
            DBWriter.setFeedMediaPlaybackInformation(stored).get()
            media.setPosition(position)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { activity()?.let { startPlayback(it, item) } },
                { error: Throwable -> Log.e(TAG, "could not store the start position", error) }
            )
    }

    private fun startPlayback(activity: Activity, item: FeedItem) {
        val media = item.media ?: return
        val button = when {
            item.feed?.isLocalFeed == true -> PlayLocalActionButton(item)
            media.isDownloaded -> PlayActionButton(item)
            else -> StreamActionButton(item)
        }
        button.onClick(activity)
    }
}
