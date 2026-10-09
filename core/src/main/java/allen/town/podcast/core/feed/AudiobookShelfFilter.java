package allen.town.podcast.core.feed;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import allen.town.podcast.model.feed.AudiobookProgress;
import allen.town.podcast.model.feed.Feed;

/**
 * Sort order and filter of the Audiobooks page, applied in memory to the books and their progress.
 * Immutable; pure logic, no Android.
 *
 * <p>Sort directions: Recently played puts the book played most recently first, books never played
 * after all played ones (by title). Title and Author are A to Z (books without an author last).
 * Progress is most complete first (finished books, then the furthest along), so the books about to
 * be finished are at the top. Date added is newest first, which is the feed id descending: feeds
 * have no "added" timestamp, and ids grow with every subscription. Ties go by title.
 *
 * <p>Filters combine with AND. Status comes from {@link AudiobookProgress}; a book without tracks
 * counts as not started. Author is OR across the chosen authors, and is ignored when fewer than two
 * authors exist among the books (the page then hides the section) or none of the chosen ones is
 * among them any more, so a stale saved author never hides books invisibly. Length goes by the
 * total duration; a book whose duration is unknown matches no length.
 */
public final class AudiobookShelfFilter {

    public enum Sort { RECENTLY_PLAYED, TITLE, AUTHOR, PROGRESS, DATE_ADDED }

    public enum Status { NOT_STARTED, IN_PROGRESS, FINISHED }

    public enum Length { UNDER_5_HOURS, FROM_5_TO_15_HOURS, OVER_15_HOURS }

    private static final long HOUR_MS = 60L * 60L * 1000L;
    static final long SHORT_LIMIT_MS = 5 * HOUR_MS;
    static final long LONG_LIMIT_MS = 15 * HOUR_MS;

    @NonNull
    public final Sort sort;
    @Nullable
    public final Status status;
    @NonNull
    public final Set<String> authors;
    @Nullable
    public final Length length;

    public AudiobookShelfFilter(@Nullable Sort sort, @Nullable Status status,
                                @Nullable Collection<String> authors, @Nullable Length length) {
        this.sort = sort != null ? sort : Sort.RECENTLY_PLAYED;
        this.status = status;
        this.authors = authors != null
                ? Collections.unmodifiableSet(new LinkedHashSet<>(authors))
                : Collections.emptySet();
        this.length = length;
    }

    /** Recently played, nothing filtered. */
    @NonNull
    public static AudiobookShelfFilter defaults() {
        return new AudiobookShelfFilter(null, null, null, null);
    }

    /**
     * True when any filter (not the sort) has an effect on these books: a status or length, or
     * chosen authors that {@link #apply} would use.
     */
    public boolean isFiltering(@NonNull Collection<Feed> feeds) {
        return status != null || length != null || !effectiveAuthors(feeds).isEmpty();
    }

    /**
     * The chosen authors that are among the feeds; none when the feeds have fewer than two
     * authors, as the page offers no author choice then.
     */
    @NonNull
    Set<String> effectiveAuthors(@NonNull Collection<Feed> feeds) {
        Set<String> available = new HashSet<>(authorsOf(feeds));
        if (available.size() < 2) {
            return Collections.emptySet();
        }
        Set<String> chosen = new HashSet<>(authors);
        chosen.retainAll(available);
        return chosen;
    }

    @NonNull
    public static Status statusOf(@Nullable AudiobookProgress progress) {
        if (progress == null || !progress.isStarted()) {
            return Status.NOT_STARTED;
        }
        return progress.isFinished() ? Status.FINISHED : Status.IN_PROGRESS;
    }

    /** The length bucket of the book, null when its total duration is unknown. */
    @Nullable
    public static Length lengthOf(@Nullable AudiobookProgress progress) {
        long total = progress != null ? progress.getTotalDuration() : 0;
        if (total <= 0) {
            return null;
        } else if (total < SHORT_LIMIT_MS) {
            return Length.UNDER_5_HOURS;
        } else if (total <= LONG_LIMIT_MS) {
            return Length.FROM_5_TO_15_HOURS;
        }
        return Length.OVER_15_HOURS;
    }

    /** The author of the feed, trimmed; null when it has none. */
    @Nullable
    public static String authorOf(@NonNull Feed feed) {
        String author = feed.getAuthor();
        if (author == null || author.trim().isEmpty()) {
            return null;
        }
        return author.trim();
    }

    /** Distinct non-empty authors of the feeds, A to Z. */
    @NonNull
    public static List<String> authorsOf(@NonNull Collection<Feed> feeds) {
        Collator collator = collator();
        // authors differing only in case stay apart, each matches its own books
        Comparator<String> order = collator::compare;
        Set<String> result = new TreeSet<>(order.thenComparing(Comparator.naturalOrder()));
        for (Feed feed : feeds) {
            String author = authorOf(feed);
            if (author != null) {
                result.add(author);
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * Filters and sorts {@code books}; {@code feedOf} and {@code progressOf} read a book's feed and
     * progress (which may be null). Returns a new list.
     */
    @NonNull
    public <T> List<T> apply(@NonNull List<T> books, @NonNull Function<T, Feed> feedOf,
                             @NonNull Function<T, AudiobookProgress> progressOf) {
        List<Feed> feeds = new ArrayList<>();
        for (T book : books) {
            feeds.add(feedOf.apply(book));
        }
        Set<String> chosenAuthors = effectiveAuthors(feeds);
        boolean filterAuthors = !chosenAuthors.isEmpty();

        List<T> result = new ArrayList<>();
        for (T book : books) {
            Feed feed = feedOf.apply(book);
            AudiobookProgress progress = progressOf.apply(book);
            if (status != null && statusOf(progress) != status) {
                continue;
            }
            if (length != null && lengthOf(progress) != length) {
                continue;
            }
            if (filterAuthors && !chosenAuthors.contains(authorOf(feed))) {
                continue;
            }
            result.add(book);
        }
        result.sort(comparator(feedOf, progressOf));
        return result;
    }

    @NonNull
    private <T> Comparator<T> comparator(@NonNull Function<T, Feed> feedOf,
                                         @NonNull Function<T, AudiobookProgress> progressOf) {
        Collator collator = collator();
        Comparator<T> byTitle = (a, b) -> compareText(collator, feedOf.apply(a).getTitle(),
                feedOf.apply(b).getTitle());
        Comparator<T> tieBreak = byTitle.thenComparing(
                (a, b) -> Long.compare(feedOf.apply(a).getId(), feedOf.apply(b).getId()));
        switch (sort) {
            case TITLE:
                return tieBreak;
            case AUTHOR:
                Comparator<T> byAuthor = (a, b) -> compareText(collator, authorOf(feedOf.apply(a)),
                        authorOf(feedOf.apply(b)));
                return byAuthor.thenComparing(tieBreak);
            case PROGRESS:
                Comparator<T> byProgress = (a, b) -> Double.compare(
                        completion(progressOf.apply(b)), completion(progressOf.apply(a)));
                return byProgress.thenComparing(tieBreak);
            case DATE_ADDED:
                return (a, b) -> Long.compare(feedOf.apply(b).getId(), feedOf.apply(a).getId());
            case RECENTLY_PLAYED:
            default:
                Comparator<T> byLastPlayed = (a, b) -> Long.compare(
                        lastPlayed(progressOf.apply(b)), lastPlayed(progressOf.apply(a)));
                return byLastPlayed.thenComparing(tieBreak);
        }
    }

    /** 0 to 1; finished books are complete even when their duration is unknown. */
    private static double completion(@Nullable AudiobookProgress progress) {
        if (progress == null) {
            return 0;
        } else if (progress.isFinished()) {
            return 1;
        } else if (progress.getTotalDuration() <= 0) {
            return 0;
        }
        return (double) progress.getListenedDuration() / progress.getTotalDuration();
    }

    private static long lastPlayed(@Nullable AudiobookProgress progress) {
        return progress != null ? Math.max(0, progress.getLastPlayedTime()) : 0;
    }

    /** Null or empty sorts last. */
    private static int compareText(@NonNull Collator collator, @Nullable String a, @Nullable String b) {
        boolean aEmpty = a == null || a.isEmpty();
        boolean bEmpty = b == null || b.isEmpty();
        if (aEmpty || bEmpty) {
            return Boolean.compare(aEmpty, bEmpty);
        }
        return collator.compare(a, b);
    }

    @NonNull
    private static Collator collator() {
        Collator collator = Collator.getInstance(Locale.getDefault());
        collator.setStrength(Collator.SECONDARY);
        return collator;
    }
}
