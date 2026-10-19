package dev.russell.focuspodcast.util

import dev.russell.focuspodcast.actionbuttons.PlayActionButton
import dev.russell.focuspodcast.actionbuttons.PlayLocalActionButton
import dev.russell.focuspodcast.actionbuttons.StreamActionButton
import dev.russell.focuspodcast.core.pref.PlaybackPreferences
import dev.russell.focuspodcast.core.service.playback.PlaybackService
import dev.russell.focuspodcast.core.storage.DBReader
import dev.russell.focuspodcast.core.storage.DBWriter
import dev.russell.focuspodcast.core.util.BookmarkUtils
import dev.russell.focuspodcast.core.util.playback.PlaybackController
import dev.russell.focuspodcast.model.feed.FeedItem
import dev.russell.focuspodcast.playback.base.PlayerStatus
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
            // Resume before seeking, as the chapter list does: a resume that arrives while the
            // seek is still running is dropped, which left playback paused at the new position.
            if (controller.status != PlayerStatus.PLAYING) {
                controller.playPause()
            }
            controller.seekTo(position)
            return null
        }
        return Completable.fromAction {
            // A last-played time of now keeps the player's rewind-after-pause from moving the
            // start back before the requested position.
            val now = System.currentTimeMillis()
            val stored = DBReader.getFeedMedia(media.id) ?: media
            BookmarkUtils.prepareStartAt(stored, position, now)
            DBWriter.setFeedMediaPlaybackInformation(stored).get()
            BookmarkUtils.prepareStartAt(media, position, now)
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
