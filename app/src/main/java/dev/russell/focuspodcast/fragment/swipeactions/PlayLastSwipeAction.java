package dev.russell.focuspodcast.fragment.swipeactions;

import android.content.Context;

import androidx.fragment.app.Fragment;

import dev.russell.focuspodcast.R;
import dev.russell.focuspodcast.menuprocess.QueuePlacementUi;
import dev.russell.focuspodcast.model.feed.FeedItem;
import dev.russell.focuspodcast.model.feed.FeedItemFilter;

public class PlayLastSwipeAction implements SwipeAction {

    @Override
    public String getId() {
        return PLAY_LAST;
    }

    @Override
    public int getActionIcon() {
        return R.drawable.ic_round_play_last_24;
    }

    @Override
    public int getActionColor() {
        return R.attr.icon_blue;
    }

    @Override
    public String getTitle(Context context) {
        return context.getString(R.string.play_last_label);
    }

    @Override
    public void performAction(FeedItem item, Fragment fragment, FeedItemFilter filter) {
        if (QueuePlacementUi.canPlace(item)) {
            QueuePlacementUi.playLast(fragment.requireActivity(), item);
        }
    }

    @Override
    public boolean willRemove(FeedItemFilter filter) {
        // the episode becomes queued, and a new one is marked unplayed
        return filter.showNotQueued || filter.showNew;
    }
}
