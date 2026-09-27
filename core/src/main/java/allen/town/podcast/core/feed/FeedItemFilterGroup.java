package allen.town.podcast.core.feed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import allen.town.podcast.core.R;
import allen.town.podcast.model.feed.FeedItemFilter;

public enum FeedItemFilterGroup {
    PLAYED(new ItemProperties(R.string.hide_played_episodes_label, FeedItemFilter.PLAYED),
            new ItemProperties(R.string.not_played, FeedItemFilter.UNPLAYED)),
    PAUSED(new ItemProperties(R.string.hide_paused_episodes_label, FeedItemFilter.PAUSED),
            new ItemProperties(R.string.not_paused, FeedItemFilter.NOT_PAUSED)),
    FAVORITE(new ItemProperties(R.string.hide_is_favorite_label, FeedItemFilter.IS_FAVORITE),
            new ItemProperties(R.string.not_favorite, FeedItemFilter.NOT_FAVORITE)),
    MEDIA(new ItemProperties(R.string.has_media, FeedItemFilter.HAS_MEDIA),
            new ItemProperties(R.string.no_media, FeedItemFilter.NO_MEDIA)),
    QUEUED(new ItemProperties(R.string.queued_label, FeedItemFilter.QUEUED),
            new ItemProperties(R.string.not_queued_label, FeedItemFilter.NOT_QUEUED)),
    DOWNLOADED(new ItemProperties(R.string.hide_downloaded_episodes_label, FeedItemFilter.DOWNLOADED),
            new ItemProperties(R.string.hide_not_downloaded_episodes_label, FeedItemFilter.NOT_DOWNLOADED)),
    TODAY(new ItemProperties(R.string.today, FeedItemFilter.TODAY),
            new ItemProperties(R.string.not_today, FeedItemFilter.NOT_TODAY));

    public final ItemProperties[] values;

    FeedItemFilterGroup(ItemProperties... values) {
        this.values = values;
    }

    /**
     * The display-name string resources of the given filter values, in the order the filter
     * dialog lists them. Values no group knows about are skipped.
     */
    public static List<Integer> displayNamesOf(String[] filterValues) {
        List<String> active = Arrays.asList(filterValues);
        List<Integer> names = new ArrayList<>();
        for (FeedItemFilterGroup group : values()) {
            for (ItemProperties properties : group.values) {
                if (active.contains(properties.filterId)) {
                    names.add(properties.displayName);
                }
            }
        }
        return names;
    }

    public static class ItemProperties {

        public final int displayName;
        public final String filterId;

        public ItemProperties(int displayName, String filterId) {
            this.displayName = displayName;
            this.filterId = filterId;
        }

    }
}
