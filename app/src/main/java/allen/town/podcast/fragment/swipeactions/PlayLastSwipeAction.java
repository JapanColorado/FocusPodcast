package allen.town.podcast.fragment.swipeactions;

import android.content.Context;

import androidx.fragment.app.Fragment;

import allen.town.podcast.R;
import allen.town.podcast.menuprocess.QueuePlacementUi;
import allen.town.podcast.model.feed.FeedItem;
import allen.town.podcast.model.feed.FeedItemFilter;

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
