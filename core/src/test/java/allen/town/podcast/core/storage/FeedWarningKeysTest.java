package allen.town.podcast.core.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class FeedWarningKeysTest {

    @Test
    public void duplicateKeyDoesNotDependOnOrder() {
        assertEquals(FeedWarningKeys.duplicateEpisodes("guid-a", "guid-b"),
                FeedWarningKeys.duplicateEpisodes("guid-b", "guid-a"));
        assertEquals("dup2:guid-a|guid-b", FeedWarningKeys.duplicateEpisodes("guid-b", "guid-a"));
    }

    @Test
    public void duplicateKeyDiffersForADifferentPair() {
        assertNotEquals(FeedWarningKeys.duplicateEpisodes("guid-a", "guid-b"),
                FeedWarningKeys.duplicateEpisodes("guid-a", "guid-c"));
    }

    @Test
    public void duplicateKeyToleratesMissingValues() {
        assertEquals("dup2:|guid-a", FeedWarningKeys.duplicateEpisodes(null, "guid-a"));
        assertEquals("dup2:|guid-a", FeedWarningKeys.duplicateEpisodes("guid-a", null));
    }

    /** The key is built from identifiers only, so a host editing an episode title keeps it. */
    @Test
    public void changedIdKeyOnlyDependsOnTheSavedEpisode() {
        assertEquals("idchg:42", FeedWarningKeys.changedEpisodeId(42));
        assertNotEquals(FeedWarningKeys.changedEpisodeId(42), FeedWarningKeys.changedEpisodeId(43));
    }
}
