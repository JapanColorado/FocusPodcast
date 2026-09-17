package allen.town.podcast.core.service.playback;

import android.content.ContentResolver;
import android.net.Uri;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.util.Log;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.media.MediaBrowserServiceCompat;

import java.util.ArrayList;
import java.util.List;

import allen.town.podcast.core.R;
import allen.town.podcast.core.storage.DBReader;
import allen.town.podcast.model.feed.Feed;
import allen.town.podcast.model.feed.FeedItem;
import allen.town.podcast.model.feed.FeedItemFilter;
import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * Answers the media browser tree that {@link PlaybackService} exposes to Android Auto and other
 * {@link MediaBrowserServiceCompat} clients: the top-level "queue / downloads / episodes" browsable
 * items plus one item per subscribed feed, and the episode lists behind them, loaded off the main
 * thread. The service keeps the {@code onGetRoot}/{@code onLoadChildren} overrides and forwards
 * the work here.
 */
class PlaybackServiceMediaBrowser {
    private static final String TAG = "PlaybackService";

    private final PlaybackService service;

    PlaybackServiceMediaBrowser(PlaybackService service) {
        this.service = service;
    }

    void onLoadChildren(@NonNull String parentId,
                        @NonNull MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> result) {
        result.detach();

        service.addServiceDisposable(Completable.create(emitter -> {
            result.sendResult(loadChildrenSynchronous(parentId));
            emitter.onComplete();
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                    }, e -> {
                        Log.e(TAG, "Failed to load media browser children", e);
                        result.sendResult(null);
                    }));
    }

    private MediaBrowserCompat.MediaItem createBrowsableMediaItem(
            @StringRes int title, @DrawableRes int icon, int numEpisodes) {
        Uri uri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(service.getResources().getResourcePackageName(icon))
                .appendPath(service.getResources().getResourceTypeName(icon))
                .appendPath(service.getResources().getResourceEntryName(icon))
                .build();

        MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                .setIconUri(uri)
                .setMediaId(service.getResources().getString(title))
                .setTitle(service.getResources().getString(title))
                .setSubtitle(service.getResources().getQuantityString(R.plurals.num_episodes,
                        numEpisodes, numEpisodes))
                .build();
        return new MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
    }

    private MediaBrowserCompat.MediaItem createBrowsableMediaItemForFeed(Feed feed) {
        MediaDescriptionCompat.Builder builder = new MediaDescriptionCompat.Builder()
                .setMediaId("FeedId:" + feed.getId())
                .setTitle(feed.getTitle())
                .setDescription(feed.getDescription())
                .setSubtitle(feed.getCustomTitle());
        if (feed.getImageUrl() != null) {
            builder.setIconUri(Uri.parse(feed.getImageUrl()));
        }
        if (feed.getLink() != null) {
            builder.setMediaUri(Uri.parse(feed.getLink()));
        }
        MediaDescriptionCompat description = builder.build();
        return new MediaBrowserCompat.MediaItem(description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
    }

    private List<MediaBrowserCompat.MediaItem> loadChildrenSynchronous(@NonNull String parentId)
            throws InterruptedException {
        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
        if (parentId.equals(service.getResources().getString(R.string.app_name))) {
            mediaItems.add(createBrowsableMediaItem(R.string.playlist_label, R.drawable.ic_playlist,
                    DBReader.getQueue().size()));
            mediaItems.add(createBrowsableMediaItem(R.string.downloads_label, R.drawable.ic_download,
                    DBReader.getDownloadedItems().size()));
            mediaItems.add(createBrowsableMediaItem(R.string.episodes_label, R.drawable.ic_episodes,
                    DBReader.getTotalEpisodeCount(new FeedItemFilter(FeedItemFilter.UNPLAYED))));
            List<Feed> feeds = DBReader.getFeedList();
            for (Feed feed : feeds) {
                mediaItems.add(createBrowsableMediaItemForFeed(feed));
            }
            return mediaItems;
        }

        List<FeedItem> feedItems;
        if (parentId.equals(service.getResources().getString(R.string.playlist_label))) {
            feedItems = DBReader.getQueue();
        } else if (parentId.equals(service.getResources().getString(R.string.downloads_label))) {
            feedItems = DBReader.getDownloadedItems();
        } else if (parentId.equals(service.getResources().getString(R.string.episodes_label))) {
            feedItems = DBReader.getRecentlyPublishedEpisodes(0,
                    PlaybackService.MAX_ANDROID_AUTO_EPISODES_PER_FEED,
                    new FeedItemFilter(FeedItemFilter.UNPLAYED));
        } else if (parentId.startsWith("FeedId:")) {
            long feedId = Long.parseLong(parentId.split(":")[1]);
            feedItems = DBReader.getFeedItemList(DBReader.getFeed(feedId));
        } else {
            Log.e(TAG, "Parent ID not found: " + parentId);
            return null;
        }
        int count = 0;
        for (FeedItem feedItem : feedItems) {
            if (feedItem.getMedia() != null && feedItem.getMedia().getMediaItem() != null) {
                mediaItems.add(feedItem.getMedia().getMediaItem());
                if (++count >= PlaybackService.MAX_ANDROID_AUTO_EPISODES_PER_FEED) {
                    break;
                }
            }
        }
        return mediaItems;
    }
}
