package dev.russell.focuspodcast.fragment.swipeactions;

import android.content.Context;

import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.fragment.app.Fragment;

import dev.russell.focuspodcast.model.feed.FeedItem;
import dev.russell.focuspodcast.model.feed.FeedItemFilter;

public interface SwipeAction {

    String ADD_TO_QUEUE = "ADD_TO_QUEUE";
    String PLAY_NEXT = "PLAY_NEXT";
    String PLAY_LAST = "PLAY_LAST";
    String START_DOWNLOAD = "START_DOWNLOAD";
    String MARK_FAV = "MARK_FAV";
    String MARK_PLAYED = "MARK_PLAYED";
    String REMOVE_FROM_QUEUE = "REMOVE_FROM_QUEUE";
    String DELETE = "DELETE";

    String getId();

    String getTitle(Context context);

    @DrawableRes
    int getActionIcon();

    @AttrRes
    int getActionColor();

    void performAction(FeedItem item, Fragment fragment, FeedItemFilter filter);

    boolean willRemove(FeedItemFilter filter);
}
