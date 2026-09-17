package allen.town.podcast.core.service.playback;

import android.media.AudioManager;
import android.os.PowerManager;
import android.util.Log;

import org.antennapod.audio.MediaPlayer;
import org.greenrobot.eventbus.EventBus;

import allen.town.podcast.core.pref.Prefs;
import allen.town.podcast.core.util.playback.IPlayer;
import allen.town.podcast.core.util.playback.MediaPlayerError;
import allen.town.podcast.core.util.playback.VideoPlayer;
import allen.town.podcast.event.PlayerErrorEvent;
import allen.town.podcast.event.playback.BufferUpdateEvent;
import allen.town.podcast.model.playback.MediaType;
import allen.town.podcast.model.playback.Playable;

/**
 * Creates the concrete {@link IPlayer} that {@link LocalPSMP} plays with - an
 * {@link ExoPlayerWrapper} or a {@link VideoPlayer}, depending on the preferences and the media
 * type - configures it and wires up every player listener (completion, seek complete, buffering,
 * info and error), translating those callbacks into {@link EventBus} events and calls back into
 * {@link LocalPSMP}. It also detaches the listeners again before the player is released.
 */
class LocalPSMPPlayerFactory {
    private static final String TAG = "LocalMediaPlayer";

    private final LocalPSMP player;

    LocalPSMPPlayerFactory(LocalPSMP player) {
        this.player = player;
    }

    /**
     * Creates the player for the given media and attaches the listeners to it. The already released
     * {@code current} player is passed in and returned unchanged when neither branch below applies,
     * exactly as the inlined version of this code did.
     */
    IPlayer createAndWire(IPlayer current, Playable media) {
        IPlayer mediaPlayer = current;
        if (Prefs.useExoplayer()) {
            mediaPlayer = new ExoPlayerWrapper(player.playerContext());
        } else if (media.getMediaType() == MediaType.VIDEO) {
            mediaPlayer = new VideoPlayer();
        }

        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setWakeMode(player.playerContext(), PowerManager.PARTIAL_WAKE_LOCK);
        setMediaPlayerListeners(mediaPlayer, media);
        return mediaPlayer;
    }

    private void setMediaPlayerListeners(IPlayer mp, Playable media) {
        if (mp == null || media == null) {
            return;
        }
        if (mp instanceof VideoPlayer) {
            if (media.getMediaType() != MediaType.VIDEO) {
                Log.w(TAG, "video player, but media type is " + media.getMediaType());
            }
            VideoPlayer vp = (VideoPlayer) mp;
            vp.setOnCompletionListener(videoCompletionListener);
            vp.setOnSeekCompleteListener(videoSeekCompleteListener);
            vp.setOnErrorListener(videoErrorListener);
            vp.setOnBufferingUpdateListener(videoBufferingUpdateListener);
            vp.setOnInfoListener(videoInfoListener);
        } else if (mp instanceof ExoPlayerWrapper) {
            ExoPlayerWrapper ap = (ExoPlayerWrapper) mp;
            ap.setOnCompletionListener(audioCompletionListener);
            ap.setOnSeekCompleteListener(audioSeekCompleteListener);
            ap.setOnBufferingUpdateListener(audioBufferingUpdateListener);
            ap.setOnErrorListener(message -> EventBus.getDefault().postSticky(new PlayerErrorEvent(message)));
            ap.setOnInfoListener(audioInfoListener);
        } else {
            Log.w(TAG, "Unknown media player: " + mp);
        }
    }

    void clearMediaPlayerListeners(IPlayer mediaPlayer) {
        if (mediaPlayer instanceof VideoPlayer) {
            VideoPlayer vp = (VideoPlayer) mediaPlayer;
            vp.setOnCompletionListener(x -> { });
            vp.setOnSeekCompleteListener(x -> { });
            vp.setOnErrorListener((mp, i, i1) -> false);
            vp.setOnBufferingUpdateListener((mp, i) -> { });
            vp.setOnInfoListener((mp, i, i1) -> false);
        } else if (mediaPlayer instanceof ExoPlayerWrapper) {
            ExoPlayerWrapper ap = (ExoPlayerWrapper) mediaPlayer;
            ap.setOnCompletionListener(x -> { });
            ap.setOnSeekCompleteListener(x -> { });
            ap.setOnBufferingUpdateListener((arg0, percent) -> { });
            ap.setOnErrorListener(x -> { });
            ap.setOnInfoListener((arg0, what, extra) -> false);
        }
    }

    private final MediaPlayer.OnCompletionListener audioCompletionListener =
            mp -> genericOnCompletion();

    private final android.media.MediaPlayer.OnCompletionListener videoCompletionListener =
            mp -> genericOnCompletion();

    private void genericOnCompletion() {
        player.onPlaybackCompletedByPlayer();
    }

    private final MediaPlayer.OnBufferingUpdateListener audioBufferingUpdateListener =
            (mp, percent) -> EventBus.getDefault().post(BufferUpdateEvent.progressUpdate(0.01f * percent));

    private final android.media.MediaPlayer.OnBufferingUpdateListener videoBufferingUpdateListener =
            (mp, percent) -> EventBus.getDefault().post(BufferUpdateEvent.progressUpdate(0.01f * percent));

    private final MediaPlayer.OnInfoListener audioInfoListener =
            (mp, what, extra) -> genericInfoListener(what);

    private final android.media.MediaPlayer.OnInfoListener videoInfoListener =
            (mp, what, extra) -> genericInfoListener(what);

    private boolean genericInfoListener(int what) {
        switch (what) {
            case android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START:
                EventBus.getDefault().post(BufferUpdateEvent.started());
                return true;
            case android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END:
                EventBus.getDefault().post(BufferUpdateEvent.ended());
                return true;
            default:
                return true;
        }
    }

    @SuppressWarnings("unused")
    private final MediaPlayer.OnErrorListener audioErrorListener =
            (mp, what, extra) -> {
                if (mp != null && mp.canFallback()) {
                    mp.fallback();
                    return true;
                } else {
                    return genericOnError(mp, what, extra);
                }
            };

    private final android.media.MediaPlayer.OnErrorListener videoErrorListener = this::genericOnError;

    private boolean genericOnError(Object inObj, int what, int extra) {
        EventBus.getDefault().postSticky(
                new PlayerErrorEvent(MediaPlayerError.getErrorString(player.playerContext(), what)));
        return true;
    }

    private final MediaPlayer.OnSeekCompleteListener audioSeekCompleteListener =
            mp -> genericSeekCompleteListener();

    private final android.media.MediaPlayer.OnSeekCompleteListener videoSeekCompleteListener =
            mp -> genericSeekCompleteListener();

    private void genericSeekCompleteListener() {
        player.onSeekComplete();
    }
}
