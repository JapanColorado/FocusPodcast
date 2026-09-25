package allen.town.podcast.core.service.playback;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.media.MediaBrowserCompat;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.webkit.URLUtil;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.media.MediaBrowserServiceCompat;
import androidx.preference.PreferenceManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import allen.town.podcast.common.util.TopSnackbarUtil;
import allen.town.podcast.core.R;
import allen.town.podcast.core.feed.util.AudioEffectUtils;
import allen.town.podcast.core.feed.util.PlayableFeedPreferences;
import allen.town.podcast.core.feed.util.PlaybackSpeedUtils;
import allen.town.podcast.core.pref.PlaybackPreferences;
import allen.town.podcast.core.pref.Prefs;
import allen.town.podcast.core.receiver.MediaButtonReceiver;
import allen.town.podcast.core.storage.DBReader;
import allen.town.podcast.core.storage.DBWriter;
import allen.town.podcast.core.util.NetworkUtils;
import allen.town.podcast.core.util.playback.PlayableUtils;
import allen.town.podcast.core.util.playback.PlaybackServiceStarter;
import allen.town.podcast.core.widget.WidgetUpdater;
import allen.town.podcast.event.PlayerErrorEvent;
import allen.town.podcast.event.adskip.AdSegmentsChangedEvent;
import allen.town.podcast.event.adskip.AdSkipUndoEvent;
import allen.town.podcast.event.playback.BufferUpdateEvent;
import allen.town.podcast.event.playback.PlaybackPositionEvent;
import allen.town.podcast.event.playback.PlaybackServiceEvent;
import allen.town.podcast.event.playback.SleepTimerUpdatedEvent;
import allen.town.podcast.event.settings.AdSkipChangedEvent;
import allen.town.podcast.event.settings.AudioEffectsChangedEvent;
import allen.town.podcast.event.settings.SkipIntroEndingChangedEvent;
import allen.town.podcast.event.settings.SpeedPresetChangedEvent;
import allen.town.podcast.event.settings.VolumeAdaptionChangedEvent;
import allen.town.podcast.model.feed.FeedMedia;
import allen.town.podcast.model.feed.FeedPreferences;
import allen.town.podcast.model.playback.MediaType;
import allen.town.podcast.model.playback.Playable;
import allen.town.podcast.playback.base.PlaybackServiceMediaPlayer;
import allen.town.podcast.playback.base.PlayerStatus;
import allen.town.podcast.ui.startintent.MainActivityStarter;
import allen.town.podcast.ui.startintent.VideoPlayerActivityStarter;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Controls the MediaPlayer that plays a FeedMedia-file
 */
public class PlaybackService extends MediaBrowserServiceCompat {
    /**
     * Logging tag
     */
    private static final String TAG = "PlaybackService";

    public static final String EXTRA_PLAYABLE = "PlaybackService.PlayableExtra";
    public static final String EXTRA_ALLOW_STREAM_THIS_TIME = "extra.allen.town.podcast.core.service.allowStream";
    public static final String EXTRA_ALLOW_STREAM_ALWAYS = "extra.allen.town.podcast.core.service.allowStreamAlways";

    public static final String ACTION_PLAYER_STATUS_CHANGED = "action.allen.town.podcast.core.service.playerStatusChanged";

    public static final String ACTION_PLAYER_NOTIFICATION = "action.allen.town.podcast.core.service.playerNotification";
    public static final String EXTRA_NOTIFICATION_CODE = "extra.allen.town.podcast.core.service.notificationCode";
    public static final String EXTRA_NOTIFICATION_TYPE = "extra.allen.town.podcast.core.service.notificationType";

    /**
     * If the PlaybackService receives this action, it will stop playback and
     * try to shutdown.
     */
    public static final String ACTION_SHUTDOWN_PLAYBACK_SERVICE = "action.allen.town.podcast.core.service.actionShutdownPlaybackService";

    /**
     * If the PlaybackService receives this action, it will end playback of the
     * current episode and load the next episode if there is one available.
     */
    public static final String ACTION_SKIP_CURRENT_EPISODE = "action.allen.town.podcast.core.service.skipCurrentEpisode";

    /**
     * If the PlaybackService receives this action, it will pause playback.
     */
    public static final String ACTION_PAUSE_PLAY_CURRENT_EPISODE = "action.allen.town.podcast.core.service.pausePlayCurrentEpisode";

    /**
     * Used in NOTIFICATION_TYPE_RELOAD.
     */
    public static final int EXTRA_CODE_AUDIO = 1;
    public static final int EXTRA_CODE_VIDEO = 2;
    public static final int EXTRA_CODE_CAST = 3;

    /**
     * Receivers of this intent should update their information about the curently playing media
     */
    public static final int NOTIFICATION_TYPE_RELOAD = 3;

    /**
     * Set a max number of episodes to load for Android Auto, otherwise there could be performance issues
     */
    public static final int MAX_ANDROID_AUTO_EPISODES_PER_FEED = 100;

    /**
     * No more episodes are going to be played.
     */
    public static final int NOTIFICATION_TYPE_PLAYBACK_END = 7;

    /**
     * Returned by getPositionSafe() or getDurationSafe() if the playbackService
     * is in an invalid state.
     */
    public static final int INVALID_TIME = -1;

    /**
     * Is true if service is running.
     */
    public static boolean isRunning = false;
    /**
     * Is true if a Cast Device is connected to the service.
     */
    private static volatile boolean isCasting = false;

    PlaybackServiceMediaPlayer mediaPlayer;
    PlaybackServiceTaskManager taskManager;
    PlaybackServiceStateManager stateManager;
    private Disposable positionEventTimer;
    /**
     * Chains started by this service that must not outlive it. Cleared (not disposed) in
     * {@link #onDestroy()} so the same instance can be reused if the service is recreated.
     */
    private final CompositeDisposable serviceDisposables = new CompositeDisposable();

    /** Owns the playback notification and the "streaming not allowed" notification. */
    PlaybackServiceNotificationUpdater notificationUpdater;
    /** Owns the MediaSession, its transport callback and everything published through it. */
    final PlaybackServiceMediaSession mediaSessionHolder = new PlaybackServiceMediaSession(this);
    /** Applies the per-feed skip-intro / skip-ending preferences. */
    final PlaybackServiceAutoSkipper autoSkipper = new PlaybackServiceAutoSkipper(this);
    /** Skips the stored ad segments of the episode being played. */
    final PlaybackServiceAdSkipper adSkipper = new PlaybackServiceAdSkipper(this);
    /** Owns the headset / bluetooth / shutdown / skip broadcast receivers. */
    private final PlaybackServiceReceivers receivers = new PlaybackServiceReceivers(this);
    /** Answers the Android Auto media browser tree. */
    private final PlaybackServiceMediaBrowser mediaBrowser = new PlaybackServiceMediaBrowser(this);
    /** Reacts to every media player status change and does the post-playback bookkeeping. */
    private final PlaybackServicePlayerCallback mediaPlayerCallback = new PlaybackServicePlayerCallback(this);

    private static volatile MediaType currentMediaType = MediaType.UNKNOWN;

    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public PlaybackService getService() {
            return PlaybackService.this;
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        return super.onUnbind(intent);
    }

    /**
     * Returns an intent which starts an audio- or videoplayer, depending on the
     * type of media that is being played. If the playbackservice is not
     * running, the type of the last played media will be looked up.
     */
    public static Intent getPlayerActivityIntent(Context context) {
        boolean showVideoPlayer;

        if (isRunning) {
            showVideoPlayer = currentMediaType == MediaType.VIDEO && !isCasting;
        } else {
            showVideoPlayer = PlaybackPreferences.getCurrentEpisodeIsVideo();
        }

        if (showVideoPlayer) {
            return new VideoPlayerActivityStarter(context).getIntent();
        } else {
            return new MainActivityStarter(context).withOpenPlayer().getIntent();
        }
    }

    /**
     * Same as {@link #getPlayerActivityIntent(Context)}, but here the type of activity
     * depends on the FeedMedia that is provided as an argument.
     */
    public static Intent getPlayerActivityIntent(Context context, Playable media) {
        if (media.getMediaType() == MediaType.VIDEO && !isCasting) {
            return new VideoPlayerActivityStarter(context).getIntent();
        } else {
            return new MainActivityStarter(context).withOpenPlayer().getIntent();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        isRunning = true;

        stateManager = new PlaybackServiceStateManager(this);
        notificationUpdater = new PlaybackServiceNotificationUpdater(this);

        receivers.register();
        EventBus.getDefault().register(this);
        taskManager = new PlaybackServiceTaskManager(this, taskManagerCallback);

        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(prefListener);
        recreateMediaSessionIfNeeded();
        EventBus.getDefault().post(new PlaybackServiceEvent(PlaybackServiceEvent.Action.SERVICE_STARTED));
    }

    void recreateMediaSessionIfNeeded() {
        mediaSessionHolder.recreateIfNeeded();
    }

    void recreateMediaPlayer() {
        Playable media = null;
        boolean wasPlaying = false;
        if (mediaPlayer != null) {
            media = mediaPlayer.getPlayable();
            wasPlaying = mediaPlayer.getPlayerStatus() == PlayerStatus.PLAYING;
            mediaPlayer.pause(true, false);
            mediaPlayer.shutdown();
        }
        mediaPlayer = new LocalPSMP(this, mediaPlayerCallback);
        if (media != null) {
            mediaPlayer.playMediaObject(media, !media.localFileAvailable(), wasPlaying, true);
        }
        isCasting = mediaPlayer.isCasting();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        notificationUpdater.notifyStoppedIfPlaying();
        stateManager.stopForeground(!Prefs.isPersistNotify());
        isRunning = false;
        currentMediaType = MediaType.UNKNOWN;
        notificationUpdater.dispose();
        // Nothing started by this service may deliver a result after it is gone.
        serviceDisposables.clear();

        cancelPositionObserver();
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(prefListener);
        mediaSessionHolder.release();
        receivers.unregister();
        if (mediaPlayer != null) {
            mediaPlayer.shutdown();
        }
        if (taskManager != null) {
            taskManager.shutdown();
        }
        EventBus.getDefault().unregister(this);
    }

    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, Bundle rootHints) {
        Log.d(TAG, "OnGetRoot clientPackageName " + clientPackageName
                + "; clientUid=" + clientUid + " ; rootHints " + rootHints);
        return new BrowserRoot(
                getResources().getString(R.string.app_name), // Name visible in Android Auto
                null); // Bundle of optional extras
    }

    @Override
    public void onLoadChildren(@NonNull String parentId,
                               @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        Log.d(TAG, "OnLoadChildren parentMediaId " + parentId);
        mediaBrowser.onLoadChildren(parentId, result);
    }

    /**
     * Registers a chain started by this service so that it cannot outlive it.
     */
    void addServiceDisposable(Disposable disposable) {
        serviceDisposables.add(disposable);
    }

    static void setCurrentMediaType(MediaType mediaType) {
        currentMediaType = mediaType;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        if (intent.getAction() != null && TextUtils.equals(intent.getAction(), MediaBrowserServiceCompat.SERVICE_INTERFACE)) {
            return super.onBind(intent);
        } else {
            return mBinder;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "onStartCommand");

        try {
            stateManager.startForeground(R.id.notification_playing, notificationUpdater.build());
        } catch (Exception e) {
            // Android 12+: a stale media button / widget start without a background exemption
            // throws ForegroundServiceStartNotAllowedException; do not take the process down.
            Log.e(TAG, "startForeground failed", e);
            stopSelf();
            return Service.START_NOT_STICKY;
        }
        notificationUpdater.cancelStreamingConfirmation();

        final int keycode = intent.getIntExtra(MediaButtonReceiver.EXTRA_KEYCODE, -1);
        final boolean hardwareButton = intent.getBooleanExtra(MediaButtonReceiver.EXTRA_HARDWAREBUTTON, false);
        Playable playable = intent.getParcelableExtra(EXTRA_PLAYABLE);
        if (keycode == -1 && playable == null) {
            Log.e(TAG, "playbackService was started with no arguments");
            stateManager.stopService();
            return Service.START_NOT_STICKY;
        }

        if ((flags & Service.START_FLAG_REDELIVERY) != 0) {
            Log.d(TAG, "onStartCommand is a redelivered intent, calling stopForeground now.");
            stateManager.stopForeground(true);
        } else {
            if (keycode != -1) {
                boolean notificationButton;
                if (hardwareButton) {
                    notificationButton = false;
                } else {
                    notificationButton = true;
                }
                boolean handled = handleKeycode(keycode, notificationButton);
                if (!handled && !stateManager.hasReceivedValidStartCommand()) {
                    stateManager.stopService();
                    return Service.START_NOT_STICKY;
                }
            } else {
                stateManager.validStartCommandWasReceived();
                boolean allowStreamThisTime = intent.getBooleanExtra(EXTRA_ALLOW_STREAM_THIS_TIME, false);
                boolean allowStreamAlways = intent.getBooleanExtra(EXTRA_ALLOW_STREAM_ALWAYS, false);
                sendNotificationBroadcast(NOTIFICATION_TYPE_RELOAD, 0);
                if (allowStreamAlways) {
                    Prefs.setAllowMobileStreaming(true);
                }
                serviceDisposables.add(Observable.fromCallable(
                        () -> {
                            if (playable instanceof FeedMedia) {
                                return DBReader.getFeedMedia(((FeedMedia) playable).getId());
                            } else {
                                return playable;
                            }
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                loadedPlayable -> startPlaying(loadedPlayable, allowStreamThisTime),
                                error -> {
                                    Log.e(TAG, "Playable was not found. Stopping service.", error);
                                    stateManager.stopService();
                                }));
                return Service.START_NOT_STICKY;
            }
        }

        return Service.START_NOT_STICKY;
    }

    /**
     * Handles media button events
     * return: keycode was handled
     */
    boolean handleKeycode(int keycode, boolean notificationButton) {
        Log.d(TAG, "handle keycode: " + keycode);
        final PlaybackServiceMediaPlayer.PSMPInfo info = mediaPlayer.getPSMPInfo();
        final PlayerStatus status = info.playerStatus;
        switch (keycode) {
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (status == PlayerStatus.PLAYING) {
                    mediaPlayer.pause(!Prefs.isPersistNotify(), false);
                } else if (status == PlayerStatus.PAUSED || status == PlayerStatus.PREPARED) {
                    mediaPlayer.resume();
                } else if (status == PlayerStatus.PREPARING) {
                    mediaPlayer.setStartWhenPrepared(!mediaPlayer.isStartWhenPrepared());
                } else if (status == PlayerStatus.INITIALIZED) {
                    mediaPlayer.setStartWhenPrepared(true);
                    mediaPlayer.prepare();
                } else if (mediaPlayer.getPlayable() == null) {
                    startPlayingFromPreferences();
                } else {
                    return false;
                }
                taskManager.restartSleepTimer();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (status == PlayerStatus.PAUSED || status == PlayerStatus.PREPARED) {
                    mediaPlayer.resume();
                } else if (status == PlayerStatus.INITIALIZED) {
                    mediaPlayer.setStartWhenPrepared(true);
                    mediaPlayer.prepare();
                } else if (mediaPlayer.getPlayable() == null) {
                    startPlayingFromPreferences();
                } else {
                    return false;
                }
                taskManager.restartSleepTimer();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (status == PlayerStatus.PLAYING) {
                    mediaPlayer.pause(!Prefs.isPersistNotify(), false);
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                if (!notificationButton) {
                    // Handle remapped button as notification button which is not remapped again.
                    return handleKeycode(Prefs.getHardwareForwardButton(), true);
                } else if (getStatus() == PlayerStatus.PLAYING || getStatus() == PlayerStatus.PAUSED) {
                    mediaPlayer.skip();
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (getStatus() == PlayerStatus.PLAYING || getStatus() == PlayerStatus.PAUSED) {
                    mediaPlayer.seekDelta(Prefs.getFastForwardSecs() * 1000);
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                if (!notificationButton) {
                    // Handle remapped button as notification button which is not remapped again.
                    return handleKeycode(Prefs.getHardwarePreviousButton(), true);
                } else if (getStatus() == PlayerStatus.PLAYING || getStatus() == PlayerStatus.PAUSED) {
                    mediaPlayer.seekTo(0);
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (getStatus() == PlayerStatus.PLAYING || getStatus() == PlayerStatus.PAUSED) {
                    mediaPlayer.seekDelta(-Prefs.getRewindSecs() * 1000);
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                if (status == PlayerStatus.PLAYING) {
                    mediaPlayer.pause(true, true);
                }

                stateManager.stopForeground(true); // gets rid of persistent notification
                return true;
            default:
                Log.d(TAG, "unknown key code: " + keycode);
                if (info.playable != null && info.playerStatus == PlayerStatus.PLAYING) {   // only notify the user about an unknown key event if it is actually doing something
                    String message = String.format(getResources().getString(R.string.unknown_media_key), keycode);
                    TopSnackbarUtil.showSnack(this, message, Toast.LENGTH_SHORT);
                }
        }
        return false;
    }

    void startPlayingFromPreferences() {
        serviceDisposables.add(Observable.fromCallable(
                () -> PlayableUtils.createInstanceFromPreferences(getApplicationContext()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        playable -> startPlaying(playable, false),
                        error -> {
                            Log.e(TAG, "Playable was not loaded from preferences. Stopping service.", error);
                            stateManager.stopService();
                        }));
    }

    void startPlaying(Playable playable, boolean allowStreamThisTime) {
        boolean localFeed = URLUtil.isContentUrl(playable.getStreamUrl());
        boolean stream = !playable.localFileAvailable() || localFeed;
        if (stream && !localFeed && !NetworkUtils.isStreamingAllowed() && !allowStreamThisTime) {
            notificationUpdater.displayStreamingNotAllowedNotification(
                    new PlaybackServiceStarter(this, playable)
                            .getIntent());
            PlaybackPreferences.writeNoMediaPlaying();
            stateManager.stopService();
            return;
        }

        mediaPlayer.playMediaObject(playable, stream, true, true);
        stateManager.validStartCommandWasReceived();
        stateManager.startForeground(R.id.notification_playing, notificationUpdater.build());
        recreateMediaSessionIfNeeded();
        updateNotificationAndMediaSession(playable);
        addPlayableToQueue(playable);
    }

    /**
     * Called by a mediaplayer Activity as soon as it has prepared its
     * mediaplayer.
     */
    public void setVideoSurface(SurfaceHolder sh) {
        Log.d(TAG, "setting display");
        mediaPlayer.setVideoSurface(sh);
    }

    public void notifyVideoSurfaceAbandoned() {
        mediaPlayer.pause(true, false);
        mediaPlayer.resetVideoSurface();
        updateNotificationAndMediaSession(getPlayable());
        stateManager.stopForeground(!Prefs.isPersistNotify());
    }

    private final PlaybackServiceTaskManager.PSTMCallback taskManagerCallback = new PlaybackServiceTaskManager.PSTMCallback() {
        @Override
        public void positionSaverTick() {
            saveCurrentPosition(true, null, PlaybackServiceMediaPlayer.INVALID_TIME);
        }

        @Override
        public WidgetUpdater.WidgetState requestWidgetState() {
            return new WidgetUpdater.WidgetState(getPlayable(), getStatus(),
                    getCurrentPosition(), getDuration(), getCurrentPlaybackSpeed());
        }

        @Override
        public void onChapterLoaded(Playable media) {
            sendNotificationBroadcast(NOTIFICATION_TYPE_RELOAD, 0);
        }
    };

    @Subscribe(threadMode = ThreadMode.MAIN)
    @SuppressWarnings("unused")
    public void playerError(PlayerErrorEvent event) {
        if (mediaPlayer.getPlayerStatus() == PlayerStatus.PLAYING) {
            mediaPlayer.pause(true, false);
        }
        stateManager.stopService();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @SuppressWarnings("unused")
    public void bufferUpdate(BufferUpdateEvent event) {
        if (event.hasEnded()) {
            Playable playable = getPlayable();
            if (getPlayable() instanceof FeedMedia
                    && playable.getDuration() <= 0 && mediaPlayer.getDuration() > 0) {
                // Playable is being streamed and does not have a duration specified in the feed
                playable.setDuration(mediaPlayer.getDuration());
                DBWriter.setFeedMedia((FeedMedia) playable);
                updateNotificationAndMediaSession(playable);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @SuppressWarnings("unused")
    public void sleepTimerUpdate(SleepTimerUpdatedEvent event) {
        if (event.isOver()) {
            mediaPlayer.pause(true, true);
            mediaPlayer.setVolume(1.0f, 1.0f);
        } else if (event.getTimeLeft() < PlaybackServiceTaskManager.SleepTimer.NOTIFICATION_THRESHOLD) {
            final float[] multiplicators = {0.1f, 0.2f, 0.3f, 0.3f, 0.3f, 0.4f, 0.4f, 0.4f, 0.6f, 0.8f};
            float multiplicator = multiplicators[Math.max(0, (int) event.getTimeLeft() / 1000)];
            Log.d(TAG, "onSleepTimerAlmostExpired: " + multiplicator);
            mediaPlayer.setVolume(multiplicator, multiplicator);
        } else if (event.isCancelled()) {
            mediaPlayer.setVolume(1.0f, 1.0f);
        }
    }

    public void setSleepTimer(long waitingTime) {
        Log.d(TAG, "Setting sleep timer to " + waitingTime + " milliseconds");
        taskManager.setSleepTimer(waitingTime);
    }

    public void disableSleepTimer() {
        taskManager.disableSleepTimer();
    }

    void sendNotificationBroadcast(int type, int code) {
        Intent intent = new Intent(ACTION_PLAYER_NOTIFICATION);
        intent.putExtra(EXTRA_NOTIFICATION_TYPE, type);
        intent.putExtra(EXTRA_NOTIFICATION_CODE, code);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    void updateNotificationAndMediaSession(final Playable p) {
        setupNotification(p);
        mediaSessionHolder.updateMetadata(p);
    }

    /**
     * Prepares notification and starts the service in the foreground.
     */
    private synchronized void setupNotification(final Playable playable) {
        notificationUpdater.setupNotification(playable);
    }

    /**
     * Persists the current position and last played time of the media file.
     *
     * @param fromMediaPlayer if true, the information is gathered from the current Media Player
     *                        and {@param playable} and {@param position} become irrelevant.
     * @param playable        the playable for which the current position should be saved, unless
     *                        {@param fromMediaPlayer} is true.
     * @param position        the position that should be saved, unless {@param fromMediaPlayer} is true.
     */
    synchronized void saveCurrentPosition(boolean fromMediaPlayer, Playable playable, int position) {
        int duration;
        if (fromMediaPlayer) {
            position = getCurrentPosition();
            duration = getDuration();
            playable = mediaPlayer.getPlayable();
        } else {
            duration = playable.getDuration();
        }
        if (position != INVALID_TIME && duration != INVALID_TIME && playable != null) {
            PlayableUtils.saveCurrentPosition(playable, position, System.currentTimeMillis());
        }
    }

    public boolean sleepTimerActive() {
        return taskManager.isSleepTimerActive();
    }

    public long getSleepTimerTimeLeft() {
        return taskManager.getSleepTimerTimeLeft();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @SuppressWarnings("unused")
    public void volumeAdaptionChanged(VolumeAdaptionChangedEvent event) {
        PlaybackVolumeUpdater playbackVolumeUpdater = new PlaybackVolumeUpdater();
        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer, event.getFeedId(), event.getVolumeAdaptionSetting());
    }

    /**
     * A podcast's own speed changed outside the player (Feed Settings, bulk edit), or the global
     * default did (feed id 0). A podcast change is copied into the in-memory preferences of the
     * playing episode's feed so a later re-prepare uses it; either way the effective speed is
     * re-applied if it concerns what is playing.
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    @SuppressWarnings("unused")
    public void speedPresetChanged(SpeedPresetChangedEvent event) {
        Playable playable = getPlayable();
        if (playable == null) {
            return;
        }
        FeedPreferences playingPreferences = PlayableFeedPreferences.of(playable);
        if (event.isGlobal()) {
            if (!PlaybackSpeedUtils.hasOwnSpeed(playingPreferences)) {
                setSpeed(PlaybackSpeedUtils.getCurrentPlaybackSpeed(playable));
            }
        } else if (playingPreferences != null
                && PlayableFeedPreferences.feedIdOf(playable) == event.getFeedId()) {
            playingPreferences.setFeedPlaybackSpeed(event.getSpeed());
            setSpeed(PlaybackSpeedUtils.getCurrentPlaybackSpeed(playable));
        }
    }

    /**
     * The audio effects of a podcast, or the global defaults (feed id 0), changed. Like
     * {@link #speedPresetChanged}, a podcast change is copied into the in-memory preferences of
     * the playing episode's feed, then the effective effects are re-applied.
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    @SuppressWarnings("unused")
    public void audioEffectsChanged(AudioEffectsChangedEvent event) {
        Playable playable = getPlayable();
        if (playable == null) {
            return;
        }
        FeedPreferences playingPreferences = PlayableFeedPreferences.of(playable);
        if (event.isGlobal()) {
            if (!AudioEffectUtils.hasOwnEffects(playingPreferences)) {
                applyAudioEffects();
            }
        } else if (playingPreferences != null
                && PlayableFeedPreferences.feedIdOf(playable) == event.getFeedId()) {
            playingPreferences.setUseFeedEffect(event.isUseFeedEffect());
            playingPreferences.setSkipSilence(event.isSkipSilence());
            playingPreferences.setMono(event.isMono());
            playingPreferences.setLoudness(event.isLoudness());
            applyAudioEffects();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @SuppressWarnings("unused")
    public void skipIntroEndingPresetChanged(SkipIntroEndingChangedEvent event) {
        if (getPlayable() instanceof FeedMedia) {
            if (((FeedMedia) getPlayable()).getItem().getFeed().getId() == event.getFeedId()) {
                if (event.getSkipEnding() != 0) {
                   FeedPreferences feedPreferences
                           = ((FeedMedia) getPlayable()).getItem().getFeed().getPreferences();
                   feedPreferences.setFeedSkipIntro(event.getSkipIntro());
                   feedPreferences.setFeedSkipEnding(event.getSkipEnding());

                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @SuppressWarnings("unused")
    public void adSegmentsChanged(AdSegmentsChangedEvent event) {
        adSkipper.onSegmentsChanged(event.getFeedItemId());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @SuppressWarnings("unused")
    public void adSkipUndoRequested(AdSkipUndoEvent event) {
        adSkipper.onUndo(event);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @SuppressWarnings("unused")
    public void adSkipSettingChanged(AdSkipChangedEvent event) {
        adSkipper.onAdSkipSettingChanged(event.getFeedId());
    }

    public static MediaType getCurrentMediaType() {
        return currentMediaType;
    }

    public static boolean isCasting() {
        return isCasting;
    }

    public void resume() {
        mediaPlayer.resume();
        taskManager.restartSleepTimer();
    }

    public void prepare() {
        mediaPlayer.prepare();
        taskManager.restartSleepTimer();
    }

    public void pause(boolean abandonAudioFocus, boolean reinit) {
        mediaPlayer.pause(abandonAudioFocus, reinit);
    }

    public void reinit() {
        mediaPlayer.reinit();
    }

    public PlaybackServiceMediaPlayer.PSMPInfo getPSMPInfo() {
        return mediaPlayer.getPSMPInfo();
    }

    public PlayerStatus getStatus() {
        return mediaPlayer.getPlayerStatus();
    }

    public Playable getPlayable() {
        return mediaPlayer.getPlayable();
    }

    /** Applies {@code speed} to the player without storing it anywhere. */
    public void setSpeed(float speed) {
        mediaPlayer.setPlaybackParams(speed, AudioEffectUtils.isSkipEnable(getPlayable()));
    }

    /**
     * A speed the user chose in the player for what is playing: it becomes the podcast's own
     * speed (or the global default for media without a subscribed podcast) and is applied.
     * {@link FeedPreferences#SPEED_USE_GLOBAL} makes the podcast follow the default again.
     */
    public void setSpeedForCurrentMedia(float speed) {
        Playable playable = getPlayable();
        PlaybackSpeedUtils.rememberSpeed(playable, speed);
        setSpeed(PlaybackSpeedUtils.getCurrentPlaybackSpeed(playable));
    }

    /**
     * Re-applies skip silence, stereo to mono and vocal enhancement as configured for what is
     * playing (its podcast's own effects or the global defaults).
     */
    private void applyAudioEffects() {
        Playable playable = getPlayable();
        mediaPlayer.setPlaybackParams(PlaybackSpeedUtils.getCurrentPlaybackSpeed(playable),
                AudioEffectUtils.isSkipEnable(playable));
        mediaPlayer.setDownmix(AudioEffectUtils.isMonoEnable(playable));
        mediaPlayer.setLoudness(AudioEffectUtils.isLoudnessEnable(playable));
    }

    public float getCurrentPlaybackSpeed() {
        if (mediaPlayer == null) {
            return 1.0f;
        }
        return mediaPlayer.getPlaybackSpeed();
    }


    public boolean isStartWhenPrepared() {
        return mediaPlayer.isStartWhenPrepared();
    }

    public void setStartWhenPrepared(boolean s) {
        mediaPlayer.setStartWhenPrepared(s);
    }

    public void seekTo(final int t) {
        mediaPlayer.seekTo(t);
        EventBus.getDefault().post(new PlaybackPositionEvent(t, getDuration()));
    }

    void seekDelta(final int d) {
        mediaPlayer.seekDelta(d);
    }

    /**
     * call getDuration() on mediaplayer or return INVALID_TIME if player is in
     * an invalid state.
     */
    public int getDuration() {
        if (mediaPlayer == null) {
            return INVALID_TIME;
        }
        return mediaPlayer.getDuration();
    }

    /**
     * call getCurrentPosition() on mediaplayer or return INVALID_TIME if player
     * is in an invalid state.
     */
    public int getCurrentPosition() {
        if (mediaPlayer == null) {
            return INVALID_TIME;
        }
        return mediaPlayer.getPosition();
    }

    public List<String> getAudioTracks() {
        if (mediaPlayer == null) {
            return Collections.emptyList();
        }
        return mediaPlayer.getAudioTracks();
    }

    public int getSelectedAudioTrack() {
        if (mediaPlayer == null) {
            return -1;
        }
        return mediaPlayer.getSelectedAudioTrack();
    }

    public void setAudioTrack(int track) {
        if (mediaPlayer != null) {
            mediaPlayer.setAudioTrack(track);
        }
    }

    public boolean isStreaming() {
        return mediaPlayer.isStreaming();
    }

    public Pair<Integer, Integer> getVideoSize() {
        return mediaPlayer.getVideoSize();
    }

    void setupPositionObserver() {
        if (positionEventTimer != null) {
            positionEventTimer.dispose();
        }

        Log.d(TAG, "setting up position observer");
        positionEventTimer = Observable.interval(1, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(number -> {
                    EventBus.getDefault().post(new PlaybackPositionEvent(getCurrentPosition(), getDuration()));
                    if (Build.VERSION.SDK_INT < 29) {
                        notificationUpdater.updatePositionAndNotify(getCurrentPosition(), getCurrentPlaybackSpeed());
                    }
                    autoSkipper.skipEndingIfNecessary();
                    adSkipper.skipIfNecessary();
                }, error -> Log.e(TAG, "Position observer failed", error));
    }

    void cancelPositionObserver() {
        if (positionEventTimer != null) {
            positionEventTimer.dispose();
        }
    }

    private void addPlayableToQueue(Playable playable) {
        if (playable instanceof FeedMedia) {
            long itemId = ((FeedMedia) playable).getItem().getId();
            DBWriter.addQueueItem(this, false, true, itemId);
        }
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (sharedPreferences, key) -> {
                if (Prefs.PREF_LOCKSCREEN_BACKGROUND.equals(key)) {
                    updateNotificationAndMediaSession(getPlayable());
                }
            };
}
