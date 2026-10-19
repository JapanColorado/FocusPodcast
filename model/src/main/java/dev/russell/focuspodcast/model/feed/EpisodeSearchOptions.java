package dev.russell.focuspodcast.model.feed;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Filters, scope and sort order of the in-app episode search, on top of the words typed. Immutable;
 * the {@code with...} methods return changed copies. The database turns every part into SQL
 * (see {@code FeedItemDao.searchItems}); nothing is filtered in memory.
 *
 * <ul>
 *     <li>{@link #filter}: the episode filter (status, downloaded, favorite) as {@link FeedItemFilter} keys.</li>
 *     <li>{@link #feedId}: one podcast or audiobook, 0 for all of them.</li>
 *     <li>{@link #dateRange}: published within the last week / month (30 days) / year (365 days).</li>
 *     <li>{@link #durationRange}: by media duration; an episode whose duration is unknown matches no range.</li>
 *     <li>{@link #fields}: where each word is looked for. Every word must match in at least one of them.
 *     {@link Field#TRANSCRIPT} (the cached transcript text) is off unless chosen.</li>
 *     <li>{@link #sort}: {@link Sort#RELEVANCE} puts episodes whose title holds every word before the
 *     ones matched only elsewhere, newest first within each group.</li>
 * </ul>
 */
public final class EpisodeSearchOptions {

    private static final long MINUTE_MS = 60L * 1000L;
    private static final long DAY_MS = 24L * 60L * MINUTE_MS;

    /**
     * Where the words are looked for. {@link #TRANSCRIPT} only reaches episodes whose transcript
     * has been fetched (on download or when it was opened), so it is not searched by default.
     */
    public enum Field {
        TITLE, SHOW_NOTES, TRANSCRIPT;

        /** The fields searched when nothing was chosen. */
        @NonNull
        public static Set<Field> defaults() {
            return EnumSet.of(TITLE, SHOW_NOTES);
        }
    }

    public enum Sort { RELEVANCE, NEWEST, OLDEST, LONGEST, SHORTEST }

    public enum DateRange {
        ANY(0), PAST_WEEK(7), PAST_MONTH(30), PAST_YEAR(365);

        private final int days;

        DateRange(int days) {
            this.days = days;
        }

        /** The oldest publication time (ms) still in range, or 0 for {@link #ANY}. */
        public long earliestPubDate(long nowMillis) {
            return days == 0 ? 0 : nowMillis - days * DAY_MS;
        }
    }

    public enum DurationRange {
        ANY(0, 0), UNDER_20_MIN(1, 20 * MINUTE_MS - 1), FROM_20_TO_60_MIN(20 * MINUTE_MS, 60 * MINUTE_MS),
        OVER_60_MIN(60 * MINUTE_MS + 1, 0);

        /** Inclusive lower bound in ms (0 = none). */
        public final long minMillis;
        /** Inclusive upper bound in ms (0 = none). */
        public final long maxMillis;

        DurationRange(long minMillis, long maxMillis) {
            this.minMillis = minMillis;
            this.maxMillis = maxMillis;
        }
    }

    @NonNull
    public final FeedItemFilter filter;
    public final long feedId;
    @NonNull
    public final DateRange dateRange;
    @NonNull
    public final DurationRange durationRange;
    /** Never empty: an empty choice means {@link Field#defaults()}. */
    @NonNull
    public final Set<Field> fields;
    @NonNull
    public final Sort sort;

    public EpisodeSearchOptions(@Nullable FeedItemFilter filter, long feedId, @Nullable DateRange dateRange,
                                @Nullable DurationRange durationRange, @Nullable Collection<Field> fields,
                                @Nullable Sort sort) {
        this.filter = filter != null ? filter : FeedItemFilter.unfiltered();
        this.feedId = Math.max(feedId, 0);
        this.dateRange = dateRange != null ? dateRange : DateRange.ANY;
        this.durationRange = durationRange != null ? durationRange : DurationRange.ANY;
        this.fields = Collections.unmodifiableSet(fields == null || fields.isEmpty()
                ? Field.defaults() : EnumSet.copyOf(fields));
        this.sort = sort != null ? sort : Sort.RELEVANCE;
    }

    /** Everything, the default fields, by relevance. */
    @NonNull
    public static EpisodeSearchOptions defaults() {
        return new EpisodeSearchOptions(null, 0, null, null, null, null);
    }

    @NonNull
    public EpisodeSearchOptions withFilter(@Nullable FeedItemFilter newFilter) {
        return new EpisodeSearchOptions(newFilter, feedId, dateRange, durationRange, fields, sort);
    }

    @NonNull
    public EpisodeSearchOptions withFeedId(long newFeedId) {
        return new EpisodeSearchOptions(filter, newFeedId, dateRange, durationRange, fields, sort);
    }

    @NonNull
    public EpisodeSearchOptions withDateRange(@Nullable DateRange newDateRange) {
        return new EpisodeSearchOptions(filter, feedId, newDateRange, durationRange, fields, sort);
    }

    @NonNull
    public EpisodeSearchOptions withDurationRange(@Nullable DurationRange newDurationRange) {
        return new EpisodeSearchOptions(filter, feedId, dateRange, newDurationRange, fields, sort);
    }

    @NonNull
    public EpisodeSearchOptions withFields(@Nullable Collection<Field> newFields) {
        return new EpisodeSearchOptions(filter, feedId, dateRange, durationRange, newFields, sort);
    }

    @NonNull
    public EpisodeSearchOptions withSort(@Nullable Sort newSort) {
        return new EpisodeSearchOptions(filter, feedId, dateRange, durationRange, fields, newSort);
    }

    /**
     * True when anything other than the sort and the podcast scope narrows the results: an episode
     * filter, a date or duration range, or leaving out one of the default fields. Adding the
     * transcript only widens the search, so it does not count.
     */
    public boolean isFiltering() {
        return filter.getValues().length > 0 || dateRange != DateRange.ANY
                || durationRange != DurationRange.ANY || !fields.containsAll(Field.defaults());
    }
}
