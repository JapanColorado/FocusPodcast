package dev.russell.yagi.core.util.playback;

import android.media.MediaPlayer;
import android.util.Log;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class VideoPlayer extends MediaPlayer implements IPlayer {
    private static final String TAG = "VideoPlayer";

    @Override
    public boolean canDownmix() {
        return false;
    }

    @Override
    public float getCurrentSpeedMultiplier() {
        return 1;
    }

    @Override
    public void setPlaybackParams(float speed, boolean skipSilence) {
        //Ignore this for non ExoPlayer implementations
    }

    @Override
    public void setDownmix(boolean b) {
        Log.e(TAG, "Setting downmix unsupported in video player");
        throw new UnsupportedOperationException("Setting downmix unsupported in video player");
    }

    @Override
    public void setClearVoiceLevel(int level) {
        // The framework MediaPlayer has no audio processor hook; Clear voice is ExoPlayer only.
        Log.d(TAG, "Clear voice is not supported in the video player, ignoring level " + level);
    }

    @Override
    public void setVolumeLeveling(int mode) {
        // Same as Clear voice: it runs in ExoPlayer's audio sink.
        Log.d(TAG, "Volume leveling is not supported in the video player, ignoring mode " + mode);
    }


    @Override
    public void setVideoScalingMode(int mode) {
        super.setVideoScalingMode(mode);
    }

    public List<String> getAudioTracks() {
        return Collections.emptyList();
    }

    @Override
    public void setAudioTrack(int track) {
    }

    @Override
    public int getSelectedAudioTrack() {
        return -1;
    }

    @Override
    public void setDataSource(String streamUrl, String username, String password) throws IOException {
        setDataSource(streamUrl);
    }
}
