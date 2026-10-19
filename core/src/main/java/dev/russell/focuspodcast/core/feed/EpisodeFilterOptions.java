package dev.russell.focuspodcast.core.feed;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.russell.focuspodcast.model.feed.FeedItemFilter;

/**
 * The choices the episode filter sheet offers (All Episodes and a podcast's episode list),
 * translated to and from {@link FeedItemFilter} keys. Pure logic, no Android.
 *
 * <p>Status is Unfinished ({@code unplayed}: not marked played, whether started or not) or
 * Finished ({@code played}). Downloaded and Favorite map onto their key pairs one to one.
 *
 * <p>Filters saved before the sheet existed may hold keys it no longer offers (today, has media,
 * queued, new) or the paused keys of the old Unplayed / In progress split. {@link #parse} reads them
 * leniently (played wins; unplayed, paused or not_paused mean Unfinished) and drops the rest, so
 * {@link #sanitize} never leaves a key in effect that the sheet cannot show.
 */
public final class EpisodeFilterOptions {

    public enum Status { UNFINISHED, FINISHED }

    @Nullable
    public final Status status;
    /** TRUE = downloaded only, FALSE = not downloaded only, null = either. */
    @Nullable
    public final Boolean downloaded;
    /** TRUE = favorites only, FALSE = non-favorites only, null = either. */
    @Nullable
    public final Boolean favorite;

    public EpisodeFilterOptions(@Nullable Status status, @Nullable Boolean downloaded,
                                @Nullable Boolean favorite) {
        this.status = status;
        this.downloaded = downloaded;
        this.favorite = favorite;
    }

    @NonNull
    public static EpisodeFilterOptions parse(@Nullable String[] values) {
        List<String> keys = values != null ? Arrays.asList(values) : new ArrayList<>();
        Status status = null;
        if (keys.contains(FeedItemFilter.PLAYED)) {
            status = Status.FINISHED;
        } else if (keys.contains(FeedItemFilter.UNPLAYED) || keys.contains(FeedItemFilter.PAUSED)
                || keys.contains(FeedItemFilter.NOT_PAUSED)) {
            status = Status.UNFINISHED;
        }
        Boolean downloaded = null;
        if (keys.contains(FeedItemFilter.DOWNLOADED)) {
            downloaded = Boolean.TRUE;
        } else if (keys.contains(FeedItemFilter.NOT_DOWNLOADED)) {
            downloaded = Boolean.FALSE;
        }
        Boolean favorite = null;
        if (keys.contains(FeedItemFilter.IS_FAVORITE)) {
            favorite = Boolean.TRUE;
        } else if (keys.contains(FeedItemFilter.NOT_FAVORITE)) {
            favorite = Boolean.FALSE;
        }
        return new EpisodeFilterOptions(status, downloaded, favorite);
    }

    /** The {@link FeedItemFilter} keys of these choices; empty when nothing is chosen. */
    @NonNull
    public String[] toValues() {
        List<String> keys = new ArrayList<>();
        if (status == Status.UNFINISHED) {
            keys.add(FeedItemFilter.UNPLAYED);
        } else if (status == Status.FINISHED) {
            keys.add(FeedItemFilter.PLAYED);
        }
        if (downloaded != null) {
            keys.add(downloaded ? FeedItemFilter.DOWNLOADED : FeedItemFilter.NOT_DOWNLOADED);
        }
        if (favorite != null) {
            keys.add(favorite ? FeedItemFilter.IS_FAVORITE : FeedItemFilter.NOT_FAVORITE);
        }
        return keys.toArray(new String[0]);
    }

    @NonNull
    public FeedItemFilter toFilter() {
        return new FeedItemFilter(toValues());
    }

    public boolean isEmpty() {
        return status == null && downloaded == null && favorite == null;
    }

    /** Rewrites saved keys into the canonical keys of what the sheet shows for them. */
    @NonNull
    public static String[] sanitize(@Nullable String[] values) {
        return parse(values).toValues();
    }

    @NonNull
    public static FeedItemFilter sanitize(@Nullable FeedItemFilter filter) {
        return new FeedItemFilter(sanitize(filter != null ? filter.getValues() : null));
    }
}
