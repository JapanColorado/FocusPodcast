package allen.town.podcast.discovery

import allen.town.podcast.core.feed.FeedUrlNotFoundException
import android.util.Log

object RetrieveFeedUtil {
    const val TAG = "RetrieveFeedUtil"

    /**
     * The feed url could not be resolved, so fall back to looking it up by track name.
     * HTTP returns 200 with no feed items rather than a 404.
     *
     * @param error
     */
    @JvmStatic
    fun tryToRetrieveFeedUrlBySearch(error: FeedUrlNotFoundException): String? {
        Log.d(TAG, "try to retrieve feed url from search")
        val url = searchFeedUrlByTrackName(error.trackName, error.artistName)
        if (url != null) {
            Log.d(TAG, "get retrieve feed url")
        } else {
            Log.d(TAG, "failed to retrieve feed url")
        }
        return url
    }

    fun searchFeedUrlByTrackName(trackName: String, artistName: String): String? {
        val searcher = CombinedSearcher()
        val query = "$trackName $artistName"
        val results = searcher.search(query)?.blockingGet() ?: return null
        for (result in results) {
            if (result == null) {
                continue
            }
            if (result.feedUrl != null && result.author != null && result.author.equals(
                    artistName,
                    ignoreCase = true
                ) && result.title.equals(trackName, ignoreCase = true)
            ) {
                return result.feedUrl
            }
        }
        return null
    }
}