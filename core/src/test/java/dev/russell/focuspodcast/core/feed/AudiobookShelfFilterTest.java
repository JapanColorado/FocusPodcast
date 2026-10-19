package dev.russell.focuspodcast.core.feed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import dev.russell.focuspodcast.core.feed.AudiobookShelfFilter.Length;
import dev.russell.focuspodcast.core.feed.AudiobookShelfFilter.Sort;
import dev.russell.focuspodcast.core.feed.AudiobookShelfFilter.Status;
import dev.russell.focuspodcast.model.feed.AudiobookProgress;
import dev.russell.focuspodcast.model.feed.Feed;

/** Sorting and filtering of the Audiobooks page. Robolectric, as {@link Feed#getTitle} needs TextUtils. */
@RunWith(RobolectricTestRunner.class)
public class AudiobookShelfFilterTest {

    private static final long HOUR = 60L * 60L * 1000L;

    /** A book as the page holds it: the feed and its progress (null = no tracks). */
    private static final class Book {
        final Feed feed;
        @Nullable
        final AudiobookProgress progress;

        Book(Feed feed, @Nullable AudiobookProgress progress) {
            this.feed = feed;
            this.progress = progress;
        }
    }

    private static Feed feed(long id, String title, @Nullable String author) {
        Feed feed = new Feed("local:" + id, null, title);
        feed.setId(id);
        feed.setAuthor(author);
        return feed;
    }

    /** tracks/played tracks, total and listened in ms, last played time. */
    private static AudiobookProgress progress(long feedId, int tracks, int played, long total,
                                              long listened, long lastPlayed) {
        return new AudiobookProgress(feedId, tracks, played, total, listened, null, lastPlayed);
    }

    private static Book notStarted(long id, String title, String author, long total) {
        return new Book(feed(id, title, author), progress(id, 3, 0, total, 0, 0));
    }

    private static Book inProgress(long id, String title, String author, long total, long listened,
                                   long lastPlayed) {
        return new Book(feed(id, title, author), progress(id, 3, 1, total, listened, lastPlayed));
    }

    private static Book finished(long id, String title, String author, long total, long lastPlayed) {
        return new Book(feed(id, title, author), progress(id, 3, 3, total, total, lastPlayed));
    }

    private static List<String> titles(AudiobookShelfFilter filter, List<Book> books) {
        List<String> titles = new ArrayList<>();
        for (Book book : filter.apply(books, b -> b.feed, b -> b.progress)) {
            titles.add(book.feed.getTitle());
        }
        return titles;
    }

    private static AudiobookShelfFilter sortedBy(Sort sort) {
        return new AudiobookShelfFilter(sort, null, null, null);
    }

    @Test
    public void statusComesFromTheProgress() {
        assertEquals(Status.UNFINISHED, AudiobookShelfFilter.statusOf(null));
        assertEquals(Status.UNFINISHED, AudiobookShelfFilter.statusOf(notStarted(1, "A", null, HOUR).progress));
        assertEquals(Status.UNFINISHED,
                AudiobookShelfFilter.statusOf(inProgress(1, "A", null, HOUR, 10, 5).progress));
        assertEquals(Status.FINISHED, AudiobookShelfFilter.statusOf(finished(1, "A", null, HOUR, 5).progress));
        assertEquals(Status.UNFINISHED,
                AudiobookShelfFilter.statusOf(progress(1, 2, 0, 0, 0, 7)));
    }

    @Test
    public void statusFilterKeepsOnlyThatStatus() {
        List<Book> books = Arrays.asList(
                notStarted(1, "New", null, HOUR),
                inProgress(2, "Reading", null, HOUR, 100, 10),
                finished(3, "Done", null, HOUR, 20),
                new Book(feed(4, "Empty", null), null));
        assertEquals(Arrays.asList("Empty", "New", "Reading"),
                titles(new AudiobookShelfFilter(Sort.TITLE, Status.UNFINISHED, null, null), books));
        assertEquals(Collections.singletonList("Done"),
                titles(new AudiobookShelfFilter(Sort.TITLE, Status.FINISHED, null, null), books));
    }

    @Test
    public void lengthBucketsByTotalDuration() {
        assertEquals(Length.UNDER_5_HOURS, AudiobookShelfFilter.lengthOf(notStarted(1, "A", null, 5 * HOUR - 1).progress));
        assertEquals(Length.FROM_5_TO_15_HOURS, AudiobookShelfFilter.lengthOf(notStarted(1, "A", null, 5 * HOUR).progress));
        assertEquals(Length.FROM_5_TO_15_HOURS, AudiobookShelfFilter.lengthOf(notStarted(1, "A", null, 15 * HOUR).progress));
        assertEquals(Length.OVER_15_HOURS, AudiobookShelfFilter.lengthOf(notStarted(1, "A", null, 15 * HOUR + 1).progress));
    }

    @Test
    public void unknownDurationMatchesNoLength() {
        assertNull(AudiobookShelfFilter.lengthOf(null));
        assertNull(AudiobookShelfFilter.lengthOf(notStarted(1, "A", null, 0).progress));
        List<Book> books = Arrays.asList(
                notStarted(1, "Unknown", null, 0),
                new Book(feed(2, "No tracks", null), null),
                notStarted(3, "Short", null, 2 * HOUR));
        for (Length length : Length.values()) {
            List<String> shown = titles(new AudiobookShelfFilter(Sort.TITLE, null, null, length), books);
            assertFalse(shown.contains("Unknown"));
            assertFalse(shown.contains("No tracks"));
        }
        assertEquals(Collections.singletonList("Short"),
                titles(new AudiobookShelfFilter(Sort.TITLE, null, null, Length.UNDER_5_HOURS), books));
    }

    @Test
    public void authorFilterIsAnOrOfTheChosenAuthors() {
        List<Book> books = Arrays.asList(
                notStarted(1, "A1", "Austen", HOUR),
                notStarted(2, "B1", "Brontë", HOUR),
                notStarted(3, "C1", "Christie", HOUR),
                notStarted(4, "Anon", null, HOUR),
                notStarted(5, "A2", " Austen ", HOUR));
        AudiobookShelfFilter filter = new AudiobookShelfFilter(Sort.TITLE, null,
                Arrays.asList("Austen", "Christie"), null);
        assertEquals(Arrays.asList("A1", "A2", "C1"), titles(filter, books));
        assertTrue(filter.isFiltering(feeds(books)));
    }

    @Test
    public void authorsAreDistinctTrimmedAndSorted() {
        List<Book> books = Arrays.asList(
                notStarted(1, "1", "Zola", HOUR),
                notStarted(2, "2", " austen", HOUR),
                notStarted(3, "3", "", HOUR),
                notStarted(4, "4", null, HOUR),
                notStarted(5, "5", "Zola", HOUR));
        assertEquals(Arrays.asList("austen", "Zola"), AudiobookShelfFilter.authorsOf(feeds(books)));
    }

    @Test
    public void authorFilterIsIgnoredWithFewerThanTwoAuthorsOrStaleChoices() {
        List<Book> oneAuthor = Arrays.asList(
                notStarted(1, "A", "Austen", HOUR),
                notStarted(2, "B", null, HOUR));
        AudiobookShelfFilter austen = new AudiobookShelfFilter(Sort.TITLE, null,
                Collections.singletonList("Austen"), null);
        assertEquals(Arrays.asList("A", "B"), titles(austen, oneAuthor));
        assertFalse(austen.isFiltering(feeds(oneAuthor)));

        List<Book> twoAuthors = Arrays.asList(
                notStarted(1, "A", "Brontë", HOUR),
                notStarted(2, "B", "Christie", HOUR));
        AudiobookShelfFilter gone = new AudiobookShelfFilter(Sort.TITLE, null,
                Collections.singletonList("Austen"), null);
        assertEquals(Arrays.asList("A", "B"), titles(gone, twoAuthors));
        assertFalse(gone.isFiltering(feeds(twoAuthors)));
    }

    @Test
    public void recentlyPlayedPutsNeverPlayedBooksLastByTitle() {
        List<Book> books = Arrays.asList(
                notStarted(1, "Zeta", null, HOUR),
                inProgress(2, "Old", null, HOUR, 10, 100),
                notStarted(3, "Alpha", null, HOUR),
                finished(4, "Newest", null, HOUR, 300),
                inProgress(5, "Middle", null, HOUR, 10, 200),
                new Book(feed(6, "Beta", null), null));
        assertEquals(Arrays.asList("Newest", "Middle", "Old", "Alpha", "Beta", "Zeta"),
                titles(AudiobookShelfFilter.defaults(), books));
    }

    @Test
    public void titleAndAuthorSortAToZ() {
        List<Book> books = Arrays.asList(
                notStarted(1, "banana", "Christie", HOUR),
                notStarted(2, "Apple", null, HOUR),
                notStarted(3, "cherry", "austen", HOUR),
                notStarted(4, "Date", "Christie", HOUR));
        assertEquals(Arrays.asList("Apple", "banana", "cherry", "Date"), titles(sortedBy(Sort.TITLE), books));
        assertEquals(Arrays.asList("cherry", "banana", "Date", "Apple"), titles(sortedBy(Sort.AUTHOR), books));
    }

    @Test
    public void progressSortsMostCompleteFirst() {
        List<Book> books = Arrays.asList(
                notStarted(1, "Fresh", null, HOUR),
                inProgress(2, "Quarter", null, 100, 25, 1),
                finished(3, "Done", null, 0, 1),
                inProgress(4, "Most", null, 100, 90, 1),
                new Book(feed(5, "Empty", null), null));
        assertEquals(Arrays.asList("Done", "Most", "Quarter", "Empty", "Fresh"),
                titles(sortedBy(Sort.PROGRESS), books));
    }

    @Test
    public void dateAddedIsNewestFeedFirst() {
        List<Book> books = Arrays.asList(
                notStarted(5, "Middle", null, HOUR),
                notStarted(9, "Newest", null, HOUR),
                notStarted(2, "Oldest", null, HOUR));
        assertEquals(Arrays.asList("Newest", "Middle", "Oldest"), titles(sortedBy(Sort.DATE_ADDED), books));
    }

    @Test
    public void filtersCombine() {
        List<Book> books = Arrays.asList(
                inProgress(1, "Long Austen", "Austen", 20 * HOUR, 10, 1),
                inProgress(2, "Short Austen", "Austen", HOUR, 10, 1),
                inProgress(3, "Short Christie", "Christie", HOUR, 10, 1),
                notStarted(4, "Short new Austen", "Austen", HOUR),
                finished(5, "Short done Austen", "Austen", HOUR, 1));
        AudiobookShelfFilter filter = new AudiobookShelfFilter(Sort.TITLE, Status.UNFINISHED,
                Collections.singletonList("Austen"), Length.UNDER_5_HOURS);
        assertEquals(Arrays.asList("Short Austen", "Short new Austen"), titles(filter, books));
    }

    @Test
    public void defaultsSortByRecentlyPlayedAndDoNotFilter() {
        AudiobookShelfFilter defaults = AudiobookShelfFilter.defaults();
        assertEquals(Sort.RECENTLY_PLAYED, defaults.sort);
        assertFalse(defaults.isFiltering(Collections.emptyList()));
        assertEquals(Sort.RECENTLY_PLAYED, new AudiobookShelfFilter(null, null, null, null).sort);
    }

    private static List<Feed> feeds(List<Book> books) {
        List<Feed> feeds = new ArrayList<>();
        for (Book book : books) {
            feeds.add(book.feed);
        }
        return feeds;
    }
}
