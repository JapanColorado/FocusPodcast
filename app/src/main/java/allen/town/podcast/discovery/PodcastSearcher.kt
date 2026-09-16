package allen.town.podcast.discovery

import io.reactivex.Single

interface PodcastSearcher {
    fun search(query: String?): Single<List<PodcastSearchResult?>?>?

    /**
     * Look up the real feed url
     * @param resultUrl
     *
     * @return
     */
    fun lookupUrl(resultUrl: String): Single<String>
    fun urlNeedsLookup(resultUrl: String): Boolean
    val name: String
}