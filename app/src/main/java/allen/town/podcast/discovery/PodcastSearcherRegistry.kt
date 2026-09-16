package allen.town.podcast.discovery

import allen.town.focus_common.util.Timber
import allen.town.podcast.core.pref.Prefs
import io.reactivex.Single

object PodcastSearcherRegistry {
    /**
     * The searchers the user has switched on, rebuilt on every read so that a change to the
     * search engine preference takes effect immediately.
     */
    @get:Synchronized
    val searchProviders: List<SearcherInfo>
        get() {
            val providers = ArrayList<SearcherInfo>()
            val searchList = Prefs.podcastSearchEngineList
            providers.add(SearcherInfo(CombinedSearcher(), 1.0f))
            for (searchEngine in searchList) {
                if (searchEngine.visible) {
                    when (searchEngine.tag) {
                        ItunesPodcastSearcher.SEARCH_ENGINE_TAG -> {
                            Timber.d("add itunes search")
                            providers.add(SearcherInfo(ItunesPodcastSearcher(), 1.0f))
                        }
                        FyydPodcastSearcher.SEARCH_ENGINE_TAG -> {
                            Timber.d("add fyyd search")
                            providers.add(SearcherInfo(FyydPodcastSearcher(), 1.0f))
                        }
                        PodcastIndexPodcastSearcher.SEARCH_ENGINE_TAG -> {
                            Timber.d("add podcastIndex search")
                            providers.add(SearcherInfo(PodcastIndexPodcastSearcher(), 1.0f))
                        }
                    }
                }
            }
            return providers
        }

    //search and url lookup are kept separate because iTunes always needs a url lookup while the others do not
    val lookUpUrlProviders: List<SearcherInfo> by lazy {
        listOf(
            SearcherInfo(CombinedSearcher(), 1.0f),
            SearcherInfo(FyydPodcastSearcher(), 1.0f),
            SearcherInfo(ItunesPodcastSearcher(), 1.0f),
            SearcherInfo(PodcastIndexPodcastSearcher(), 1.0f)
        )
    }

    /**
     * Resolves the real feed RSS url. Currently only iTunes needs this.
     * @param url
     * @return
     */
    @JvmStatic
    fun lookupUrl(url: String): Single<String> {
        for (searchProviderInfo in lookUpUrlProviders) {
            if (searchProviderInfo.searcher.javaClass != CombinedSearcher::class.java
                && searchProviderInfo.searcher.urlNeedsLookup(url)
            ) {
                return searchProviderInfo.searcher.lookupUrl(url)
            }
        }
        return Single.just(url)
    }

    fun urlNeedsLookup(url: String): Boolean {
        for (searchProviderInfo in lookUpUrlProviders) {
            if (searchProviderInfo.searcher.javaClass != CombinedSearcher::class.java
                && searchProviderInfo.searcher.urlNeedsLookup(url)
            ) {
                return true
            }
        }
        return false
    }

    class SearcherInfo(val searcher: PodcastSearcher, val weight: Float)
}
