package dev.russell.focuspodcast.fragment.onlinesearch;

import android.util.Log;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import dev.russell.focuspodcast.adapter.ItunesAdapter;
import dev.russell.focuspodcast.discovery.ItunesEpisodesSearcher;
import dev.russell.focuspodcast.event.FeedItemEvent;
import dev.russell.focuspodcast.event.FeedListUpdateEvent;
import dev.russell.focuspodcast.event.PlayerStatusEvent;
import dev.russell.focuspodcast.event.UnreadItemsUpdateEvent;
import dev.russell.focuspodcast.event.playback.PlaybackPositionEvent;

public class OnlineSearchEpisodesFragment extends OnlineSearchFragmentBase {
    private static String TAG="OnlineSearchEpisodesFragment";
    public static OnlineSearchFragmentBase newInstance() {
        OnlineSearchFragmentBase fragment = new OnlineSearchEpisodesFragment();
        fragment.searchProvider = new ItunesEpisodesSearcher();
        return fragment;
    }

    @Override
    boolean isTypeEpisodes() {
        return true;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(FeedItemEvent event) {
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(PlaybackPositionEvent event) {
        if (adapter != null) {
            for (int i = 0; i < adapter.getItemCount(); i++) {
                ItunesAdapter.PodcastViewHolder holder = (ItunesAdapter.PodcastViewHolder) gridView.findViewHolderForAdapterPosition(i);
                if (holder != null && holder.isCurrentlyPlayingItem()) {
                    holder.notifyPlaybackPositionUpdated(event);
                    break;
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerStatusChanged(PlayerStatusEvent event) {
        adapter.notifyDataSetChanged();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUnreadItemsChanged(UnreadItemsUpdateEvent event) {
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onFeedListChanged(FeedListUpdateEvent event) {
    }

}
