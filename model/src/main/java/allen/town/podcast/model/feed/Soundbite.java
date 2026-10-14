package allen.town.podcast.model.feed;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.Objects;

/**
 * A highlight of an episode that the feed points out with the Podcasting 2.0
 * {@code <podcast:soundbite>} tag: where it starts, how long it runs, and an optional title.
 */
public class Soundbite implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long startMs;
    private final long durationMs;
    @Nullable
    private final String title;

    public Soundbite(long startMs, long durationMs, @Nullable String title) {
        this.startMs = startMs;
        this.durationMs = durationMs;
        this.title = title == null || title.trim().isEmpty() ? null : title.trim();
    }

    public long getStartMs() {
        return startMs;
    }

    public long getDurationMs() {
        return durationMs;
    }

    /** The feed's title for the highlight, null when it gave none. */
    @Nullable
    public String getTitle() {
        return title;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Soundbite)) {
            return false;
        }
        Soundbite other = (Soundbite) o;
        return startMs == other.startMs && durationMs == other.durationMs
                && Objects.equals(title, other.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startMs, durationMs, title);
    }

    @NonNull
    @Override
    public String toString() {
        return "Soundbite{" + startMs + "+" + durationMs + ", " + title + "}";
    }
}
