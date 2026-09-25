package allen.town.podcast.core.service.playback;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;

import allen.town.podcast.core.feed.util.AudioEffectUtils;
import allen.town.podcast.event.PlayerErrorEvent;
import allen.town.podcast.playback.base.PlaybackServiceMediaPlayer;
import allen.town.podcast.playback.base.PlayerStatus;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import allen.town.podcast.model.feed.FeedMedia;
import allen.town.podcast.model.feed.FeedPreferences;
import allen.town.podcast.model.playback.MediaType;
import allen.town.podcast.core.feed.util.PlaybackSpeedUtils;
import allen.town.podcast.core.pref.Prefs;
import allen.town.podcast.playback.base.RewindAfterPauseUtils;
import allen.town.podcast.core.util.playback.IPlayer;
import allen.town.podcast.model.playback.Playable;
import allen.town.podcast.core.util.playback.VideoPlayer;
import org.greenrobot.eventbus.EventBus;

/**
 * Manages the MediaPlayer object of the PlaybackService.
 * <p/>
 * This class owns the player, the media it plays and the player status, and keeps every entry
 * point of {@code PlaybackServiceMediaPlayer}. Cohesive groups of its work are delegated to
 * collaborators in this package, which reach back into it through the package-private members and
 * bridges below: audio focus ({@link LocalPSMPAudioFocus}), creating the player and wiring up its
 * listeners ({@link LocalPSMPPlayerFactory}), seeking ({@link LocalPSMPSeeker}), speed / volume /
 * audio effects ({@link LocalPSMPAudioEffects}) and what happens once the current media stops
 * playing ({@link LocalPSMPPlaybackEnder}). The threading policy - run everything on the caller
 * thread for ExoPlayer, on a single background thread otherwise - lives in {@link PlayerExecutor}
 * and {@link PlayerLock}.
 */
public class LocalPSMP extends PlaybackServiceMediaPlayer {
    private static final String TAG = "LocalMediaPlayer";

    volatile IPlayer mediaPlayer;
    volatile Playable media;

    private volatile boolean stream;
    private volatile MediaType mediaType;
    final AtomicBoolean startWhenPrepared;
    private volatile Pair<Integer, Integer> videoSize;

    /**
     * Some asynchronous calls might change the state of the MediaPlayer object. Therefore calls in other threads
     * have to wait until these operations have finished.
     */
    final PlayerLock playerLock;
    final PlayerExecutor executor;

    /**
     * All ExoPlayer methods must be executed on the same thread, so for ExoPlayer everything runs
     * on the calling thread and neither the lock nor the executor do anything. Shared with
     * {@link PlayerLock} and {@link PlayerExecutor}, which read it on every call.
     */
    final AtomicBoolean useCallerThread = new AtomicBoolean(true);
    boolean isShutDown = false;

    private final LocalPSMPAudioFocus audioFocus;
    private final LocalPSMPSeeker seeker;
    private final LocalPSMPPlayerFactory playerFactory;
    private final LocalPSMPAudioEffects audioEffects;
    private final LocalPSMPPlaybackEnder playbackEnder;

    public LocalPSMP(@NonNull Context context,
                     @NonNull PlaybackServiceMediaPlayer.PSMPCallback callback) {
        super(context, callback);
        this.playerLock = new PlayerLock(useCallerThread);
        this.startWhenPrepared = new AtomicBoolean(false);
        this.seeker = new LocalPSMPSeeker(this);
        this.playerFactory = new LocalPSMPPlayerFactory(this);
        this.audioEffects = new LocalPSMPAudioEffects(this);
        this.playbackEnder = new LocalPSMPPlaybackEnder(this);

        executor = new PlayerExecutor(useCallerThread);

        mediaPlayer = null;
        mediaType = MediaType.UNKNOWN;
        videoSize = null;

        audioFocus = new LocalPSMPAudioFocus(this, context);
    }

    /**
     * Starts or prepares playback of the specified Playable object. If another Playable object is already being played, the currently playing
     * episode will be stopped and replaced with the new Playable object. If the Playable object is already being played, the method will
     * not do anything.
     * Whether playback starts immediately depends on the given parameters. See below for more details.
     * <p/>
     * States:
     * During execution of the method, the object will be in the INITIALIZING state. The end state depends on the given parameters.
     * <p/>
     * If 'prepareImmediately' is set to true, the method will go into PREPARING state and after that into PREPARED state. If
     * 'startWhenPrepared' is set to true, the method will additionally go into PLAYING state.
     * <p/>
     * If an unexpected error occurs while loading the Playable's metadata or while setting the MediaPlayers data source, the object
     * will enter the ERROR state.
     * <p/>
     * This method is executed on an internal executor service.
     *
     * @param playable           The Playable object that is supposed to be played. This parameter must not be null.
     * @param stream             The type of playback. If false, the Playable object MUST provide access to a locally available file via
     *                           getLocalMediaUrl. If true, the Playable object MUST provide access to a resource that can be streamed by
     *                           the Android MediaPlayer via getStreamUrl.
     * @param startWhenPrepared  Sets the 'startWhenPrepared' flag. This flag determines whether playback will start immediately after the
     *                           episode has been prepared for playback. Setting this flag to true does NOT mean that the episode will be prepared
     *                           for playback immediately (see 'prepareImmediately' parameter for more details)
     * @param prepareImmediately Set to true if the method should also prepare the episode for playback.
     */
    @Override
    public void playMediaObject(@NonNull final Playable playable, final boolean stream, final boolean startWhenPrepared, final boolean prepareImmediately) {
        Log.d(TAG, "playing");
        useCallerThread.set(Prefs.useExoplayer());
        executor.submit(() -> {
            playerLock.lock();
            try {
                playMediaObject(playable, false, stream, startWhenPrepared, prepareImmediately);
            } catch (RuntimeException e) {
                // Rethrown: the caller's executor task must fail so the error is not silently lost.
                Log.e(TAG, "Failed to start playing " + playable.getEpisodeTitle(), e);
                throw e;
            } finally {
                playerLock.unlock();
            }
        });
    }

    /**
     * Internal implementation of playMediaObject. This method has an additional parameter that allows the caller to force a media player reset even if
     * the given playable parameter is the same object as the currently playing media.
     * <p/>
     * This method requires the playerLock and is executed on the caller's thread.
     *
     * @see #playMediaObject(Playable, boolean, boolean, boolean)
     */
    private void playMediaObject(@NonNull final Playable playable, final boolean forceReset, final boolean stream, final boolean startWhenPrepared, final boolean prepareImmediately) {
        if (!playerLock.isHeldByCurrentThread()) {
            throw new IllegalStateException("method requires playerLock");
        }


        if (media != null) {
            if (!forceReset && media.getIdentifier().equals(playable.getIdentifier())
                    && playerStatus == PlayerStatus.PLAYING) {
                // episode is already playing -> ignore method call
                Log.d(TAG, "already in playing");
                return;
            } else {
                // stop playback of this episode
                if (playerStatus == PlayerStatus.PAUSED || playerStatus == PlayerStatus.PLAYING || playerStatus == PlayerStatus.PREPARED) {
                    mediaPlayer.stop();
                }
                // set temporarily to pause in order to update list with current position
                if (playerStatus == PlayerStatus.PLAYING) {
                    callback.onPlaybackPause(media, getPosition());
                }

                if (!media.getIdentifier().equals(playable.getIdentifier())) {
                    final Playable oldMedia = media;
                    executor.submit(() -> callback.onPostPlayback(oldMedia, false, false, true));
                }

                setPlayerStatus(PlayerStatus.INDETERMINATE, null);
            }
        }

        this.media = playable;
        this.stream = stream;
        this.mediaType = media.getMediaType();
        this.videoSize = null;
        createMediaPlayer();
        LocalPSMP.this.startWhenPrepared.set(startWhenPrepared);
        setPlayerStatus(PlayerStatus.INITIALIZING, media);
        try {
            callback.ensureMediaInfoLoaded(media);
            callback.onMediaChanged(false);
            setPlaybackParams(PlaybackSpeedUtils.getCurrentPlaybackSpeed(media), AudioEffectUtils.isSkipEnable(media));
            audioEffects.setAudioEffect();
            if (stream) {
                if (playable instanceof FeedMedia) {
                    FeedMedia feedMedia = (FeedMedia) playable;
                    FeedPreferences preferences = feedMedia.getItem().getFeed().getPreferences();
                    mediaPlayer.setDataSource(
                            media.getStreamUrl(),
                            preferences.getUsername(),
                            preferences.getPassword());
                } else {
                    mediaPlayer.setDataSource(media.getStreamUrl());
                }
            } else if (media.getLocalMediaUrl() != null && new File(media.getLocalMediaUrl()).canRead()) {
                mediaPlayer.setDataSource(media.getLocalMediaUrl());
            } else {
                throw new IOException("Unable to read local file " + media.getLocalMediaUrl());
            }
            UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
            if (uiModeManager.getCurrentModeType() != Configuration.UI_MODE_TYPE_CAR) {
                setPlayerStatus(PlayerStatus.INITIALIZED, media);
            }

            if (prepareImmediately) {
                setPlayerStatus(PlayerStatus.PREPARING, media);
                mediaPlayer.prepare();
                onPrepared(startWhenPrepared);
            }

        } catch (IOException | IllegalStateException e) {
            // Reported to the UI through PlayerErrorEvent below; nothing else can be done here.
            Log.e(TAG, "Failed to prepare media player", e);
            setPlayerStatus(PlayerStatus.ERROR, null);
            EventBus.getDefault().postSticky(new PlayerErrorEvent(e.getLocalizedMessage()));
        }
    }

    /**
     * Resumes playback if the PSMP object is in PREPARED or PAUSED state. If the PSMP object is in an invalid state.
     * nothing will happen.
     * <p/>
     * This method is executed on an internal executor service.
     */
    @Override
    public void resume() {
        executor.submit(() -> {
            playerLock.lock();
            resumeSync();
            playerLock.unlock();
        });
    }

    private void resumeSync() {
        if (playerStatus == PlayerStatus.PAUSED || playerStatus == PlayerStatus.PREPARED) {
            int focusGained = audioFocus.request();

            if (focusGained == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d(TAG, "audio focus requested");
                acquireWifiLockIfNecessary();

                setPlaybackParams(PlaybackSpeedUtils.getCurrentPlaybackSpeed(media), AudioEffectUtils.isSkipEnable(media));
                audioEffects.setAudioEffect();
                setVolume(1.0f, 1.0f);

                if (playerStatus == PlayerStatus.PREPARED && media.getPosition() > 0) {
                    int newPosition = RewindAfterPauseUtils.calculatePositionWithRewind(
                        media.getPosition(),
                        media.getLastPlayedTime());
                    seeker.seekToSync(newPosition);
                }
                mediaPlayer.start();

                setPlayerStatus(PlayerStatus.PLAYING, media);
                audioFocus.clearTransientLossFlag();
            } else {
                Log.e(TAG, "failed to request audio focus");
            }
        } else {
            Log.d(TAG, "resume was ignored because current state of PSMP object is " + playerStatus);
        }
    }


    /**
     * Saves the current position and pauses playback. Note that, if audiofocus
     * is abandoned, the lockscreen controls will also disapear.
     * <p/>
     * This method is executed on an internal executor service.
     *
     * @param abandonFocus is true if the service should release audio focus
     * @param reinit       is true if service should reinit after pausing if the media
     *                     file is being streamed
     */
    @Override
    public void pause(final boolean abandonFocus, final boolean reinit) {
        executor.submit(() -> {
            playerLock.lock();
            releaseWifiLockIfNecessary();
            if (playerStatus == PlayerStatus.PLAYING) {
                Log.d(TAG, "pause play");
                mediaPlayer.pause();
                setPlayerStatus(PlayerStatus.PAUSED, media, getPosition());

                if (abandonFocus) {
                    audioFocus.abandon();
                    audioFocus.clearTransientLossFlag();
                }
                if (stream && reinit) {
                    reinit();
                }
            } else {
                Log.d(TAG, "ignore call to pause: player is in " + playerStatus + " state");
            }

            playerLock.unlock();
        });
    }

    /**
     * Prepares media player for playback if the service is in the INITALIZED
     * state.
     * <p/>
     * This method is executed on an internal executor service.
     */
    @Override
    public void prepare() {
        executor.submit(() -> {
            playerLock.lock();

            if (playerStatus == PlayerStatus.INITIALIZED) {
                setPlayerStatus(PlayerStatus.PREPARING, media);
                try {
                    mediaPlayer.prepare();
                    onPrepared(startWhenPrepared.get());
                } catch (IOException e) {
                    // Reported to the UI through PlayerErrorEvent below.
                    Log.e(TAG, "Failed to prepare media player", e);
                    setPlayerStatus(PlayerStatus.ERROR, null);
                    EventBus.getDefault().postSticky(new PlayerErrorEvent(e.getLocalizedMessage()));
                }
            }
            playerLock.unlock();

        });
    }

    /**
     * Called after media player has been prepared. This method is executed on the caller's thread.
     */
    private void onPrepared(final boolean startWhenPrepared) {
        playerLock.lock();

        if (playerStatus != PlayerStatus.PREPARING) {
            playerLock.unlock();
            throw new IllegalStateException("Player is not in PREPARING state");
        }


        if (mediaType == MediaType.VIDEO && mediaPlayer instanceof ExoPlayerWrapper) {
            ExoPlayerWrapper vp = (ExoPlayerWrapper) mediaPlayer;
            videoSize = new Pair<>(vp.getVideoWidth(), vp.getVideoHeight());
        } else if (mediaType == MediaType.VIDEO && mediaPlayer instanceof VideoPlayer) {
            VideoPlayer vp = (VideoPlayer) mediaPlayer;
            videoSize = new Pair<>(vp.getVideoWidth(), vp.getVideoHeight());
        }

        // TODO this call has no effect!
        if (media.getPosition() > 0) {
            seeker.seekToSync(media.getPosition());
        }

        if (media.getDuration() <= 0) {
            media.setDuration(mediaPlayer.getDuration());
        }
        setPlayerStatus(PlayerStatus.PREPARED, media);

        if (startWhenPrepared) {
            resumeSync();
        }

        playerLock.unlock();
    }

    /**
     * Resets the media player and moves it into INITIALIZED state.
     * <p/>
     * This method is executed on an internal executor service.
     */
    @Override
    public void reinit() {
        useCallerThread.set(Prefs.useExoplayer());
        executor.submit(() -> {
            playerLock.lock();
            Log.d(TAG, "re init");
            releaseWifiLockIfNecessary();
            if (media != null) {
                playMediaObject(media, true, stream, startWhenPrepared.get(), false);
            } else if (mediaPlayer != null) {
                mediaPlayer.reset();
            } else {
                Log.d(TAG, "call to re-init was ignored, media and mediaPlayer were null");
            }
            playerLock.unlock();
        });
    }

    /**
     * Seeks to the specified position. If the PSMP object is in an invalid state, this method will do nothing.
     * Invalid time values (< 0) will be ignored.
     * <p/>
     * This method is executed on an internal executor service.
     */
    @Override
    public void seekTo(final int t) {
        executor.submit(() -> seeker.seekToSync(t));
    }

    /**
     * Seek a specific position from the current position
     *
     * @param d offset from current position (positive or negative)
     */
    @Override
    public void seekDelta(final int d) {
        executor.submit(() -> {
            playerLock.lock();
            int currentPosition = getPosition();
            if (currentPosition != INVALID_TIME) {
                seeker.seekToSync(currentPosition + d);
            } else {
                Log.e(TAG, "getPosition() returned INVALID_TIME in seekDelta");
            }

            playerLock.unlock();
        });
    }

    /**
     * Returns the duration of the current media object or INVALID_TIME if the duration could not be retrieved.
     */
    @Override
    public int getDuration() {
        if (!playerLock.tryLock()) {
            return INVALID_TIME;
        }

        int retVal = INVALID_TIME;
        if (playerStatus == PlayerStatus.PLAYING
                || playerStatus == PlayerStatus.PAUSED
                || playerStatus == PlayerStatus.PREPARED) {
            retVal = mediaPlayer.getDuration();
        }
        if (retVal <= 0 && media != null && media.getDuration() > 0) {
            retVal = media.getDuration();
        }

        playerLock.unlock();
        return retVal;
    }

    /**
     * Returns the position of the current media object or INVALID_TIME if the position could not be retrieved.
     */
    @Override
    public int getPosition() {
        try {
            if (!playerLock.tryLock(50, TimeUnit.MILLISECONDS)) {
                return INVALID_TIME;
            }
        } catch (InterruptedException e) {
            // Documented fallback of this method: INVALID_TIME when the position cannot be read
            // right now. Restore the flag so the caller still sees the interruption.
            Thread.currentThread().interrupt();
            return INVALID_TIME;
        }

        int retVal = INVALID_TIME;
        if (playerStatus.isAtLeast(PlayerStatus.PREPARED)) {
            retVal = mediaPlayer.getCurrentPosition();
        }
        if (retVal <= 0 && media != null && media.getPosition() >= 0) {
            retVal = media.getPosition();
        }

        playerLock.unlock();
        return retVal;
    }

    @Override
    public boolean isStartWhenPrepared() {
        return startWhenPrepared.get();
    }

    @Override
    public void setStartWhenPrepared(boolean startWhenPrepared) {
        this.startWhenPrepared.set(startWhenPrepared);
    }


    /**
     * Sets the playback speed.
     * This method is executed on an internal executor service.
     */
    @Override
    public void setPlaybackParams(final float speed, final boolean skipSilence) {
        audioEffects.setPlaybackParams(speed, skipSilence);
    }

    /**
     * Returns the current playback speed. If the playback speed could not be retrieved, 1 is returned.
     */
    @Override
    public float getPlaybackSpeed() {
        return audioEffects.getPlaybackSpeed();
    }

    /**
     * Sets the playback volume.
     * This method is executed on an internal executor service.
     */
    @Override
    public void setVolume(final float volumeLeft, float volumeRight) {
        audioEffects.setVolume(volumeLeft, volumeRight);
    }

    /**
     * Sets the playback volume.
     * This method is executed on the caller's thread.
     */
    void setVolumeSync(float volumeLeft, float volumeRight) {
        audioEffects.setVolumeSync(volumeLeft, volumeRight);
    }

    /**
     * Returns true if the mediaplayer can mix stereo down to mono
     */
    @Override
    public boolean canDownmix() {
        return audioEffects.canDownmix();
    }

    @Override
    public void setDownmix(boolean enable) {
        audioEffects.setDownmix(enable);
    }

    @Override
    public void setLoudness(boolean enable) {
        audioEffects.setLoudness(enable);
    }

    @Override
    public MediaType getCurrentMediaType() {
        return mediaType;
    }

    @Override
    public boolean isStreaming() {
        return stream;
    }

    /**
     * Releases internally used resources. This method should only be called when the object is not used anymore.
     */
    @Override
    public void shutdown() {
        if (mediaPlayer != null) {
            try {
                playerFactory.clearMediaPlayerListeners(mediaPlayer);
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (Exception e) {
                // Best effort: the player is released right below either way, and an
                // IllegalStateException here only means it was already in a dead state.
                Log.e(TAG, "Error while stopping media player during shutdown", e);
            }
            mediaPlayer.release();
            mediaPlayer = null;
            playerStatus = PlayerStatus.STOPPED;
        }
        isShutDown = true;
        executor.shutdown();
        audioFocus.cancelPendingLossCallback();
        audioFocus.abandon();
        releaseWifiLockIfNecessary();
    }

    @Override
    public void setVideoSurface(final SurfaceHolder surface) {
        executor.submit(() -> {
            playerLock.lock();
            if (mediaPlayer != null) {
                mediaPlayer.setDisplay(surface);
            }
            playerLock.unlock();
        });
    }

    @Override
    public void resetVideoSurface() {
        executor.submit(() -> {
            playerLock.lock();
            if (mediaType == MediaType.VIDEO) {
                Log.d(TAG, "reset video surface");
                mediaPlayer.setDisplay(null);
                reinit();
            } else {
            }
            playerLock.unlock();
        });
    }

    /**
     * Return width and height of the currently playing video as a pair.
     *
     * @return Width and height as a Pair or null if the video size could not be determined. The method might still
     * return an invalid non-null value if the getVideoWidth() and getVideoHeight() methods of the media player return
     * invalid values.
     */
    @Override
    public Pair<Integer, Integer> getVideoSize() {
        if (!playerLock.tryLock()) {
            // use cached value if lock can't be aquired
            return videoSize;
        }
        Pair<Integer, Integer> res;
        if (mediaPlayer == null || playerStatus == PlayerStatus.ERROR || mediaType != MediaType.VIDEO) {
            res = null;
        } else if (mediaPlayer instanceof ExoPlayerWrapper) {
            ExoPlayerWrapper vp = (ExoPlayerWrapper) mediaPlayer;
            videoSize = new Pair<>(vp.getVideoWidth(), vp.getVideoHeight());
            res = videoSize;
        } else {
            VideoPlayer vp = (VideoPlayer) mediaPlayer;
            videoSize = new Pair<>(vp.getVideoWidth(), vp.getVideoHeight());
            res = videoSize;
        }
        playerLock.unlock();
        return res;
    }

    /**
     * Returns the current media, if you need the media and the player status together, you should
     * use getPSMPInfo() to make sure they're properly synchronized. Otherwise a race condition
     * could result in nonsensical results (like a status of PLAYING, but a null playable)
     * @return the current media. May be null
     */
    @Override
    public Playable getPlayable() {
        return media;
    }

    @Override
    protected void setPlayable(Playable playable) {
        media = playable;
    }

    public List<String> getAudioTracks() {
        return mediaPlayer.getAudioTracks();
    }

    public void setAudioTrack(int track) {
        mediaPlayer.setAudioTrack(track);
    }

    public int getSelectedAudioTrack() {
        return mediaPlayer.getSelectedAudioTrack();
    }

    private void createMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        if (media == null) {
            mediaPlayer = null;
            playerStatus = PlayerStatus.STOPPED;
            return;
        }

        mediaPlayer = playerFactory.createAndWire(mediaPlayer, media);
    }

    @Override
    protected Future<?> endPlayback(final boolean hasEnded, final boolean wasSkipped,
                                    final boolean shouldContinue, final boolean toStoppedState) {
        useCallerThread.set(Prefs.useExoplayer());
        return playbackEnder.endPlayback(hasEnded, wasSkipped, shouldContinue, toStoppedState);
    }

    @Override
    protected boolean shouldLockWifi() {
        return stream;
    }

    @Override
    public boolean isCasting() {
        return false;
    }

    // --- Bridges for the collaborators of this package, which cannot see the protected members
    // --- of PlaybackServiceMediaPlayer or this class' private state.

    /**
     * The current player status, read without acquiring the monitor that
     * {@link #getPlayerStatus()} takes.
     */
    PlayerStatus status() {
        return playerStatus;
    }

    void updateStatus(PlayerStatus newStatus, Playable newMedia, int position) {
        setPlayerStatus(newStatus, newMedia, position);
    }

    void updateStatus(PlayerStatus newStatus, Playable newMedia) {
        setPlayerStatus(newStatus, newMedia);
    }

    void releaseWifiLock() {
        releaseWifiLockIfNecessary();
    }

    void abandonAudioFocus() {
        audioFocus.abandon();
    }

    /**
     * Runs the internal implementation of {@code playMediaObject} on the caller's thread; requires
     * the player lock, exactly like the private method it calls.
     */
    void playMediaObjectSync(@NonNull final Playable playable, final boolean forceReset,
                             final boolean stream, final boolean startWhenPrepared,
                             final boolean prepareImmediately) {
        playMediaObject(playable, forceReset, stream, startWhenPrepared, prepareImmediately);
    }

    PSMPCallback playerCallback() {
        return callback;
    }

    Context playerContext() {
        return context;
    }

    void notifyShouldStop() {
        callback.shouldStop();
    }

    /**
     * Called by the player listeners when the current media played to its end.
     */
    void onPlaybackCompletedByPlayer() {
        endPlayback(true, false, true, true);
    }

    /**
     * Called by the player listeners once a seek has finished.
     */
    void onSeekComplete() {
        seeker.onSeekComplete();
    }
}
