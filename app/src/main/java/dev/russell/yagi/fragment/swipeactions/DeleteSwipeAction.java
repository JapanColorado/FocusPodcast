package dev.russell.yagi.fragment.swipeactions;

import android.content.Context;

import androidx.fragment.app.Fragment;

import dev.russell.yagi.R;
import dev.russell.yagi.core.storage.DBWriter;
import dev.russell.yagi.model.feed.FeedItem;
import dev.russell.yagi.model.feed.FeedItemFilter;

public class DeleteSwipeAction implements SwipeAction {

    @Override
    public String getId() {
        return DELETE;
    }

    @Override
    public int getActionIcon() {
        return R.drawable.ic_delete;
    }

    @Override
    public int getActionColor() {
        return R.attr.icon_red;
    }

    @Override
    public String getTitle(Context context) {
        return context.getString(R.string.delete_episode_label);
    }

    @Override
    public void performAction(FeedItem item, Fragment fragment, FeedItemFilter filter) {
        if (!item.isDownloaded()) {
            return;
        }
        DBWriter.deleteFeedMediaOfItem(fragment.requireContext(), item.getMedia().getId());
    }

    @Override
    public boolean willRemove(FeedItemFilter filter) {
        return filter.showDownloaded;
    }
}
