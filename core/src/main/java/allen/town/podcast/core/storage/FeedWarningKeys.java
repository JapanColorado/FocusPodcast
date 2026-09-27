package allen.town.podcast.core.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Builds the keys that identify a non-fatal feed warning in the download log, so that a warning
 * re-reported on every refresh replaces its previous row and can be dismissed for good. A key must
 * stay the same across refreshes while the underlying problem is the same, and must not depend on
 * anything the host may edit in the meantime, such as an episode title.
 */
public final class FeedWarningKeys {

    private FeedWarningKeys() {
    }

    /**
     * "The podcast host added the same episode twice": the pair of identifying values, sorted so
     * that the key does not depend on which of the two the refresh happened to meet first.
     */
    @NonNull
    public static String duplicateEpisodes(@Nullable String identifyingValueA,
                                           @Nullable String identifyingValueB) {
        String a = identifyingValueA != null ? identifyingValueA : "";
        String b = identifyingValueB != null ? identifyingValueB : "";
        return a.compareTo(b) <= 0 ? "dup2:" + a + "|" + b : "dup2:" + b + "|" + a;
    }

    /**
     * "The podcast host changed the ID of an existing episode": the database id of the episode
     * that was repaired, which survives the repair.
     */
    @NonNull
    public static String changedEpisodeId(long savedItemId) {
        return "idchg:" + savedItemId;
    }
}
