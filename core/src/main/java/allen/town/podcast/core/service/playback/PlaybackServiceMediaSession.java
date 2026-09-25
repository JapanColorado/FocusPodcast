package allen.town.podcast.core.service.playback;

import android.app.PendingIntent;
import android.app.UiModeManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

import allen.town.podcast.core.R;
import allen.town.podcast.core.pref.Prefs;
import allen.town.podcast.core.receiver.MediaButtonReceiver;
import allen.town.podcast.core.storage.DBReader;
import allen.town.podcast.core.storage.FeedSearcher;
import allen.town.podcast.model.feed.FeedItem;
import allen.town.podcast.model.feed.FeedMedia;
import allen.town.podcast.model.playback.Playable;
import allen.town.podcast.playback.base.PlayerStatus;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * Owns the {@link MediaSessionCompat} of {@link PlaybackService}. That means its lifecycle
 * (creating it, re-activating an existing one and releasing it), the transport control callback
 * that hardware media buttons, the lock screen, Android Auto and Android Wear dispatch into, and
 * everything the service publishes through the session: the {@link PlaybackStateCompat} with its
 * capabilities and car/watch custom actions, the {@link MediaMetadataCompat} of the currently
 * playing episode and the queue. The service keeps every public entry point and simply delegates
 * to this class, which calls back into the service for playback operations.
 */
class PlaybackServiceMediaSession {
    private static final String TAG = "PlaybackService";

    /**
     * Custom action used by Android Wear, Android Auto
     */
    static final String CUSTOM_ACTION_FAST_FORWARD = "action.allen.town.podcast.core.service.fastForward";
    static final String CUSTOM_ACTION_REWIND = "action.allen.town.podcast.core.service.rewind";

    private final PlaybackService service;

    /**
     * Used for Lollipop notifications, Android Wear, and Android Auto.
     */
    private MediaSessionCompat mediaSession;

    PlaybackServiceMediaSession(PlaybackService service) {
        this.service = service;
    }

    MediaSessionCompat.Token getSessionToken() {
        return mediaSession.getSessionToken();
    }

    void recreateIfNeeded() {
        if (mediaSession != null) {
            // Media session was not destroyed, so we can re-use it.
            if (!mediaSession.isActive()) {
                mediaSession.setActive(true);
            }
            return;
        }
        ComponentName eventReceiver = new ComponentName(service.getApplicationContext(), MediaButtonReceiver.class);
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setComponent(eventReceiver);
        PendingIntent buttonReceiverIntent = PendingIntent.getBroadcast(service, 0, mediaButtonIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));

        mediaSession = new MediaSessionCompat(service.getApplicationContext(), TAG,
                eventReceiver, buttonReceiverIntent);
        service.setSessionToken(mediaSession.getSessionToken());

        try {
            mediaSession.setCallback(sessionCallback);
            mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                    | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        } catch (NullPointerException npe) {
            // on some devices (Huawei) setting active can cause a NullPointerException
            // even with correct use of the api.
            // See http://stackoverflow.com/questions/31556679/android-huawei-mediassessioncompat
            // and https://plus.google.com/+IanLake/posts/YgdTkKFxz7d
            Log.e(TAG, "NullPointerException while setting up MediaSession", npe);
        }

        service.recreateMediaPlayer();
        mediaSession.setActive(true);
    }

    void release() {
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
    }

    void loadQueue() {
        service.addServiceDisposable(Single.<List<MediaSessionCompat.QueueItem>>create(emitter -> {
            List<MediaSessionCompat.QueueItem> queueItems = new ArrayList<>();
            for (FeedItem feedItem : DBReader.getQueue()) {
                if (feedItem.getMedia() != null) {
                    MediaDescriptionCompat mediaDescription = feedItem.getMedia().getMediaItem().getDescription();
                    queueItems.add(new MediaSessionCompat.QueueItem(mediaDescription, feedItem.getId()));
                }
            }
            emitter.onSuccess(queueItems);
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(queueItems -> mediaSession.setQueue(queueItems),
                        error -> Log.e(TAG, "Failed to load the media session queue", error)));
    }

    /**
     * Updates the Media Session for the corresponding status.
     *
     * @param playerStatus the current {@link PlayerStatus}
     */
    void updatePlaybackState(final PlayerStatus playerStatus) {
        PlaybackStateCompat.Builder sessionState = new PlaybackStateCompat.Builder();

        int state;
        if (playerStatus != null) {
            switch (playerStatus) {
                case PLAYING:
                    state = PlaybackStateCompat.STATE_PLAYING;
                    break;
                case PREPARED:
                case PAUSED:
                    state = PlaybackStateCompat.STATE_PAUSED;
                    break;
                case STOPPED:
                    state = PlaybackStateCompat.STATE_STOPPED;
                    break;
                case SEEKING:
                    state = PlaybackStateCompat.STATE_FAST_FORWARDING;
                    break;
                case PREPARING:
                case INITIALIZING:
                    state = PlaybackStateCompat.STATE_CONNECTING;
                    break;
                case ERROR:
                    state = PlaybackStateCompat.STATE_ERROR;
                    break;
                case INITIALIZED: // Deliberate fall-through
                case INDETERMINATE:
                default:
                    state = PlaybackStateCompat.STATE_NONE;
                    break;
            }
        } else {
            state = PlaybackStateCompat.STATE_NONE;
        }
        sessionState.setState(state, service.getCurrentPosition(), service.getCurrentPlaybackSpeed());
        long capabilities = PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_REWIND
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_FAST_FORWARD
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SEEK_TO
                | PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED;

        if (useSkipToPreviousForRewindInLockscreen()) {
            // Workaround to fool Android so that Lockscreen will expose a skip-to-previous button,
            // which will be used for rewind.
            // The workaround is used for pre Lollipop (Androidv5) devices.
            // For Androidv5+, lockscreen widges are really notifications (compact),
            // with an independent codepath
            //
            // @see #sessionCallback in the backing callback, skipToPrevious implementation
            //   is actually the same as rewind. So no new inconsistency is created.
            // @see PlaybackServiceNotificationUpdater#setupNotification(Playable) for the method
            //   to create Androidv5+ lockscreen UI with notification (compact)
            capabilities = capabilities | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        }

        UiModeManager uiModeManager = (UiModeManager) service.getApplicationContext()
                .getSystemService(Context.UI_MODE_SERVICE);
        if (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_CAR) {
            sessionState.addCustomAction(
                new PlaybackStateCompat.CustomAction.Builder(
                        CUSTOM_ACTION_REWIND,
                        service.getString(R.string.rewind_label), R.drawable.ic_notification_fast_rewind)
                        .build());
            sessionState.addCustomAction(
                new PlaybackStateCompat.CustomAction.Builder(
                        CUSTOM_ACTION_FAST_FORWARD,
                        service.getString(R.string.fast_forward_label), R.drawable.ic_notification_fast_forward)
                        .build());
        } else {
            // This would give the PIP of videos a play button
            capabilities = capabilities | PlaybackStateCompat.ACTION_PLAY;
            if (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_WATCH) {
                WearMediaSession.sessionStateAddActionForWear(sessionState,
                        CUSTOM_ACTION_REWIND,
                        service.getString(R.string.rewind_label),
                        android.R.drawable.ic_media_rew);
                WearMediaSession.sessionStateAddActionForWear(sessionState,
                        CUSTOM_ACTION_FAST_FORWARD,
                        service.getString(R.string.fast_forward_label),
                        android.R.drawable.ic_media_ff);
                WearMediaSession.mediaSessionSetExtraForWear(mediaSession);
            }
        }

        sessionState.setActions(capabilities);

        mediaSession.setPlaybackState(sessionState.build());
    }

    private static boolean useSkipToPreviousForRewindInLockscreen() {
        // showRewindOnCompactNotification() corresponds to the "Set Lockscreen Buttons"
        // Settings in UI.
        // Hence, from user perspective, he/she is setting the buttons for Lockscreen
        return (Prefs.showRewindOnCompactNotification()
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP));
    }

    void updateMetadata(final Playable p) {
        if (p == null || mediaSession == null) {
            return;
        }

        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, p.getFeedTitle());
        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, p.getEpisodeTitle());
        builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, p.getFeedTitle());
        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, p.getDuration());
        builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, p.getEpisodeTitle());
        builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, p.getFeedTitle());

        if (Prefs.setLockscreenBackground() && service.notificationUpdater.isIconCached()) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, service.notificationUpdater.getCachedIcon());
        } else if (PlaybackService.isCasting() && !TextUtils.isEmpty(p.getImageLocation())) {
            // In the absence of metadata art, the controller dialog takes care of creating it.
            builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, p.getImageLocation());
        }

        if (service.stateManager.hasReceivedValidStartCommand()) {
            mediaSession.setSessionActivity(PendingIntent.getActivity(service,
                    R.id.pending_intent_player_activity,
                    PlaybackService.getPlayerActivityIntent(service), PendingIntent.FLAG_UPDATE_CURRENT
                            | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0)));
            try {
                mediaSession.setMetadata(builder.build());
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "Setting media session metadata", e);
                builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, null);
                mediaSession.setMetadata(builder.build());
            }
        }
    }

    private final MediaSessionCompat.Callback sessionCallback = new MediaSessionCompat.Callback() {

        private static final String TAG = "MediaSessionCompat";

        @Override
        public void onPlay() {
            Log.d(TAG, "onPlay()");
            PlayerStatus status = service.getStatus();
            if (status == PlayerStatus.PAUSED || status == PlayerStatus.PREPARED) {
                service.resume();
            } else if (status == PlayerStatus.INITIALIZED) {
                service.setStartWhenPrepared(true);
                service.prepare();
            }
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            Log.d(TAG, "onPlayFromMediaId: mediaId: " + mediaId + " extras: " + extras.toString());
            FeedMedia p = DBReader.getFeedMedia(Long.parseLong(mediaId));
            if (p != null) {
                service.startPlaying(p, false);
            }
        }

        @Override
        public void onPlayFromSearch(String query, Bundle extras) {
            Log.d(TAG, "onPlayFromSearch  query=" + query + " extras=" + extras.toString());

            if (query.equals("")) {
                Log.d(TAG, "onPlayFromSearch called with empty query, resuming from the last position");
                service.startPlayingFromPreferences();
                return;
            }

            List<FeedItem> results = FeedSearcher.searchFeedItems(query, 0);
            if (results.size() > 0 && results.get(0).getMedia() != null) {
                FeedMedia media = results.get(0).getMedia();
                service.startPlaying(media, false);
                return;
            }
            onPlay();
        }

        @Override
        public void onPause() {
            Log.d(TAG, "onPause()");
            if (service.getStatus() == PlayerStatus.PLAYING) {
                service.pause(!Prefs.isPersistNotify(), false);
            }
        }

        @Override
        public void onStop() {
            Log.d(TAG, "onStop()");
            service.mediaPlayer.stopPlayback(true);
        }

        @Override
        public void onSkipToPrevious() {
            Log.d(TAG, "onSkipToPrevious()");
            service.seekDelta(-Prefs.getRewindSecs() * 1000);
        }

        @Override
        public void onRewind() {
            Log.d(TAG, "onRewind()");
            service.seekDelta(-Prefs.getRewindSecs() * 1000);
        }

        @Override
        public void onFastForward() {
            Log.d(TAG, "onFastForward()");
            service.seekDelta(Prefs.getFastForwardSecs() * 1000);
        }

        @Override
        public void onSkipToNext() {
            Log.d(TAG, "onSkipToNext()");
            UiModeManager uiModeManager = (UiModeManager) service.getApplicationContext()
                    .getSystemService(Context.UI_MODE_SERVICE);
            if (Prefs.getHardwareForwardButton() == KeyEvent.KEYCODE_MEDIA_NEXT
                    || uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_CAR) {
                service.mediaPlayer.skip();
            } else {
                service.seekDelta(Prefs.getFastForwardSecs() * 1000);
            }
        }


        @Override
        public void onSeekTo(long pos) {
            Log.d(TAG, "onSeekTo()");
            service.seekTo((int) pos);
        }

        @Override
        public void onSetPlaybackSpeed(float speed) {
            Log.d(TAG, "onSetPlaybackSpeed()");
            service.setSpeedForCurrentMedia(speed);
        }

        @Override
        public boolean onMediaButtonEvent(final Intent mediaButton) {
            Log.d(TAG, "onMediaButtonEvent(" + mediaButton + ")");
            if (mediaButton != null) {
                KeyEvent keyEvent = mediaButton.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (keyEvent != null
                        && keyEvent.getAction() == KeyEvent.ACTION_DOWN
                        && keyEvent.getRepeatCount() == 0) {
                    return service.handleKeycode(keyEvent.getKeyCode(), false);
                }
            }
            return false;
        }

        @Override
        public void onCustomAction(String action, Bundle extra) {
            Log.d(TAG, "onCustomAction(" + action + ")");
            if (CUSTOM_ACTION_FAST_FORWARD.equals(action)) {
                onFastForward();
            } else if (CUSTOM_ACTION_REWIND.equals(action)) {
                onRewind();
            }
        }
    };
}
