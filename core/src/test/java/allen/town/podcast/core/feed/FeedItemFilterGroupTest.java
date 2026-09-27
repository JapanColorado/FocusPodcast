package allen.town.podcast.core.feed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

import allen.town.podcast.core.R;
import allen.town.podcast.model.feed.FeedItemFilter;

public class FeedItemFilterGroupTest {

    @Test
    public void displayNamesFollowTheDialogOrder() {
        String[] values = {FeedItemFilter.DOWNLOADED, FeedItemFilter.UNPLAYED};
        assertEquals(Arrays.asList(R.string.not_played, R.string.hide_downloaded_episodes_label),
                FeedItemFilterGroup.displayNamesOf(values));
    }

    @Test
    public void unknownAndEmptyValuesYieldNoNames() {
        assertTrue(FeedItemFilterGroup.displayNamesOf(new String[]{"bogus", ""}).isEmpty());
        assertTrue(FeedItemFilterGroup.displayNamesOf(new String[0]).isEmpty());
    }
}
