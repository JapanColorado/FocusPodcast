package dev.russell.focuspodcast.fragment.actions;

import android.util.Log;
import android.widget.Toast;

import androidx.annotation.PluralsRes;

import java.util.ArrayList;
import java.util.List;

import dev.russell.focuspodcast.common.util.TopSnackbarUtil;
import dev.russell.focuspodcast.R;
import dev.russell.focuspodcast.activity.MainActivity;
import dev.russell.focuspodcast.core.service.download.DownloadRequest;
import dev.russell.focuspodcast.core.service.download.DownloadRequestCreator;
import dev.russell.focuspodcast.core.service.download.DownloadService;
import dev.russell.focuspodcast.core.storage.DBWriter;
import dev.russell.focuspodcast.core.util.LongList;
import dev.russell.focuspodcast.menuprocess.QueuePlacementUi;
import dev.russell.focuspodcast.model.feed.FeedItem;

public class EpisodeMultiSelectActionHandler {
    private static final String TAG = "EpisodeSelectHandler";
    private final MainActivity activity;
    private final List<FeedItem> selectedItems;

    public EpisodeMultiSelectActionHandler(MainActivity activity, List<FeedItem> selectedItems) {
        this.activity = activity;
        this.selectedItems = selectedItems;
    }

    public void handleAction(int id) {
        if (id == R.id.add_to_queue_batch) {
            queueChecked();
        } else if (id == R.id.play_next_batch) {
            QueuePlacementUi.place(activity, selectedItems, true);
        } else if (id == R.id.play_last_batch) {
            QueuePlacementUi.place(activity, selectedItems, false);
        } else if (id == R.id.remove_from_queue_batch) {
            removeFromQueueChecked();
        } else if (id == R.id.mark_read_batch) {
            markedCheckedPlayed();
        } else if (id == R.id.mark_unread_batch) {
            markedCheckedUnplayed();
        } else if (id == R.id.download_batch) {
            downloadChecked();
        } else if (id == R.id.delete_batch) {
            deleteChecked();
        } else if (id == R.id.add_to_favorites_item) {
            addToFav();
        }  else if (id == R.id.remove_from_favorites_item) {
            removeFromFav();
        } else {
            Log.e(TAG, "Unrecognized speed dial action item. Do nothing. id=" + id);
        }
    }

    private void queueChecked() {
        // Check if an episode actually contains any media files before adding it to queue
        LongList toQueue = new LongList(selectedItems.size());
        for (FeedItem episode : selectedItems) {
            if (episode.hasMedia()) {
                toQueue.add(episode.getId());
            }
        }
        DBWriter.addQueueItem(activity, true, toQueue.toArray());
        showMessage(R.plurals.added_to_queue_batch_label, toQueue.size());
    }

    private void removeFromQueueChecked() {
        long[] checkedIds = getSelectedIds();
        DBWriter.removeQueueItem(activity, true, checkedIds);
        showMessage(R.plurals.removed_from_queue_batch_label, checkedIds.length);
    }

    private void markedCheckedPlayed() {
        long[] checkedIds = getSelectedIds();
        DBWriter.markItemPlayed(FeedItem.PLAYED, checkedIds);
        showMessage(R.plurals.marked_read_batch_label, checkedIds.length);
    }

    private void markedCheckedUnplayed() {
        long[] checkedIds = getSelectedIds();
        DBWriter.markItemPlayed(FeedItem.UNPLAYED, checkedIds);
        showMessage(R.plurals.marked_unread_batch_label, checkedIds.length);
    }

    private void addToFav() {
        for (FeedItem feedItem : selectedItems) {
            DBWriter.addFavoriteItem(feedItem);
        }
        showMessage(R.plurals.added_to_favorites_batch_label, selectedItems.size());
    }

    private void removeFromFav() {
        for (FeedItem feedItem : selectedItems) {
            DBWriter.removeFavoriteItem(feedItem);
        }
        showMessage(R.plurals.removed_from_favorites_batch_label, selectedItems.size());
    }


    private void downloadChecked() {
        // download the check episodes in the same order as they are currently displayed.
        // Already-downloaded episodes are skipped: their complete file would be treated as a
        // partial one, and the Range request past its end gets it deleted and fetched again.
        List<DownloadRequest> requests = new ArrayList<>();
        for (FeedItem episode : selectedItems) {
            if (episode.hasMedia() && !episode.getMedia().isDownloaded()
                    && episode.getFeed() != null && !episode.getFeed().isLocalFeed()) {
                requests.add(DownloadRequestCreator.create(episode.getMedia()).build());
            }
        }
        if (!requests.isEmpty()) {
            DownloadService.download(activity, true, requests.toArray(new DownloadRequest[0]));
        }
        showMessage(R.plurals.downloading_batch_label, requests.size());
    }

    private void deleteChecked() {
        int countHasMedia = 0;
        for (FeedItem feedItem : selectedItems) {
            if (feedItem.hasMedia() && feedItem.getMedia().isDownloaded()) {
                countHasMedia++;
                DBWriter.deleteFeedMediaOfItem(activity, feedItem.getMedia().getId());
            }
        }
        showMessage(R.plurals.deleted_multi_episode_batch_label, countHasMedia);
    }

    private void showMessage(@PluralsRes int msgId, int numItems) {
        TopSnackbarUtil.showSnack(activity,activity.getResources()
                .getQuantityString(msgId, numItems, numItems), Toast.LENGTH_LONG);
    }

    private long[] getSelectedIds() {
        long[] checkedIds = new long[selectedItems.size()];
        for (int i = 0; i < selectedItems.size(); ++i) {
            checkedIds[i] = selectedItems.get(i).getId();
        }
        return checkedIds;
    }
}
