package allen.town.podcast.core.feed;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import allen.town.podcast.core.feed.EpisodeFilterOptions.Status;
import allen.town.podcast.model.feed.FeedItemFilter;

/** The episode filter sheet's choices and the {@link FeedItemFilter} keys they map to. Plain JUnit. */
public class EpisodeFilterOptionsTest {

    private static String[] keys(String... keys) {
        return keys;
    }

    @Test
    public void unplayedMeansNotPlayedAndNotStarted() {
        String[] values = new EpisodeFilterOptions(Status.UNPLAYED, null, null).toValues();
        assertArrayEquals(keys(FeedItemFilter.UNPLAYED, FeedItemFilter.NOT_PAUSED), values);
        FeedItemFilter filter = new FeedItemFilter(values);
        assertTrue(filter.showUnplayed);
        assertTrue(filter.showNotPaused);
        assertFalse(filter.showPaused);
    }

    @Test
    public void inProgressMeansStartedButNotPlayed() {
        String[] values = new EpisodeFilterOptions(Status.IN_PROGRESS, null, null).toValues();
        assertArrayEquals(keys(FeedItemFilter.UNPLAYED, FeedItemFilter.PAUSED), values);
    }

    @Test
    public void playedMapsToPlayed() {
        assertArrayEquals(keys(FeedItemFilter.PLAYED),
                new EpisodeFilterOptions(Status.PLAYED, null, null).toValues());
    }

    @Test
    public void downloadedAndFavoriteMapOneToOne() {
        assertArrayEquals(keys(FeedItemFilter.DOWNLOADED, FeedItemFilter.NOT_FAVORITE),
                new EpisodeFilterOptions(null, true, false).toValues());
        assertArrayEquals(keys(FeedItemFilter.NOT_DOWNLOADED, FeedItemFilter.IS_FAVORITE),
                new EpisodeFilterOptions(null, false, true).toValues());
    }

    @Test
    public void nothingChosenIsNoKeys() {
        EpisodeFilterOptions none = new EpisodeFilterOptions(null, null, null);
        assertTrue(none.isEmpty());
        assertEquals(0, none.toValues().length);
        assertTrue(EpisodeFilterOptions.parse(null).isEmpty());
    }

    @Test
    public void everyChoiceSurvivesTheRoundTrip() {
        Boolean[] tristate = {null, Boolean.TRUE, Boolean.FALSE};
        Status[] statuses = {null, Status.UNPLAYED, Status.IN_PROGRESS, Status.PLAYED};
        for (Status status : statuses) {
            for (Boolean downloaded : tristate) {
                for (Boolean favorite : tristate) {
                    EpisodeFilterOptions options = new EpisodeFilterOptions(status, downloaded, favorite);
                    EpisodeFilterOptions parsed = EpisodeFilterOptions.parse(options.toValues());
                    assertEquals(status, parsed.status);
                    assertEquals(downloaded, parsed.downloaded);
                    assertEquals(favorite, parsed.favorite);
                }
            }
        }
    }

    @Test
    public void droppedKeysAreSanitizedAway() {
        String[] saved = keys(FeedItemFilter.TODAY, FeedItemFilter.HAS_MEDIA, FeedItemFilter.NOT_QUEUED,
                FeedItemFilter.NEW, FeedItemFilter.NOT_TODAY, FeedItemFilter.NO_MEDIA, FeedItemFilter.QUEUED);
        assertEquals(0, EpisodeFilterOptions.sanitize(saved).length);

        String[] mixed = keys(FeedItemFilter.QUEUED, FeedItemFilter.DOWNLOADED, FeedItemFilter.TODAY);
        assertArrayEquals(keys(FeedItemFilter.DOWNLOADED), EpisodeFilterOptions.sanitize(mixed));
    }

    @Test
    public void oldStatusCombinationsMapToTheClosestChoice() {
        // the old dialog stored the played and paused pairs separately
        assertEquals(Status.UNPLAYED, EpisodeFilterOptions.parse(keys(FeedItemFilter.UNPLAYED)).status);
        assertEquals(Status.UNPLAYED, EpisodeFilterOptions.parse(keys(FeedItemFilter.NOT_PAUSED)).status);
        assertEquals(Status.IN_PROGRESS, EpisodeFilterOptions.parse(keys(FeedItemFilter.PAUSED)).status);
        assertEquals(Status.PLAYED,
                EpisodeFilterOptions.parse(keys(FeedItemFilter.PLAYED, FeedItemFilter.PAUSED)).status);
        assertArrayEquals(keys(FeedItemFilter.UNPLAYED, FeedItemFilter.PAUSED),
                EpisodeFilterOptions.sanitize(keys(FeedItemFilter.PAUSED, FeedItemFilter.HAS_MEDIA)));
    }

    @Test
    public void conflictingPairsKeepThePositiveKey() {
        EpisodeFilterOptions parsed = EpisodeFilterOptions.parse(keys(FeedItemFilter.DOWNLOADED,
                FeedItemFilter.NOT_DOWNLOADED, FeedItemFilter.IS_FAVORITE, FeedItemFilter.NOT_FAVORITE));
        assertEquals(Boolean.TRUE, parsed.downloaded);
        assertEquals(Boolean.TRUE, parsed.favorite);
    }

    @Test
    public void sanitizingANullFilterGivesAnEmptyOne() {
        assertEquals(0, EpisodeFilterOptions.sanitize((FeedItemFilter) null).getValues().length);
        assertNull(EpisodeFilterOptions.parse(new String[0]).status);
    }
}
