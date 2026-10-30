package dev.russell.yagi.menuprocess;

import android.app.Activity;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import dev.russell.yagi.R;
import dev.russell.yagi.activity.MainActivity;
import dev.russell.yagi.common.util.TopSnackbarUtil;
import dev.russell.yagi.core.pref.PlaybackPreferences;
import dev.russell.yagi.core.storage.DBWriter;
import dev.russell.yagi.core.util.LongList;
import dev.russell.yagi.model.feed.FeedItem;
import dev.russell.yagi.model.feed.FeedMedia;

/**
 * The UI side of "Play next" / "Play last": runs {@link DBWriter#playNext} or
 * {@link DBWriter#playLast} and confirms it with a short message.
 *
 * <p>When the queue is kept sorted ({@link DBWriter#isQueueOrderAutomatic()}) the sort decides the
 * position, so the message is the plain "Added to playlist" instead of claiming a position the
 * episode did not get.</p>
 */
public final class QueuePlacementUi {

    private QueuePlacementUi() {
    }

    /**
     * Whether "Play next" / "Play last" can do anything for this episode: it needs media, must not
     * be the episode in the player, and an already queued episode cannot be moved in a sorted queue.
     */
    public static boolean canPlace(@NonNull FeedItem item) {
        FeedMedia media = item.getMedia();
        if (media == null || isCurrent(media)) {
            return false;
        }
        return !(item.isTagged(FeedItem.TAG_QUEUE) && DBWriter.isQueueOrderAutomatic());
    }

    public static void playNext(@NonNull Activity activity, @NonNull FeedItem item) {
        DBWriter.playNext(activity, false, item.getId());
        showSingle(activity, true);
    }

    public static void playLast(@NonNull Activity activity, @NonNull FeedItem item) {
        DBWriter.playLast(activity, false, item.getId());
        showSingle(activity, false);
    }

    /** Batch version for multi-select; the items keep the order of the list. */
    public static void place(@NonNull Activity activity, @NonNull List<FeedItem> items, boolean next) {
        LongList ids = new LongList(items.size());
        for (FeedItem item : items) {
            FeedMedia media = item.getMedia();
            if (media != null && !isCurrent(media)) {
                ids.add(item.getId());
            }
        }
        if (next) {
            DBWriter.playNext(activity, true, ids.toArray());
        } else {
            DBWriter.playLast(activity, true, ids.toArray());
        }
        int count = ids.size();
        int pluralRes;
        if (DBWriter.isQueueOrderAutomatic()) {
            pluralRes = R.plurals.added_to_queue_batch_label;
        } else {
            pluralRes = next ? R.plurals.play_next_batch_label : R.plurals.play_last_batch_label;
        }
        TopSnackbarUtil.showSnack(activity,
                activity.getResources().getQuantityString(pluralRes, count, count), Toast.LENGTH_LONG);
    }

    private static boolean isCurrent(@NonNull FeedMedia media) {
        return PlaybackPreferences.getCurrentlyPlayingFeedMediaId() == media.getId();
    }

    private static void showSingle(@Nullable Activity activity, boolean next) {
        if (activity == null) {
            return;
        }
        int textRes;
        if (DBWriter.isQueueOrderAutomatic()) {
            textRes = R.string.added_to_queue_label;
        } else {
            textRes = next ? R.string.playing_next_label : R.string.added_to_end_of_queue_label;
        }
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).showSnackbarAbovePlayer(textRes, Snackbar.LENGTH_SHORT);
        } else {
            TopSnackbarUtil.showSnack(activity, textRes, Toast.LENGTH_SHORT);
        }
    }
}
