package allen.town.podcast.discovery

import android.util.Log
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.SingleOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.*
import java.util.concurrent.CountDownLatch

class CombinedSearcher : PodcastSearcher {
    override fun search(query: String?): Single<List<PodcastSearchResult?>?>? {
        // Read the registry once: it rebuilds its list on every access and the result indices
        // have to line up with singleResults.
        val providers = PodcastSearcherRegistry.searchProviders
        val disposables = ArrayList<Disposable>()
        val singleResults: MutableList<List<PodcastSearchResult?>?> = ArrayList(
            Collections.nCopies<List<PodcastSearchResult?>?>(
                providers.size,
                null
            )
        )
        val latch = CountDownLatch(providers.size)
        for (i in providers.indices) {
            val searchProviderInfo = providers[i]
            val searcher = searchProviderInfo.searcher
            if (searchProviderInfo.weight <= 0.00001f || searcher.javaClass == CombinedSearcher::class.java) {
                latch.countDown()
                continue
            }
            val singleSearch = searcher.search(query)
            if (singleSearch == null) {
                // The searcher declined the query; it just contributes no results.
                latch.countDown()
                continue
            }
            disposables.add(
                singleSearch.subscribe({ e: List<PodcastSearchResult?>? ->
                    singleResults[i] = e
                    latch.countDown()
                }
                ) { throwable: Throwable? ->
                    Log.d(TAG, Log.getStackTraceString(throwable))
                    latch.countDown()
                })
        }
        return Single.create(
            SingleOnSubscribe { subscriber: SingleEmitter<List<PodcastSearchResult?>?> ->
                latch.await()
                val results = weightSearchResults(singleResults, providers)
                subscriber.onSuccess(results)
            })
            .doOnDispose {
                for (disposable in disposables) {
                    disposable.dispose()
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    private fun weightSearchResults(
        singleResults: List<List<PodcastSearchResult?>?>,
        providers: List<PodcastSearcherRegistry.SearcherInfo>
    ): List<PodcastSearchResult?> {
        val resultRanking = HashMap<String?, Float>()
        val urlToResult = HashMap<String?, PodcastSearchResult?>()
        for (i in singleResults.indices) {
            val providerPriority = providers[i].weight
            val providerResults = singleResults[i] ?: continue
            for (position in providerResults.indices) {
                val result = providerResults[position] ?: continue
                urlToResult[result.feedUrl] = result
                var ranking = resultRanking[result.feedUrl] ?: 0f
                ranking += 1f / (position + 1f)
                resultRanking[result.feedUrl] = ranking * providerPriority
            }
        }
        val sortedResults: List<Map.Entry<String?, Float>> =
            ArrayList<Map.Entry<String?, Float>>(resultRanking.entries)
        Collections.sort(sortedResults) { (_, value): Map.Entry<String?, Float>, (_, value1): Map.Entry<String?, Float> ->
            java.lang.Double.compare(
                value1.toDouble(), value.toDouble()
            )
        }
        val results: MutableList<PodcastSearchResult?> = ArrayList()
        for ((key) in sortedResults) {
            results.add(urlToResult[key])
        }
        return results
    }

    override fun lookupUrl(url: String): Single<String> {
        return PodcastSearcherRegistry.lookupUrl(url)
    }

    override fun urlNeedsLookup(url: String): Boolean {
        return PodcastSearcherRegistry.urlNeedsLookup(url)
    }

    override val name: String
        get() = ""

    companion object {
        private const val TAG = "CombinedSearcher"
    }
}
