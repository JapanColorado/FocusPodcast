package dev.russell.focuspodcast.searchpreference;

import androidx.annotation.NonNull;

public interface SearchPreferenceResultListener {
    void onSearchResultClicked(@NonNull SearchPreferenceResult result);
}
