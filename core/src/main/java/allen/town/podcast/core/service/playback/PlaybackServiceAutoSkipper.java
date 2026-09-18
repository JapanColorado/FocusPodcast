package allen.town.podcast.core.service.playback;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import allen.town.podcast.common.util.TopSnackbarUtil;
import allen.town.podcast.core.R;
import allen.town.podcast.model.feed.FeedItem;
import allen.town.podcast.model.feed.FeedMedia;
import allen.town.podcast.model.feed.FeedPreferences;
import allen.town.podcast.model.playback.Playable;

/**
 * Applies the per-feed "skip intro" and "skip ending" preferences for {@link PlaybackService}:
 * it seeks past the intro when an episode starts, skips to the next episode shortly before the
 * configured ending starts (telling the user about both with a snackbar), and remembers which
 * episode it auto-skipped so that post-playback processing can mark it as played even though the
 * user never reached its end.
 */
class PlaybackServiceAutoSkipper {
    private static final String TAG = "PlaybackService";

    private final PlaybackService service;

    private String autoSkippedFeedMediaId = null;

    PlaybackServiceAutoSkipper(PlaybackService service) {
        this.service = service;
    }

    void skipIntro(Playable playable) {
        if (!(playable instanceof FeedMedia)) {
            return;
        }

        FeedMedia feedMedia = (FeedMedia) playable;
        FeedPreferences preferences = feedMedia.getItem().getFeed().getPreferences();
        int skipIntro = preferences.getFeedSkipIntro();

        Context context = service.getApplicationContext();
        if (skipIntro > 0 && playable.getPosition() < skipIntro * 1000) {
            int duration = service.getDuration();
            if (skipIntro * 1000 < duration || duration <= 0) {
                Log.d(TAG, "skipIntro " + playable.getEpisodeTitle());
                service.mediaPlayer.seekTo(skipIntro * 1000);
                String skipIntroMesg = context.getString(R.string.pref_feed_skip_intro_toast,
                        skipIntro);
                TopSnackbarUtil.showSnack(context, skipIntroMesg,
                        Toast.LENGTH_LONG);
            }
        }
    }

    void skipEndingIfNecessary() {
        Playable playable = service.mediaPlayer.getPlayable();
        if (!(playable instanceof FeedMedia)) {
            return;
        }

        int duration = service.getDuration();
        int remainingTime = duration - service.getCurrentPosition();

        FeedMedia feedMedia = (FeedMedia) playable;
        FeedPreferences preferences = feedMedia.getItem().getFeed().getPreferences();
        int skipEnd = preferences.getFeedSkipEnding();
        if (skipEnd > 0
                && skipEnd * 1000 < service.getDuration()
                && (remainingTime - (skipEnd * 1000) > 0)
                && ((remainingTime - skipEnd * 1000) < (service.getCurrentPlaybackSpeed() * 1000))) {
            Log.d(TAG, "skipEndingIfNecessary: Skipping the remaining " + remainingTime + " "
                    + skipEnd * 1000 + " speed " + service.getCurrentPlaybackSpeed());
            Context context = service.getApplicationContext();
            String skipMesg = context.getString(R.string.pref_feed_skip_ending_toast, skipEnd);
            TopSnackbarUtil.showSnack(context, skipMesg, Toast.LENGTH_LONG);

            this.autoSkippedFeedMediaId = feedMedia.getItem().getIdentifyingValue();
            service.mediaPlayer.skip();
        }
    }

    /**
     * Records that the given episode was skipped by the app rather than by the user, so that
     * {@link #consumeAutoSkipped(FeedItem)} reports it and post-playback processing marks it as
     * played. Used by {@link PlaybackServiceAdSkipper} when an ad segment runs to the very end of
     * an episode and the only sensible skip is to the next one.
     */
    void markAutoSkipped(FeedItem item) {
        if (item != null) {
            this.autoSkippedFeedMediaId = item.getIdentifyingValue();
        }
    }

    /**
     * @return true if the given item is the one this class auto-skipped, clearing the marker so
     *         that it is only reported once.
     */
    boolean consumeAutoSkipped(FeedItem item) {
        if (autoSkippedFeedMediaId != null && autoSkippedFeedMediaId.equals(item.getIdentifyingValue())) {
            autoSkippedFeedMediaId = null;
            return true;
        }
        return false;
    }
}
