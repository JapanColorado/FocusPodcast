package dev.russell.focuspodcast.fragment.swipeactions;

import android.content.Context;

import androidx.fragment.app.Fragment;

import dev.russell.focuspodcast.R;
import dev.russell.focuspodcast.menuprocess.FeedItemMenuProcess;
import dev.russell.focuspodcast.model.feed.FeedItem;
import dev.russell.focuspodcast.model.feed.FeedItemFilter;

public class MarkPlayedSwipeAction implements SwipeAction {

    @Override
    public String getId() {
        return MARK_PLAYED;
    }

    @Override
    public int getActionIcon() {
        return R.drawable.ic_mark_played;
    }

    @Override
    public int getActionColor() {
        return R.attr.icon_purple;
    }

    @Override
    public String getTitle(Context context) {
        return context.getString(R.string.mark_read_label);
    }

    @Override
    public void performAction(FeedItem item, Fragment fragment, FeedItemFilter filter) {
        int togglePlayState =
                item.getPlayState() != FeedItem.PLAYED  ? FeedItem.PLAYED : FeedItem.UNPLAYED;
        FeedItemMenuProcess.markReadWithUndo(fragment,
                item, togglePlayState, willRemove(filter));
    }

    @Override
    public boolean willRemove(FeedItemFilter filter) {
        return filter.showUnplayed || filter.showPlayed;
    }
}
