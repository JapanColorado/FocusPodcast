package dev.russell.focuspodcast.core.service.playback;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;

import java.util.Collections;
import java.util.List;

import dev.russell.focuspodcast.core.R;
import dev.russell.focuspodcast.core.feed.util.AudiobookUtils;
import dev.russell.focuspodcast.core.feed.util.VideoModeUtils;
import dev.russell.focuspodcast.core.pref.PlaybackPreferences;
import dev.russell.focuspodcast.core.pref.Prefs;
import dev.russell.focuspodcast.core.pref.SleepTimerPreferences;
import dev.russell.focuspodcast.core.service.QuickSettingsTileService;
import dev.russell.focuspodcast.core.storage.DBReader;
import dev.russell.focuspodcast.core.storage.DBWriter;
import dev.russell.focuspodcast.core.sync.queue.SynchronizationQueueSink;
import dev.russell.focuspodcast.core.util.FeedItemUtil;
import dev.russell.focuspodcast.core.util.IntentUtils;
import dev.russell.focuspodcast.core.util.NetworkUtils;
import dev.russell.focuspodcast.core.util.playback.PlaybackServiceStarter;
import dev.russell.focuspodcast.event.MessageEvent;
import dev.russell.focuspodcast.model.feed.Feed;
import dev.russell.focuspodcast.model.feed.FeedItem;
import dev.russell.focuspodcast.model.feed.FeedMedia;
import dev.russell.focuspodcast.model.feed.FeedPreferences;
import dev.russell.focuspodcast.model.playback.MediaType;
import dev.russell.focuspodcast.model.playback.Playable;
import dev.russell.focuspodcast.playback.base.PlaybackServiceMediaPlayer;
import dev.russell.focuspodcast.playback.base.PlayerStatus;

/**
 * Everything {@link PlaybackService} does in reaction to its media player: the
 * {@link PlaybackServiceMediaPlayer.PSMPCallback} implementation that reacts to every player
 * status change (persisting the played media, updating the notification, media session, widget,
 * quick settings tile and AVRCP listeners, arming the sleep timer and the position observer),
 * picking the next episode from the queue, and the post-playback bookkeeping that marks an
 * episode played, removes it from the queue, auto-deletes it and adds it to the playback history.
 */
class PlaybackServicePlayerCallback implements PlaybackServiceMediaPlayer.PSMPCallback {
    private static final String TAG = "PlaybackService";

    private static final String AVRCP_ACTION_PLAYER_STATUS_CHANGED = "com.android.music.playstatechanged";
    private static final String AVRCP_ACTION_META_CHANGED = "com.android.music.metachanged";

    private final PlaybackService service;

    PlaybackServicePlayerCallback(PlaybackService service) {
        this.service = service;
    }

    @Override
    public void statusChanged(PlaybackServiceMediaPlayer.PSMPInfo newInfo) {
        if (service.mediaPlayer != null) {
            PlaybackService.setCurrentMediaType(service.mediaPlayer.getCurrentMediaType());
        } else {
            PlaybackService.setCurrentMediaType(MediaType.UNKNOWN);
        }

        service.mediaSessionHolder.updatePlaybackState(newInfo.playerStatus);
        switch (newInfo.playerStatus) {
            case INITIALIZED:
                if (service.mediaPlayer.getPSMPInfo().playable != null) {
                    VideoModeUtils.onMediaStarted(service.mediaPlayer.getPSMPInfo().playable);
                    PlaybackPreferences.writeMediaPlaying(service.mediaPlayer.getPSMPInfo().playable,
                            service.mediaPlayer.getPSMPInfo().playerStatus);
                }
                service.updateNotificationAndMediaSession(newInfo.playable);
                break;
            case PREPARED:
                if (service.mediaPlayer.getPSMPInfo().playable != null) {
                    // Car mode skips INITIALIZED, so the choice is pinned here as well.
                    VideoModeUtils.onMediaStarted(service.mediaPlayer.getPSMPInfo().playable);
                    PlaybackPreferences.writeMediaPlaying(service.mediaPlayer.getPSMPInfo().playable,
                            service.mediaPlayer.getPSMPInfo().playerStatus);
                }
                service.taskManager.startChapterLoader(newInfo.playable);
                break;
            case PAUSED:
                service.updateNotificationAndMediaSession(newInfo.playable);
                if (!PlaybackService.isCasting()) {
                    service.stateManager.stopForeground(!Prefs.isPersistNotify());
                }
                service.cancelPositionObserver();
                PlaybackPreferences.writePlayerStatus(service.mediaPlayer.getPlayerStatus());
                break;
            case STOPPED:
                break;
            case PLAYING:
                PlaybackPreferences.writePlayerStatus(service.mediaPlayer.getPlayerStatus());
                service.saveCurrentPosition(true, null, Playable.INVALID_TIME);
                service.recreateMediaSessionIfNeeded();
                service.updateNotificationAndMediaSession(newInfo.playable);
                service.setupPositionObserver();
                service.boundarySleepTimer.onPlaybackStartedOrMediaChanged();
                service.stateManager.validStartCommandWasReceived();
                service.stateManager.startForeground(R.id.notification_playing,
                        service.notificationUpdater.build());
                // Auto-enable arms the sleep timer only when an episode starts (PREPARED -> PLAYING).
                // Resuming from pause or returning from a seek must not re-arm it: otherwise a timer
                // the user just disabled or undid comes back on the next unpause.
                if (newInfo.oldPlayerStatus == PlayerStatus.PREPARED
                        && SleepTimerPreferences.autoEnable() && !service.sleepTimerActive()) {
                    service.setSleepTimer(SleepTimerPreferences.timerMillis());
                    EventBus.getDefault().post(new MessageEvent(
                            service.getString(R.string.sleep_timer_enabled_label),
                            service::disableSleepTimer));
                }
                service.mediaSessionHolder.loadQueue();
                break;
            case ERROR:
                PlaybackPreferences.writeNoMediaPlaying();
                service.stateManager.stopService();
                break;
            default:
                break;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            TileService.requestListeningState(service.getApplicationContext(),
                    new ComponentName(service.getApplicationContext(), QuickSettingsTileService.class));
        }

        IntentUtils.sendLocalBroadcast(service.getApplicationContext(),
                PlaybackService.ACTION_PLAYER_STATUS_CHANGED);
        bluetoothNotifyChange(newInfo, AVRCP_ACTION_PLAYER_STATUS_CHANGED);
        bluetoothNotifyChange(newInfo, AVRCP_ACTION_META_CHANGED);
        service.taskManager.requestWidgetUpdate();
    }

    @Override
    public void shouldStop() {
        service.stateManager.stopForeground(!Prefs.isPersistNotify());
    }

    @Override
    public void onMediaChanged(boolean reloadUI) {
        Log.d(TAG, "reloadUI callback reached");
        service.adSkipper.onMediaChanged(service.getPlayable());
        service.boundarySleepTimer.onPlaybackStartedOrMediaChanged();
        if (reloadUI) {
            service.sendNotificationBroadcast(PlaybackService.NOTIFICATION_TYPE_RELOAD, 0);
        }
        service.updateNotificationAndMediaSession(service.getPlayable());
    }

    @Override
    public void onPlaybackStart(@NonNull Playable playable, int position) {
        service.taskManager.startWidgetUpdater();
        service.adSkipper.onMediaChanged(playable);
        if (position != PlaybackServiceMediaPlayer.INVALID_TIME) {
            playable.setPosition(position);
        } else {
            service.autoSkipper.skipIntro(playable);
        }
        playable.onPlaybackStart();
        service.taskManager.startPositionSaver();
    }

    @Override
    public void onPlaybackPause(Playable playable, int position) {
        service.taskManager.cancelPositionSaver();
        service.cancelPositionObserver();
        service.saveCurrentPosition(position == PlaybackServiceMediaPlayer.INVALID_TIME || playable == null,
                playable, position);
        service.taskManager.cancelWidgetUpdater();
        if (playable != null) {
            if (playable instanceof FeedMedia) {
                SynchronizationQueueSink.enqueueEpisodePlayedIfSynchronizationIsActive(
                        service.getApplicationContext(), (FeedMedia) playable, false);
            }
            playable.onPlaybackPause(service.getApplicationContext());
        }
    }

    @Nullable
    @Override
    public Playable findMedia(@NonNull String url) {
        FeedItem item = DBReader.getFeedItemByGuidOrEpisodeUrl(null, url);
        return item != null ? item.getMedia() : null;
    }

    @Override
    public void ensureMediaInfoLoaded(@NonNull Playable media) {
        if (media instanceof FeedMedia && ((FeedMedia) media).getItem() == null) {
            ((FeedMedia) media).setItem(DBReader.getFeedItem(((FeedMedia) media).getItemId()));
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * A running "end of episode" or "end of chapter" sleep timer is used up when an episode ends
     * (played to the end, or skipped automatically at its configured ending or a closing ad):
     * the episode has already gone through the normal completion path (so it is marked played,
     * leaves the queue and is auto-deleted as usual by post-playback), and the next episode is
     * then only remembered as the current one, exactly as when "follow queue" is off, instead of
     * being started.
     */
    @Override
    public Playable getNextInQueue(final Playable currentMedia, boolean completed) {
        boolean stopForSleepTimer = (completed || wasAutoSkipped(currentMedia))
                && service.boundarySleepTimer.consumeAtEpisodeEnd();
        if (!(currentMedia instanceof FeedMedia)) {
            Log.d(TAG, "getNextInQueue(), but playable not an instance of FeedMedia, so not proceeding");
            PlaybackPreferences.writeNoMediaPlaying();
            return null;
        }
        Log.d(TAG, "getNextInQueue()");
        FeedMedia media = (FeedMedia) currentMedia;
        if (media.getItem() == null) {
            media.setItem(DBReader.getFeedItem(media.getItemId()));
        }
        FeedItem item = media.getItem();
        if (item == null) {
            Log.w(TAG, "getNextInQueue() with FeedMedia object whose FeedItem is null");
            PlaybackPreferences.writeNoMediaPlaying();
            return null;
        }
        if (item.getFeed() != null && item.getFeed().isAudiobook()) {
            return getNextAudiobookTrack(item, item.getFeed(), stopForSleepTimer);
        }
        FeedItem nextItem;
        nextItem = DBReader.getNextInQueue(item);

        if (nextItem == null || nextItem.getMedia() == null) {
            PlaybackPreferences.writeNoMediaPlaying();
            return null;
        }

        if (!Prefs.isFollowQueue() || stopForSleepTimer) {
            Log.d(TAG, "getNextInQueue(), but follow queue is not enabled or the sleep timer stops here.");
            return parkNext(nextItem.getMedia());
        }

        if (!nextItem.getMedia().localFileAvailable() && !NetworkUtils.isStreamingAllowed()
                && Prefs.isFollowQueue() && !nextItem.getFeed().isLocalFeed()) {
            service.notificationUpdater.displayStreamingNotAllowedNotification(
                    new PlaybackServiceStarter(service, nextItem.getMedia())
                            .getIntent());
            PlaybackPreferences.writeNoMediaPlaying();
            service.stateManager.stopService();
            return null;
        }
        return nextItem.getMedia();
    }

    /**
     * The track of the same audiobook that follows {@code item} in the book's order
     * ({@link AudiobookUtils#playbackOrder}), whatever the queue holds and whether or not "follow
     * queue" is on. At the end of the book playback stops instead of falling through to the queue.
     */
    @Nullable
    private Playable getNextAudiobookTrack(@NonNull FeedItem item, @NonNull Feed feed,
                                           boolean stopForSleepTimer) {
        List<FeedItem> tracks = DBReader.getFeedItemList(feed);
        FeedItem nextItem = AudiobookUtils.nextTrack(tracks, AudiobookUtils.playbackOrder(feed), item.getId());
        if (nextItem == null || nextItem.getMedia() == null) {
            Log.d(TAG, "getNextInQueue(): end of the audiobook");
            PlaybackPreferences.writeNoMediaPlaying();
            return null;
        }
        // queue and favourite tags, so that post-playback treats the track like any other
        DBReader.loadAdditionalFeedItemListData(Collections.singletonList(nextItem));
        if (stopForSleepTimer) {
            Log.d(TAG, "getNextInQueue(): the sleep timer stops before the next track");
            return parkNext(nextItem.getMedia());
        }
        if (!nextItem.getMedia().localFileAvailable() && !NetworkUtils.isStreamingAllowed()
                && !feed.isLocalFeed()) {
            service.notificationUpdater.displayStreamingNotAllowedNotification(
                    new PlaybackServiceStarter(service, nextItem.getMedia()).getIntent());
            PlaybackPreferences.writeNoMediaPlaying();
            service.stateManager.stopService();
            return null;
        }
        return nextItem.getMedia();
    }

    /**
     * Remembers {@code next} as the media to play, without playing it, and returns null so that
     * the player stops.
     */
    @Nullable
    private Playable parkNext(@NonNull Playable next) {
        PlaybackPreferences.writeMediaPlaying(next, PlayerStatus.STOPPED);
        service.updateNotificationAndMediaSession(next);
        return null;
    }

    /** True if the app (not the user) skipped {@code media}, at its ending or a closing ad. */
    private boolean wasAutoSkipped(Playable media) {
        return media instanceof FeedMedia && ((FeedMedia) media).getItem() != null
                && service.autoSkipper.isAutoSkipped(((FeedMedia) media).getItem());
    }

    /**
     * Set of instructions to be performed when playback ends.
     */
    @Override
    public void onPlaybackEnded(MediaType mediaType, boolean stopPlaying) {
        Log.d(TAG, "playback end");
        if (stopPlaying) {
            service.taskManager.cancelPositionSaver();
            service.cancelPositionObserver();
            if (!PlaybackService.isCasting()) {
                service.stateManager.stopForeground(true);
                service.stateManager.stopService();
            }
        }
        if (mediaType == null) {
            service.sendNotificationBroadcast(PlaybackService.NOTIFICATION_TYPE_PLAYBACK_END, 0);
        } else {
            service.sendNotificationBroadcast(PlaybackService.NOTIFICATION_TYPE_RELOAD,
                    PlaybackService.isCasting() ? PlaybackService.EXTRA_CODE_CAST
                            : (mediaType == MediaType.VIDEO) ? PlaybackService.EXTRA_CODE_VIDEO
                                    : PlaybackService.EXTRA_CODE_AUDIO);
        }
    }

    /**
     * This method processes the media object after its playback ended, either because it completed
     * or because a different media object was selected for playback.
     * <p>
     * Even though these tasks aren't supposed to be resource intensive, a good practice is to
     * usually call this method on a background thread.
     *
     * @param playable    the media object that was playing. It is assumed that its position
     *                    property was updated before this method was called.
     * @param ended       if true, it signals that {@param playable} was played until its end.
     *                    In such case, the position property of the media becomes irrelevant for
     *                    most of the tasks (although it's still a good practice to keep it
     *                    accurate).
     * @param skipped     if the user pressed a skip &gt;| button.
     * @param playingNext if true, it means another media object is being loaded in place of this
     *                    one.
     *                    Instances when we'd set it to false would be when we're not following the
     *                    queue or when the queue has ended.
     */
    @Override
    public void onPostPlayback(@NonNull Playable media, boolean ended, boolean skipped,
                               boolean playingNext) {
        postPlayback(media, ended, skipped, playingNext);
    }

    private void postPlayback(final Playable playable, boolean ended, boolean skipped,
                              boolean playingNext) {
        if (playable == null) {
            Log.e(TAG, "Cannot do post-playback processing: media was null");
            return;
        }
        Log.d(TAG, "onPostPlayback(): media=" + playable.getEpisodeTitle());

        if (!(playable instanceof FeedMedia)) {
            Log.d(TAG, "Not doing post-playback processing: media not of type FeedMedia");
            if (ended) {
                playable.onPlaybackCompleted(service.getApplicationContext());
            } else {
                playable.onPlaybackPause(service.getApplicationContext());
            }
            return;
        }
        FeedMedia media = (FeedMedia) playable;
        FeedItem item = media.getItem();
        boolean smartMarkAsPlayed = FeedItemUtil.hasAlmostEnded(media);
        if (!ended && smartMarkAsPlayed) {
            Log.d(TAG, "smart mark as played");
        }

        boolean autoSkipped = service.autoSkipper.consumeAutoSkipped(item);

        if (ended || smartMarkAsPlayed) {
            SynchronizationQueueSink.enqueueEpisodePlayedIfSynchronizationIsActive(
                    service.getApplicationContext(), media, true);
            media.onPlaybackCompleted(service.getApplicationContext());
        } else {
            SynchronizationQueueSink.enqueueEpisodePlayedIfSynchronizationIsActive(
                    service.getApplicationContext(), media, false);
            media.onPlaybackPause(service.getApplicationContext());
        }

        if (item != null) {
            if (ended || smartMarkAsPlayed
                    || autoSkipped
                    || (skipped && !Prefs.shouldSkipKeepEpisode())) {
                // only mark the item as played if we're not keeping it anyways
                DBWriter.markItemPlayed(item, FeedItem.PLAYED, ended || (skipped && smartMarkAsPlayed));
                // don't know if it actually matters to not autodownload when smart mark as played is triggered
                DBWriter.removeQueueItem(service, ended, item);
                // Delete episode if enabled
                FeedPreferences.AutoDeleteAction action =
                        item.getFeed().getPreferences().getCurrentAutoDelete();
                // Never for local files (deleting one would only drop its content URI from the
                // row and make the track unplayable) nor for audiobooks, which are re-listened to.
                boolean keepFile = item.getFeed().isLocalFeed() || item.getFeed().isAudiobook();
                boolean shouldAutoDelete = !keepFile && (action == FeedPreferences.AutoDeleteAction.YES
                        || (action == FeedPreferences.AutoDeleteAction.GLOBAL && Prefs.isAutoDelete()));
                if (shouldAutoDelete && (!item.isTagged(FeedItem.TAG_FAVORITE)
                        || !Prefs.shouldFavoriteKeepEpisode())) {
                    DBWriter.deleteFeedMediaOfItem(service, media.getId());
                    Log.d(TAG, "Episode Deleted");
                }
            }
        }

        if (ended || skipped || playingNext) {
            DBWriter.addItemToPlaybackHistory(media);
        }
    }

    private void bluetoothNotifyChange(PlaybackServiceMediaPlayer.PSMPInfo info, String whatChanged) {
        boolean isPlaying = false;

        if (info.playerStatus == PlayerStatus.PLAYING) {
            isPlaying = true;
        }

        if (info.playable != null) {
            Intent i = new Intent(whatChanged);
            i.putExtra("id", 1L);
            i.putExtra("artist", "");
            i.putExtra("album", info.playable.getFeedTitle());
            i.putExtra("track", info.playable.getEpisodeTitle());
            i.putExtra("playing", isPlaying);
            i.putExtra("duration", (long) info.playable.getDuration());
            i.putExtra("position", (long) info.playable.getPosition());
            service.sendBroadcast(i);
        }
    }
}
