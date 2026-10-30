package dev.russell.yagi.model.transcript;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A downloaded transcript as the database keeps it: the text as received, the address and MIME
 * type it came with, and when. It is parsed again whenever it is shown.
 */
public final class CachedTranscript {

    @Nullable
    private final String url;
    @Nullable
    private final String type;
    @NonNull
    private final String raw;
    private final long fetchedAt;

    public CachedTranscript(@Nullable String url, @Nullable String type, @NonNull String raw, long fetchedAt) {
        this.url = url;
        this.type = type;
        this.raw = raw;
        this.fetchedAt = fetchedAt;
    }

    @Nullable
    public String getUrl() {
        return url;
    }

    @Nullable
    public String getType() {
        return type;
    }

    @NonNull
    public String getRaw() {
        return raw;
    }

    public long getFetchedAt() {
        return fetchedAt;
    }

    /** Whether this copy was downloaded from {@code currentUrl}, i.e. the feed still points at it. */
    public boolean isFrom(@Nullable String currentUrl) {
        return url != null && url.equals(currentUrl);
    }
}
